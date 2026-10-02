package com.hakimi.aviation.service.admin.impl;

import com.hakimi.aviation.config.RedisKey;
import com.hakimi.aviation.entity.Flight;
import com.hakimi.aviation.entity.FlightSegment;
import com.hakimi.aviation.entity.SegmentInstance;
import com.hakimi.aviation.enums.BizCodeEnum;
import com.hakimi.aviation.es.FlightIndexDoc;
import com.hakimi.aviation.exception.BizException;
import com.hakimi.aviation.mapper.FlightMapper;
import com.hakimi.aviation.mapper.FlightSegmentMapper;
import com.hakimi.aviation.mapper.SegmentInstanceMapper;
import com.hakimi.aviation.model.request.flight.CreateFlightRequest;
import com.hakimi.aviation.repository.FlightIndexRepository;
import com.hakimi.aviation.service.admin.HandleFlightService;
import com.hakimi.aviation.util.AirportCityUtil;
import com.hakimi.aviation.util.ValidateRequest;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Service
public class HandleFlightServiceImpl implements HandleFlightService {

    @Resource
    private FlightMapper flightMapper;

    @Resource
    private FlightSegmentMapper flightSegmentMapper;

    @Resource
    private SegmentInstanceMapper segmentInstanceMapper;

    @Resource
    private FlightIndexRepository flightIndexRepository;
    
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 创建新的航班的方法 还需要同时创建新的 flight_segment 实例将新航班与现有的航段实例关联起来 新航班必须基于已有的航段实例才能创建
     * @param request DTO 只含有 航班号 flightNO 与 List<SegmentInstance> 其余所有所需字段需要查询数据库填充
     * @return 新创建的 Flight 实例
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Flight createNewFlight(CreateFlightRequest request){
        //前置的合法性校验
        ValidateRequest.ValidateFlightCreateReq(request);

        //插入新航班
        Flight flight = parseToFlight(request);
        flightMapper.insert(flight);
        //插入新的 航班航段关联记录
        List<FlightSegment> segmentList = parseToSegment(flight.getId(), request);
        flightSegmentMapper.insertBatch(segmentList);

        // B端并发不高，不引入MQ以降低复杂度，但仍然要保证DB与Redis/ES的一致性
        // 通过注册事务同步器，在数据库事务成功 Commit 之后再执行网络IO操作
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    // 同步存入 ES
                    FlightIndexDoc flightIndexDoc = new FlightIndexDoc();
                    BeanUtils.copyProperties(flight, flightIndexDoc);
                    flightIndexRepository.save(flightIndexDoc);

                    // Redis Hash 插入航班信息
                    Map<String, String> dataToRedis = parseToMap(flight);
                    stringRedisTemplate.opsForHash().putAll(
                            RedisKey.INFO_FLIGHT + flight.getId(),
                            dataToRedis);

                } catch (Exception e) {
                    // B端可以在此记录日志，如果失败后续可以人工/定时任务补偿
                    log.error("航班 [{}] 异步同步 ES/Redis 失败", flight.getId(), e);
                }
            }
        });
        
        return flight;
    }

    /**
     * 拼接实体类
     * @param request 请求经过前置校验 只可能包含一个或两个id
     * @return Flight 实体类
     */
    private Flight parseToFlight(CreateFlightRequest request){

        Flight flight = new Flight();
        String flightNo = request.getFlightNo();
        List<Long> segmentInstanceIds = request.getSegmentInstanceIds();

        List<SegmentInstance> unOrderedSegmentInstances = segmentInstanceMapper.selectBatchIds(segmentInstanceIds);
        
        // 校验航段实例是否存在以及数量是否匹配
        if(unOrderedSegmentInstances == null || unOrderedSegmentInstances.size() != segmentInstanceIds.size()){
            log.error("航段实例不存在或数量不匹配，请求的航段数：{}，数据库实际查询到的航段数：{}", 
                    segmentInstanceIds.size(), 
                    unOrderedSegmentInstances == null ? 0 : unOrderedSegmentInstances.size());
            throw new BizException(BizCodeEnum.FLIGHT_ERROR);
        }

        // 保证航段实例的顺序与前端传入的ID顺序一致，因为 selectBatchIds 出来的结果可能不保证顺序
        Map<Long, SegmentInstance> instanceMap = unOrderedSegmentInstances.stream()
                .collect(Collectors.toMap(SegmentInstance::getId, s -> s));
        
        List<SegmentInstance> segmentInstances = segmentInstanceIds.stream()
                .map(instanceMap::get)
                .toList();
                
        // 增加 NPE 检查：防止某个航段 ID 在数据库中对应的记录为 null
        for (SegmentInstance instance : segmentInstances) {
            if (instance == null) {
                log.error("航段实例映射失败，存在空记录");
                throw new BizException(BizCodeEnum.FLIGHT_ERROR);
            }
        }

        List<Long> templateIds = segmentInstances.stream()
                .map(SegmentInstance::getTemplateId)
                .toList();

        flight.setFlightNo(flightNo);

        // 防御：防止模板ID为空导致后续查询抛出异常
        if (templateIds.get(0) == null || templateIds.get(templateIds.size() - 1) == null) {
            log.error("航段实例关联的模板ID为空");
            throw new BizException(BizCodeEnum.FLIGHT_ERROR);
        }

        flight.setDeptCity(
                AirportCityUtil.findDeptCity(templateIds.get(0))
        );
        
        // 防御：防止航段实例的时间字段为空
        if (segmentInstances.get(0).getDeptTime() == null) {
             log.error("首个航段起飞时间为空");
             throw new BizException(BizCodeEnum.FLIGHT_ERROR);
        }
        
        flight.setFlightDate(
                segmentInstances.get(0).getDeptTime().toLocalDate()
        );

        flight.setDeptTime(
                segmentInstances.get(0).getDeptTime()
        );

        flight.setArrCity(
                AirportCityUtil.findArrCity(templateIds.get(templateIds.size() - 1))
        );
        
        if (segmentInstances.get(segmentInstances.size() - 1).getArrTime() == null) {
             log.error("末尾航段到达时间为空");
             throw new BizException(BizCodeEnum.FLIGHT_ERROR);
        }

        flight.setArrTime(
                segmentInstances.get(segmentInstances.size() - 1).getArrTime()
        );

        // 防御性编程：如果 getPrice 为空，赋为0，防止 reduce 抛出 NullPointerException
        BigDecimal totalPrice = segmentInstances.stream()
                .map(s -> s.getPrice() != null ? s.getPrice() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        flight.setTotalPrice(totalPrice);

        if(flight.getDeptTime().isAfter(flight.getArrTime())){
            log.error("起飞时间晚于到达时间 航段配置非法");
            throw new BizException(BizCodeEnum.ILLEGAL_SEGMENT_SET);
        }

        return flight;
    }

    private List<FlightSegment> parseToSegment(Long flightId,CreateFlightRequest request){

        List<Long> segmentInstanceIds = request.getSegmentInstanceIds();

        return IntStream.range(0, segmentInstanceIds.size())
                .mapToObj(i -> {
                    FlightSegment fs = new FlightSegment();
                    fs.setFlightId(flightId);                  // 自增生成的主键
                    fs.setSegmentInstanceId(segmentInstanceIds.get(i)); // 根据索引取对应的航段 ID
                    fs.setSegOrder(i + 1);                           // 顺序从 1 开始：1, 2...
                    return fs;
                })
                .toList();
    }

    private Map<String,String> parseToMap(Flight flight){

        Map<String,String> redisData = new HashMap<>();
        
        // 追加所有的安全判空，防止Flight里面的字段因为未成功注入抛出NPE
        redisData.put("id", flight.getId() != null ? flight.getId().toString() : "");
        redisData.put("flight_no", flight.getFlightNo() != null ? flight.getFlightNo() : "");
        redisData.put("dept_city", flight.getDeptCity() != null ? flight.getDeptCity() : "");
        redisData.put("arr_city", flight.getArrCity() != null ? flight.getArrCity() : "");
        redisData.put("flight_date", flight.getFlightDate() != null ? flight.getFlightDate().toString() : "");
        redisData.put("total_price", flight.getTotalPrice() != null ? flight.getTotalPrice().toString() : "0");

        // 追加时间和详细的字段，防止前台需要时获取不到
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        if (flight.getDeptTime() != null) {
            redisData.put("dept_time", flight.getDeptTime().format(formatter));
        }
        if (flight.getArrTime() != null) {
            redisData.put("arr_time", flight.getArrTime().format(formatter));
        }

        return redisData;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String cancelFlight(Long flightId) {


        return "";
    }
}