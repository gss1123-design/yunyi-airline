package com.hakimi.aviation.service.order.impl;

import com.alibaba.fastjson.JSONObject;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.response.AlipayTradePagePayResponse;
import com.hakimi.aviation.alipay.AlipayConfigProperties;
import com.hakimi.aviation.alipay.AlipayProcess;
import com.hakimi.aviation.config.RedisKey;
import com.hakimi.aviation.entity.TicketOrder;
import com.hakimi.aviation.enums.BizCodeEnum;
import com.hakimi.aviation.exception.BizException;
import com.hakimi.aviation.mapper.FlightMapper;
import com.hakimi.aviation.mapper.OrderMapper;
import com.hakimi.aviation.mapper.SegmentInstanceMapper;
import com.hakimi.aviation.service.order.PayService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Slf4j
@Service
public class PayServiceImpl implements PayService {

    @Resource
    private AlipayClient alipayClient;

    @Resource
    private AlipayProcess alipayProcess;

    @Resource
    private AlipayConfigProperties properties;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private FlightMapper flightMapper;

    @Resource
    private OrderMapper orderMapper;

    @Resource
    private SegmentInstanceMapper segmentInstanceMapper;

    @Override
    public String payOrder(Long orderId) {

        AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();

        // 设置同步跳转地址和异步通知地址
        request.setReturnUrl(properties.getReturnUrl());
        request.setNotifyUrl(properties.getNotifyUrl());

        String snapshotKey = RedisKey.ORDER_SNAPSHOT_KEY + orderId;
        Map<Object, Object> orderData = stringRedisTemplate.opsForHash().entries(snapshotKey);
        if(orderData == null || orderData.isEmpty()){
            log.warn("支付拦截：订单不存在或已超时释放，orderId: {}", orderId);
            throw new BizException(BizCodeEnum.ORDER_MISS_OR_EXPIRED);
        }

        // 组装业务参数 JSON 格式
        JSONObject bizContent = new JSONObject();
        // 订单号
        bizContent.put("out_trade_no", "Hakimi-"+orderId);
        // 订单总金额，支付宝要求是字符串格式
        bizContent.put("total_amount", orderData.get("total_amount").toString());
        // 订单标题
        bizContent.put("subject", orderData.get("subject").toString());

        String createTimeStr = orderData.get("create_time").toString();

        // 快照创建时间解析为 LocalDateTime
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LocalDateTime createTime = LocalDateTime.parse(createTimeStr, formatter);
        // 加上 15 分钟，得到绝对过期时间
        LocalDateTime expireTime = createTime.plusMinutes(15);
        // 放支付宝的参数里
        bizContent.put("time_expire", expireTime.format(formatter));


        // 销售产品码，电脑网站支付固定为 FAST_INSTANT_TRADE_PAY
        bizContent.put("product_code", "FAST_INSTANT_TRADE_PAY");

        request.setBizContent(bizContent.toString());

        try {
            // 调用 SDK 生成一段包含自动提交表单的 HTML 代码
            AlipayTradePagePayResponse response = alipayClient.pageExecute(request);
            if (response.isSuccess()) {
                return response.getBody(); // 这就是那一长串 <form> 网页代码
            } else {
                throw new RuntimeException("支付宝下单失败：" + response.getMsg());
            }
        } catch (AlipayApiException e) {
            e.printStackTrace();
            throw new RuntimeException("调用支付宝接口发生异常");
        }

    }

