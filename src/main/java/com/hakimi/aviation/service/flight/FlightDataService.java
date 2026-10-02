package com.hakimi.aviation.service.flight;

import com.hakimi.aviation.config.RedisKey;
import com.hakimi.aviation.dto.FlightBlueprintDTO;
import com.hakimi.aviation.entity.Flight;
import com.hakimi.aviation.entity.FlightSegment;
import com.hakimi.aviation.entity.SegmentInstance;
import com.hakimi.aviation.mapper.FlightMapper;
import com.hakimi.aviation.mapper.FlightSegmentMapper;
import com.hakimi.aviation.mapper.SegmentInstanceMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class FlightDataService {

    @Autowired
    private FlightMapper flightMapper;

    @Autowired
    private SegmentInstanceMapper instanceMapper;

    @Autowired
    private FlightSegmentMapper relationMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 核心引擎：根据蓝图生成指定日期的数据
     */
    // 🚨 必须加事务！要么全成功，要么全回滚，绝不产生脏数据
    @Transactional(rollbackFor = Exception.class)
    public void generateDailyData(LocalDate targetDate, List<FlightBlueprintDTO> blueprints) {

        // 核心查重神器：用来记录【今天】已经生成过的物理航段
        Map<Long, Long> todaySegmentCache = new HashMap<>();

        // 🟢 新增：拓扑收集器！用来暂存“航班ID -> 航段ID列表”的映射关系
        Map<Long, List<String>> flightRouteMap = new HashMap<>();

        for (FlightBlueprintDTO bp : blueprints) {

            // 1. 无脑生成航班壳子
            Flight flight = new Flight();
            flight.setFlightNo(bp.getFlightNo());
            flight.setDeptCity(bp.getDeptCity());
            flight.setArrCity(bp.getArrCity());
            flight.setFlightDate(targetDate);
            flight.setTotalPrice(bp.getTotalPrice());
            flightMapper.insert(flight);

            Long currentFlightId = flight.getId(); // 拿到自增主键

            // 🟢 新增：准备一个小盒子，用来装当前这个航班包含的所有航段 ID
            List<String> currentFlightSegmentIds = new ArrayList<>();

            // 2. 遍历组装航段
            int order = 1;
            for (Long templateId : bp.getSegmentTemplateIds()) {

                Long finalInstanceId;

                // 💡 查重逻辑：如果缓存里有，说明别的航班已经建过了，直接白嫖！
                if (todaySegmentCache.containsKey(templateId)) {
                    finalInstanceId = todaySegmentCache.get(templateId);
                    log.info("航班 {} 完美复用了已存在的航段实例 ID: {}", bp.getFlightNo(), finalInstanceId);
                } else {
                    // 如果没有，老老实实新建一个今日实例
                    SegmentInstance newInst = new SegmentInstance();
                    newInst.setTemplateId(templateId);
                    newInst.setDeptTime(targetDate.atTime(10, 0));
                    newInst.setArrTime(targetDate.atTime(12, 0));
                    newInst.setAvailableSeats(180);
                    newInst.setStatus("OPEN");
                    instanceMapper.insert(newInst);

                    finalInstanceId = newInst.getId();

                    // 🔥 极其通用的 Redis 预热：库存写 180！
                    String redisKey = RedisKey.STOCK_KEY + finalInstanceId;
                    stringRedisTemplate.opsForValue().set(redisKey, "180");

                    // 塞进缓存，造福后面的航班
                    todaySegmentCache.put(templateId, finalInstanceId);
                }

                // 🟢 新增：把拿到的真实航段 ID，按顺序塞进当前航班的小盒子里
                currentFlightSegmentIds.add(String.valueOf(finalInstanceId));

                // 3. 完美缝合：在关联表里把航班和航段锁死
                FlightSegment relation = new FlightSegment();
                relation.setFlightId(currentFlightId);
                relation.setSegmentInstanceId(finalInstanceId);
                relation.setSegOrder(order++);
                relationMapper.insert(relation);
            }

            // 🟢 新增：当前航班的航段都集齐了，把小盒子交给总收集器
            flightRouteMap.put(currentFlightId, currentFlightSegmentIds);
        }

        // 🟢 新增：终极杀招！循环彻底结束后，用 Pipeline 一次性把拓扑关系打进 Redis
        if (!flightRouteMap.isEmpty()) {
            stringRedisTemplate.executePipelined(new SessionCallback<Object>() {
                @Override
                public <K, V> Object execute(RedisOperations<K, V> operations) throws DataAccessException {
                    for (Map.Entry<Long, List<String>> entry : flightRouteMap.entrySet()) {
                        // 组装 Redis Key
                        String routeKey = RedisKey.ROUTE_FLIGHT + entry.getKey();
                        List<String> segmentIds = entry.getValue();

                        // opsForList().rightPushAll() 极其适合存这种有序的 ID 列表
                        operations.opsForList().rightPushAll((K) routeKey, (V[]) segmentIds.toArray());
                    }
                    return null; // 这里必须返回 null，这是 Spring 的规矩
                }
            });
            log.info("====== 成功通过 Pipeline 批量预热了 {} 个航班的拓扑映射！======", flightRouteMap.size());
        }

        log.info("====== 日期 {} 的航班数据已全部生成并预热至 Redis！======", targetDate);
    }
}