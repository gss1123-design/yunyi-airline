package com.hakimi.aviation.service.flight.impl;

import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.hakimi.aviation.common.SeatProbeFactory;
import com.hakimi.aviation.config.RedisKey;
import com.hakimi.aviation.entity.Flight;
import com.hakimi.aviation.entity.TicketOrder;
import com.hakimi.aviation.enums.BizCodeEnum;
import com.hakimi.aviation.es.FlightIndexDoc;
import com.hakimi.aviation.exception.BizException;
import com.hakimi.aviation.mapper.FlightMapper;
import com.hakimi.aviation.mapper.OrderMapper;
import com.hakimi.aviation.message.order.CancelOrderMessage;
import com.hakimi.aviation.model.request.flight.BookingRequest;
import com.hakimi.aviation.model.request.flight.FlightSearchRequest;
import com.hakimi.aviation.model.vo.FlightSearchVO;
import com.hakimi.aviation.model.vo.TicketOrderVO;
import com.hakimi.aviation.service.admin.async.BookingAsyncService;
import com.hakimi.aviation.service.flight.FlightService;
import com.hakimi.aviation.util.SeatUtil;
import com.hakimi.aviation.util.ValidateRequest;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class FlightServiceImpl implements FlightService {

    @Resource
    private FlightMapper flightMapper;

    @Resource
    private OrderMapper orderMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RabbitTemplate rabbitTemplate;

    @Resource
    private BookingAsyncService bookingAsyncService;

    @Resource
    private ElasticsearchOperations elasticsearchOperations;

    // 预加载 Lua 脚本对象，不要每次请求都去读文件，提升并发性能
    private DefaultRedisScript<Long> bookingAllInOneScript;

    // 全局限流：最多允许 30 个线程同时去 MySQL 查列表兜底
    private final Semaphore esFallbackSemaphore = new Semaphore(30);


    @PostConstruct
    public void init() {
        bookingAllInOneScript = new DefaultRedisScript<>();
        bookingAllInOneScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/booking_all_in_one.lua")));
        bookingAllInOneScript.setResultType(Long.class);
    }

    @Override
    public List<Flight> searchFlight(FlightSearchRequest request) {

        //做请求合法性校验 DTO属性不能出现空值或者非法的值 否则抛出异常 后续业务不进行
        ValidateRequest.ValidateFlightSearchReq(request);

        //业务降级 到数据库查找
        List<Flight> flights = flightMapper.searchFlight(request);

        return flights;
    }

    @Override
    public List<FlightSearchVO> searchFlightWithCache(FlightSearchRequest request) {

        //前置合法性校验
        ValidateRequest.ValidateFlightSearchReq(request);

        List<FlightIndexDoc> flightIndexDocs = new ArrayList<>();

        try{
            flightIndexDocs = searchFlightFromES(request);
        }catch (Exception e){
            log.error("中间件 ES 异常 降级到数据库",e);
            //NOTE 降级策略
            flightIndexDocs = searchFlightFromDBFallback(request);
        }

        if(flightIndexDocs.isEmpty()){
            //NOTE 直接返回空数据
            return Collections.emptyList();
        }

        List<FlightSearchVO> resultVOs = new ArrayList<>();

        try{
            resultVOs =  assembleInventoryFromRedis(flightIndexDocs);
        }catch (Exception e){
            log.error("中间件 Redis 异常 返回静态数据 航班已不可购买",e);
            //TODO 服务降级 未查询到数据的航班 不可预订 需要通知B端检修
            for (FlightIndexDoc doc : flightIndexDocs) {
                FlightSearchVO vo = new FlightSearchVO();
                BeanUtils.copyProperties(doc, vo);
                vo.setAvailableSeats(-1); // -1 代表“系统维护中/余票待查”
                resultVOs.add(vo);
            }
        }
        //返回
        return resultVOs;
    }

    private List<FlightIndexDoc> searchFlightFromES(FlightSearchRequest request){

        // ==========================================
        // 配置查询条件 (Bool Query -> Must -> Term)
        // ==========================================
        Query boolQuery = Query.of(q -> q
                .bool(b -> b
                        .must(m -> m.term(t -> t.field("deptCity").value(request.getDeptCity())))
                        .must(m -> m.term(t -> t.field("arrCity").value(request.getArrCity())))
                        // LocalDate 转 String 精确匹配 ES 的 date 类型 (需确保 format 匹配)
                        .must(m -> m.term(t -> t.field("flightDate").value(request.getFlightDate().toString())))
                )
        );

        // ==========================================
        // 动态排序装配
        // ==========================================
        SortOptions sortOptions;
        if (Integer.valueOf(2).equals(request.getSortType())) {
            // 类型 2: 按价格从低到高排序
            sortOptions = SortOptions.of(s -> s
                    .field(f -> f.field("totalPrice").order(SortOrder.Asc))
            );
        } else {
            // 默认类型 1: 按起飞时间最早排序
            sortOptions = SortOptions.of(s -> s
                    .field(f -> f.field("firstDeptTime").order(SortOrder.Asc))
            );
        }

        // 包装成 NativeQuery
        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(boolQuery)
                .withSort(sortOptions)
                .withMaxResults(20) // 限制只取前 20 条防 OOM
                .build();

        // ==========================================
        // 发送请求 & 装箱返回
        // ==========================================
        // 执行查询，拿到一个包装了元数据（命中数、耗时、评分等）的 SearchHits 对象
        SearchHits<FlightIndexDoc> searchHits = elasticsearchOperations.search(nativeQuery, FlightIndexDoc.class);

        // 把里面真实的文档内容 (Content) 提纯出来，变成 List 返回给下一步的 Redis 去缝合
        return searchHits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .collect(Collectors.toList());

    }

    /**
     * 此方法用来接收 ES 传来的 List 并从 Redis 中获取到 各航段的库存 并组装成VO
     */
    private List<FlightSearchVO> assembleInventoryFromRedis(List<FlightIndexDoc> resultFromES){

        // 获取到所有的 flightId
        List<Long> flightIds = resultFromES.stream()
                .map(doc -> doc.getId())
                .collect(Collectors.toList());

        // Pipeline 批量获取航班对应的航段路由 返回的是 List<List<String>>
        List<Object> routeResults = stringRedisTemplate.executePipelined(
                (RedisCallback<Object>) connection -> {
                    for (Long fid : flightIds) {
                        // 转成 byte[] 才能在底层 connection 中使用
                        byte[] key = ("route:flight:" + fid).getBytes();
                        // 把这个 key 对应的 List 里的所有元素 (0 到 -1) 全拿出来
                        connection.listCommands().lRange(key, 0, -1);
                    }
                    //NOTE 使用 Pipeline 时，Callback 内部必须 return null
                    return null;
                }
        );

        // 用一个 Set 来收集所有不重复的航段 ID
        Set<String> allSegmentIds = new HashSet<>();
        for (Object result : routeResults) {
            if (result != null) {
                // 因为咱们用的 StringRedisTemplate，拿回来的直接是 List<String>
                List<String> segIds = (List<String>) result;
                allSegmentIds.addAll(segIds);
            }
        }

        // 拼装库存的 Keys: [ "stock:seg:101", "stock:seg:102", ... ]
        List<String> stockKeys = allSegmentIds.stream()
                .map(segId -> "stock:seg:" + segId)
                .collect(Collectors.toList());

        // 一次性查出所有库存
        List<String> stockValues = stringRedisTemplate.opsForValue().multiGet(stockKeys);

        Map<String, Integer> stockMap = new HashMap<>();
        for (int i = 0; i < stockKeys.size(); i++) {
            String val = stockValues.get(i);
            // 如果 Redis 查不到，直接赋 -1
            stockMap.put(stockKeys.get(i), val == null ? -1 : Integer.parseInt(val));
        }

        List<FlightSearchVO> resultVOs = new ArrayList<>();

        // resultFromES 和 routeResults 在索引位置上一一对应
        for (int i = 0; i < resultFromES.size(); i++) {
            FlightIndexDoc doc = resultFromES.get(i);
            List<String> currentSegIds = (List<String>) routeResults.get(i);

            int finalStock = -1; // 默认待查

            if (!CollectionUtils.isEmpty(currentSegIds)) {
                boolean hasMissingStock = false;
                int minStock = Integer.MAX_VALUE;

                // 遍历该航班的所有航段，寻找最小库存
                for (String segId : currentSegIds) {
                    int stock = stockMap.getOrDefault("stock:seg:" + segId, -1);
                    if (stock == -1) {
                        hasMissingStock = true; // 发现丢数据了！
                        break; // 直接阻断，不用继续查了
                    }
                    minStock = Math.min(minStock, stock);
                }

                // 如果有任何一段缺失，整体标为 -1；否则就是木桶的最短板
                finalStock = hasMissingStock ? -1 : minStock;
            }

            // 4.3 属性拷贝与装箱
            FlightSearchVO vo = new FlightSearchVO();
            // Spring 自带神器：自动把同名同类型的属性拷贝过去，省得写十几个 set
            BeanUtils.copyProperties(doc, vo);
            // 补上刚才算出来的核心动态库存
            vo.setAvailableSeats(finalStock);

            resultVOs.add(vo);
        }

        return resultVOs;
    }

    /**
     * ES 不可用后 在这个方法里启动降级策略 开始从数据库拉取航班静态信息
     * @param request DTO
     * @return ES 信息实体类 航班静态信息 不包含实时库存
     */
    private List<FlightIndexDoc> searchFlightFromDBFallback(FlightSearchRequest request){
        //先尝试抢占一个信号量
        if(!esFallbackSemaphore.tryAcquire()){
            //NOTE 在这里快速失败 返回空列表  避免数据库被打爆
            log.error("[ES降级保护] 并发查库人数过多，已触发限流拒绝服务");
            return Collections.emptyList();
        }

        try{
            // NOTE 在这里通过联合索引快速查找到航班信息 返回 无需联表查询
            LambdaQueryWrapper<Flight> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper
                    .eq(Flight::getDeptCity,request.getDeptCity())
                    .eq(Flight::getArrCity,request.getArrCity())
                    .eq(Flight::getFlightDate,request.getFlightDate())
                    .last("LIMIT 20");

            List<Flight> flightList = flightMapper.selectList(queryWrapper);

            //按照 DTO 传递的排序规则进行排序
            Comparator<Flight> comparator;

            if (Integer.valueOf(2).equals(request.getSortType())) {
                // 按价格 (BigDecimal) 排序。如果遇到 null，自动垫底
                comparator = Comparator.comparing(
                        Flight::getTotalPrice,
                        Comparator.nullsLast(BigDecimal::compareTo)
                );
            } else {
                // 按起飞时间 (LocalDateTime) 排序。如果遇到 null，自动垫底！
                comparator = Comparator.comparing(
                        Flight::getDeptTime,
                        Comparator.nullsLast(LocalDateTime::compareTo)
                );
            }

            // 将装配好的比较器塞进 Stream 流
            List<Flight> sortedFlightList = flightList.stream()
                    .sorted(comparator)
                    .limit(20)
                    .collect(Collectors.toList());

            List<FlightIndexDoc> flightIndexDocs = sortedFlightList.stream().map(flight -> {
                FlightIndexDoc doc = new FlightIndexDoc();
                BeanUtils.copyProperties(flight, doc);
                return doc;
            }).collect(Collectors.toList());

            return flightIndexDocs;

        }finally {
            //保证能够释放信号量
            esFallbackSemaphore.release();
        }

    }

    /**
     * 预订机票的方法 controller层直接调用 在这里进行扣减Redis库存、分配座位，异步落库订单、生成快照等操作
     * @param request 请求的 DTO
     * @return  VO 类
     */
    @Override
    public TicketOrderVO bookingFlight(BookingRequest request, Long userId, String userName) {

        //请求需要先经过前置合法性校验
        ValidateRequest.ValidateBookingReq(request);

        Long flightId = request.getFlightId();


        //首先检查 DTO 内用户Id是否和现在登录的用户Id相同
        if(!userId.equals(request.getUserId())){
            log.error("请求的用户 ID 与登录用户不一致");
            throw new BizException(BizCodeEnum.ILLEGAL_REQUEST);
        }
        //NOTE Lua 的参数 包含 两个检查项的 RedisKey
        List<String> keys = Arrays.asList(
                RedisKey.ORDER_NOT_FINISH_KEY + userId,
                RedisKey.ROUTE_FLIGHT + flightId
        );
        //NOTE 可接受的座位偏移量 并且已将偏好座位置前
        List<Integer> probeSequence = SeatProbeFactory.getProbeSequence(request.getSeatPrefer());

        //NOTE Lua的参数，第一个元素是 flightId 后面的是座位偏移量
        List<String> argsList = new ArrayList<>();
        argsList.add(String.valueOf(flightId));
        for(Integer offset:probeSequence){
            argsList.add(String.valueOf(offset));
        }

        //-------- 在此阶段开始准备 进行重复购票校验 并对库存进行检查和扣减 扣减成功后需要在Redis中回填一条行程记录 ---------//

        /**
         * 启动 Lua 脚本 有五种返回值：
         * >= 0：座位号的偏移量，从零开始；代表检验及库存扣减、占座全部成功 可以生成订单；
         * -1：库存不足 购票失败
         * -2：航班数据异常 可能是航班信息丢失 需要人工介入 发生此类情况后续可以向B端报告 暂时限制购买此航班
         * -3：检验到用户重复购票
         * -4：用户行程记录缓存 miss 启动降级策略从数据库拉取
         * -5：航班已经被故障屏障暂停销售
         */
        Long result = stringRedisTemplate.execute(
                bookingAllInOneScript,
                keys,
                argsList.toArray()
        );
        //noinspection ConstantConditions
        if(result == null){
            throw new BizException(BizCodeEnum.SERVICE_BUSY);
        }

        if(result == -3){
            throw new BizException(BizCodeEnum.REPEAT_PURCHASE);
        }
        else if(result == -2){
            String pauseReason = stringRedisTemplate.opsForValue().get(
                    RedisKey.FLIGHT_SALE_PAUSE_KEY + flightId
            );
            log.error("FLIGHT_SALE_PAUSED flightId={} reason={} action=repair-redis-and-resume",
                    flightId, pauseReason);

            throw new BizException(BizCodeEnum.FLIGHT_ERROR);
        }
        else if(result == -5){
            String pauseReason = stringRedisTemplate.opsForValue().get(
                    RedisKey.FLIGHT_SALE_PAUSE_KEY + flightId
            );
            log.warn("航班当前处于故障停售状态 flightId={} reason={}", flightId, pauseReason);
            throw new BizException(BizCodeEnum.FLIGHT_ERROR);
        }
        else if(result == -1){
            throw new BizException(BizCodeEnum.TICKET_SOLD_OUT);
        }
        //NOTE 压测时暂时消掉
        else if(result == -4){
            log.info(">>> 用户{}行程缓存数据获取失败 将降级到数据库获取", userName);
            //降级策略 从数据库中获取数据
            List<Long> orderHistory = orderMapper.getOrderHistory(userId);

            //准备回填（哨兵 -1 必须带上）
            List<String> backfillElements = new ArrayList<>();
            backfillElements.add("-1");
            if (!orderHistory.isEmpty()) {
                orderHistory.forEach(id -> backfillElements.add(String.valueOf(id)));
            }

            //回填缓存
            stringRedisTemplate.opsForSet().add(
                    RedisKey.ORDER_NOT_FINISH_KEY + userId,
                    backfillElements.toArray(new String[0])
            );
            //设置 TTL 与 JWT 过期时间一致 其一定会晚于 JWT 过期
            stringRedisTemplate.expire(
                    RedisKey.ORDER_NOT_FINISH_KEY + userId,
                    7, TimeUnit.DAYS
            );

            //判断是否重复购买
            if (orderHistory.contains(flightId)) {
                //抛出异常
                throw new BizException(BizCodeEnum.REPEAT_PURCHASE);
            }

            // 这里的处理要细腻：
            // 既然缓存已经回填好了，可以选择抛出“繁忙”让用户重试，
            // 或者因为本线程已经拿到了 DB 权限，直接继续往下走 Lua 扣减。
            throw new BizException(BizCodeEnum.SERVICE_BUSY);
        }

        //-------- 到此已完成校验和库存扣减 可以准备生成订单 -----------//

        /**
        从Redis中获取航班信息，不访问数据库
        只需要从Hash中获取到总价即可 不需要经过序列化
        */
        Object priceObj = stringRedisTemplate
                .opsForHash()
                .get(RedisKey.INFO_FLIGHT + flightId, "total_price");

        BigDecimal totalPrice;
        if(priceObj == null){
            //降级策略：从数据库中获取到航班对象 再取出价格
            //NOTE 这实际上不是一个正常的情况 数据层一定发生了异常 应该做好限流 并且记录日志 进行检查 考虑暂时限制购买此航班
            // 按理这里一般不会被触发
            log.warn("航班 (ID:{})缓存数据丢失，正在降级查询数据库，需要检查",flightId);
            Flight flight = flightMapper.selectById(flightId);
            totalPrice = flight.getTotalPrice();
        }
        else{
            totalPrice = new BigDecimal(String.valueOf(priceObj));
        }

        //订单对象
        TicketOrder ticketOrder = parseToTicketOrder(userId,userName,flightId,totalPrice,result);
        //组件死信消息 用来超时取消订单
        CancelOrderMessage cancelOrderMessage = new CancelOrderMessage(
                ticketOrder.getId(),
                flightId,
                userId,
                Math.toIntExact(result)
        );

        //使用专门的异步服务类来异步执行网络 IO 任务
        bookingAsyncService.postBookingTasks(ticketOrder,flightId,userId,cancelOrderMessage);

        //NOTE 前端需要返回确切的座位号，故需要准备一个VO类
        return parseToTicketOrderVO(ticketOrder);
    }

    private TicketOrder parseToTicketOrder(Long userId, String userName, Long flightId, BigDecimal totalPrice, Long seatOffset){

        TicketOrder ticketOrder = new TicketOrder();
        //用 MP 自带的工具 使用雪花算法提前生成主键Id
        long id = IdWorker.getId();
        ticketOrder.setId(id);
        //使用前缀加主键作为订单号
        ticketOrder.setOrderNo("Hakimi-"+id);
        ticketOrder.setUserId(Long.valueOf(userId));
        ticketOrder.setFlightId(flightId);
        //NOTE 插入座位偏移量
        ticketOrder.setSeatOffset(Math.toIntExact(seatOffset));
        ticketOrder.setPassengerName(userName);
        ticketOrder.setTotalPrice(totalPrice);
        ticketOrder.setStatus("UNPAID");
        //下单时默认行程未结束
        ticketOrder.setIsFinished(0);
        ticketOrder.setCreatedAt(LocalDateTime.now());

        return ticketOrder;
    }

    private TicketOrderVO parseToTicketOrderVO(TicketOrder ticketOrder){

        TicketOrderVO ticketOrderVO = new TicketOrderVO();

        ticketOrderVO.setOrderId(ticketOrder.getId());
        ticketOrderVO.setOrderNo(ticketOrder.getOrderNo());
        ticketOrderVO.setFlightId(ticketOrder.getFlightId());
        ticketOrderVO.setPassengerName(ticketOrder.getPassengerName());
        ticketOrderVO.setTotalPrice(ticketOrder.getTotalPrice());
        ticketOrderVO.setStatus(ticketOrder.getStatus());
        ticketOrderVO.setIsFinished(ticketOrder.getIsFinished());
        ticketOrderVO.setCreatedAt(ticketOrder.getCreatedAt());

        ticketOrderVO.setExactSeat(SeatUtil.toSeatNo(ticketOrder.getSeatOffset()));

        return ticketOrderVO;
    }

}