    /**
     * 支付宝回调使用的方法
     * 经过预订时的拦截 与支付宝网关的稀释 加上业务本身的并发程度情况 此方法的并发量通常不会超过180
     * 结合接口并发量 以及业务的强一致性与实时性要求 本方法直接连接操作数据库 并使用数据库作为兜底防止极端情况下的超卖
     * @param orderId,tradeNo,totalAmount 支付宝回调携带的 HTTP 请求 中的参数
     * @return 只能返回 "success" 或 "failure" 给支付宝 以确定支付是否成功、还是否需要重试 callback
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean confirmOrder(Long orderId,String tradeNo,String totalAmount) {

        //TODO 待办事宜：
        // 1.根据支付宝HTTP请求判断支付情况
        // 2.直接删除Redis中的未支付状态 查看结果 如果是0（即不存在）则不进行后续业务 直接返回"success" ; 否则继续业务
        // 待定操作： (在确认删除未支付状态成功后 向Redis中 SETNX 插入一个占位符 表示此订单支付正在进行  插入失败的线程不进行后续业务 直接返回 "success")
        // 3.数据库乐观更新订单状态 只修改 status 为 "UNPAID" 的 订单为 "PAID" 判断updateRows来决定是否扣减库存
        // 4.如果updateRows为0 则可判断已支付完成 不扣减库存 直接返回 "success" 否则继续扣减库存
        // 5.扣减库存时 指定 available_seats 必须大于等于 所要扣减的数量 SQL执行完后 检查updateRows
        // 6.扣减库存的SQL执行完后 如果updateRows等于0 说明发生了极端竞争 可能是在预订阶段发生了超卖 直接抛出异常回滚事务 考虑介入人工
        // 7.如果一切正常 则整个支付阶段全部完成 需要立马删除Redis的订单快照

        log.info("confirmOrder 方法已被调用");


        TicketOrder ticketOrder = orderMapper.selectById(orderId);
        if(ticketOrder == null){
            log.error("回调异常：查无此订单 {}", orderId);
            return false;
        }

        if("PAID".equals(ticketOrder.getStatus())) {
            System.out.println("已支付过了");
            return true;
        }


        BigDecimal callbackAmount = new BigDecimal(totalAmount);
        if(ticketOrder.getTotalPrice().compareTo(callbackAmount) != 0) {
            log.error("检验到订单钱款实际不符，疑似伪造或篡改");
            return false;
        }

        log.info("通过检验 准备开始执行订单确认业务");


        Long userId = ticketOrder.getUserId();
        //直接删除 Redis中的 未支付记录
        Long removeSuccess = stringRedisTemplate.opsForSet().remove(
                RedisKey.ORDER_UNPAID_KEY + userId,
                orderId.toString());

        if(removeSuccess == null || removeSuccess == 0){
            //NOTE 如果删除失败（已被删除）或者为空（异常） 则此次回调已经失败（可能订单被取消或者是重复回调） 需要判断是否需要退款
            // 必须降级检查数据库订单状态是否为 "CANCELLED" 如果付款后因为订单过期而造成库存回滚 则必须退款
            // 这里还是要经过数据库乐观更新 确定是否应该退款


            //NOTE 这里不使用UPDATE，因为会改变数据库里原有的订单状态，而且没办法复原
            //乐观更新订单状态 通过updateRows决定是否要退款
            //int updateRows = orderMapper.updateStatusToPaid(orderId,tradeNo);

            String orderStatus = orderMapper.selectStatusByOrderId(orderId);

            if("CANCELLED".equals(orderStatus)){
                //NOTE 只有 “CANCELLED” 这种情况才能退款，未付款或已支付都不应该退款
                log.info("订单已被取消，此次支付回调失败 准备执行退款服务。orderId:{}",orderId);
                //异步退款
                alipayProcess.triggerRefundProcess(ticketOrder);

            }
            //NOTE 订单状态没有改变，说明这是重复的回调请求 数据库订单已经为支付成功 不予理会
            else{
                log.info("重复的支付回调，直接忽略。orderId: {}", orderId);
            }
            //最后都要返回成功 防止支付宝一致重复回调
            return true;
        }

        //乐观更新订单状态 通过updateRows决定是否要继续扣减库存
        int updateRows = orderMapper.updateStatusToPaid(orderId,tradeNo);
        //修改后无影响 说明已经支付过 直接返回成功
        //NOTE 按理说这里不会被触发 但不影响
        if(updateRows == 0){
            return true;
        }

        Long flightId = ticketOrder.getFlightId();

        updateRows = segmentInstanceMapper.deductStockByFlightId(flightId,1);
        //说明出现了极端竞争 必须抛出异常让事务回滚
        //NOTE 按理说这里不会被触发 但不影响
        if(updateRows == 0){
            throw new BizException(BizCodeEnum.TICKET_SOLD_OUT);
        }

        //到此整个流程已经正常结束 再此删除订单快照
        stringRedisTemplate.delete(RedisKey.ORDER_SNAPSHOT_KEY + orderId);

        return true;
    }


}
