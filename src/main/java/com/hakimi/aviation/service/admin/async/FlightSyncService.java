package com.hakimi.aviation.service.admin.async;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hakimi.aviation.entity.Flight;
import com.hakimi.aviation.entity.FlightSegment;
import com.hakimi.aviation.entity.SegmentInstance;
import com.hakimi.aviation.es.FlightIndexDoc;
import com.hakimi.aviation.mapper.FlightMapper;
import com.hakimi.aviation.mapper.FlightSegmentMapper;
import com.hakimi.aviation.mapper.SegmentInstanceMapper;
import com.hakimi.aviation.repository.FlightIndexRepository;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class FlightSyncService {

    @Autowired
    private FlightIndexRepository flightIndexRepository; // 刚刚创建的，绝对不猜

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private FlightMapper flightMapper;

    @Resource
    private FlightSegmentMapper flightSegmentMapper;

    @Autowired
    private SegmentInstanceMapper segmentInstanceMapper;

    /**
     * 生产级数据初始化：分批次 + 批量 IN 查询 + 内存组装
     */
    public void syncAllFlights() {
        int pageSize = 500;
        int current = 1;
        long totalProcessed = 0;

        // 先做第一次查询，获取总页数
        Page<Flight> pageParam = new Page<>(current, pageSize);
        Page<Flight> flightPage = flightMapper.selectPage(pageParam, null);

        // 如果数据库压根没数据，直接跑路
        if (flightPage.getRecords() == null || flightPage.getRecords().isEmpty()) {
            System.out.println("数据库空空如也，无需同步。");
            return;
        }

        // 获取总页数
        long totalPages = flightPage.getPages();

        while (current <= totalPages) {
            // 如果不是第一页（第一页上面查过了），则重新查询
            if (current > 1) {
                pageParam = new Page<>(current, pageSize);
                flightPage = flightMapper.selectPage(pageParam, null);
            }

            List<Flight> flights = flightPage.getRecords();

            if (flights == null || flights.isEmpty()) {
                break; // 处理完毕，退出循环
            }

            // 2. 提取这 500 个航班的 ID 集合
            List<Long> flightIds = flights.stream()
                    .map(Flight::getId)
                    .collect(Collectors.toList());

            List<FlightSegment> flightSegments = flightSegmentMapper.selectList(
                    new LambdaQueryWrapper<FlightSegment>()
                            .in(FlightSegment::getFlightId, flightIds)
                            .orderByAsc(FlightSegment::getFlightId, FlightSegment::getSegOrder)
            );

            // 3. 提取所有真实的 segment_instance_id
            List<Long> instanceIds = flightSegments.stream()
                    .map(FlightSegment::getSegmentInstanceId)
                    .collect(Collectors.toList());

            // 4. 从 segment_instance 表批量拉取真实的航段实例数据
            // (防空指针：如果 instanceIds 为空会报错，实战中记得判空)
            List<SegmentInstance> instances = segmentInstanceMapper.selectBatchIds(instanceIds);

            // 5. 将航段实例转成 Map 方便快速查找 (Key: 航段实例ID, Value: 航段实例对象)
            Map<Long, SegmentInstance> instanceMap = instances.stream()
                    .collect(Collectors.toMap(SegmentInstance::getId, i -> i));

            // 6. 将航段实例绑定回对应的 FlightId (极其关键的一步)
            Map<Long, List<SegmentInstance>> segmentsMap = new HashMap<>();
            for (FlightSegment fs : flightSegments) {
                SegmentInstance instance = instanceMap.get(fs.getSegmentInstanceId());
                if (instance != null) {
                    // 利用 computeIfAbsent 优雅地构建一对多关系
                    segmentsMap.computeIfAbsent(fs.getFlightId(), k -> new ArrayList<>()).add(instance);
                }
            }

            // 7. 准备批量写入 ES 的集合
            List<FlightIndexDoc> esDocsToSave = new ArrayList<>();

            // 8. 遍历当前的 500 个航班，纯内存操作，零数据库 IO
            for (Flight flight : flights) {
                List<SegmentInstance> segments = segmentsMap.get(flight.getId());

                // 防御性编程：如果航班没有关联的航段，说明是脏数据，跳过
                if (segments == null || segments.isEmpty()) {
                    continue;
                }

                // 因为 SQL 里已经排过序，直接取头尾即可
                LocalDateTime firstDeptTime = segments.get(0).getDeptTime();
                LocalDateTime lastArrTime = segments.get(segments.size() - 1).getArrTime();

                // 组装 ES 文档
                FlightIndexDoc esDoc = new FlightIndexDoc();
                esDoc.setId(flight.getId());
                esDoc.setFlightNo(flight.getFlightNo());
                esDoc.setDeptCity(flight.getDeptCity());
                esDoc.setArrCity(flight.getArrCity());
                esDoc.setFlightDate(flight.getFlightDate());
                esDoc.setTotalPrice(flight.getTotalPrice());
                esDoc.setFirstDeptTime(firstDeptTime);
                esDoc.setLastArrTime(lastArrTime);

                esDocsToSave.add(esDoc);

                // --- Redis 同步逻辑 (保持不变，覆盖与先删后插保证幂等) ---
                String infoKey = "info:flight:" + flight.getId();
                Map<String, String> flightInfoMap = Map.of(
                        "flight_no", flight.getFlightNo(),
                        "total_price", flight.getTotalPrice().toString()
                );
                redisTemplate.opsForHash().putAll(infoKey, flightInfoMap);

                String routeKey = "route:flight:" + flight.getId();
                redisTemplate.delete(routeKey);
                List<String> segIds = segments.stream().map(seg -> String.valueOf(seg.getId())).collect(Collectors.toList());
                if (!segIds.isEmpty()) {
                    redisTemplate.opsForList().rightPushAll(routeKey, segIds);
                }

                for (SegmentInstance seg : segments) {
                    String stockKey = "stock:seg:" + seg.getId();
                    redisTemplate.opsForValue().set(stockKey, String.valueOf(seg.getAvailableSeats()));
                }
            }

            // 7. 批量将这 500 条数据 Upsert 进 ES (大大减少 ES 的网络连接开销)
            if (!esDocsToSave.isEmpty()) {
                flightIndexRepository.saveAll(esDocsToSave);
            }

            totalProcessed += flights.size();
            System.out.println("成功处理第 " + current + " 页 / 共 " + totalPages + " 页");

            // 关键：在这里自增，并判定循环
            current++;
        }

        System.out.println("🚀 全量同步完成！共同步有效航班: " + totalProcessed + " 条");
    }
}
