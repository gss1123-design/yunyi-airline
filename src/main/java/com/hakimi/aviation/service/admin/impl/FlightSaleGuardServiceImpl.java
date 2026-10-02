package com.hakimi.aviation.service.admin.impl;

import com.hakimi.aviation.config.RedisKey;
import com.hakimi.aviation.entity.Flight;
import com.hakimi.aviation.enums.BizCodeEnum;
import com.hakimi.aviation.exception.BizException;
import com.hakimi.aviation.mapper.FlightMapper;
import com.hakimi.aviation.model.vo.FlightSaleStateVO;
import com.hakimi.aviation.service.admin.FlightSaleGuardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FlightSaleGuardServiceImpl implements FlightSaleGuardService {

    private final StringRedisTemplate stringRedisTemplate;
    private final FlightMapper flightMapper;

    @Override
    public FlightSaleStateVO getSaleState(Long flightId) {
        if (flightMapper.selectById(flightId) == null) {
            throw new BizException(BizCodeEnum.FLIGHT_ERROR);
        }
        String reason = stringRedisTemplate.opsForValue().get(pauseKey(flightId));
        return new FlightSaleStateVO(flightId, reason != null, reason);
    }

    @Override
    public FlightSaleStateVO resumeSale(Long flightId) {
        Flight flight = flightMapper.selectById(flightId);
        List<String> segmentIds = stringRedisTemplate.opsForList().range(
                RedisKey.ROUTE_FLIGHT + flightId, 0, -1
        );

        if (flight == null || CollectionUtils.isEmpty(segmentIds)) {
            throw new BizException(BizCodeEnum.FLIGHT_ERROR);
        }

        for (String segmentId : segmentIds) {
            String stockValue = stringRedisTemplate.opsForValue().get(RedisKey.STOCK_KEY + segmentId);
            if (!isValidStock(stockValue)) {
                throw new BizException(BizCodeEnum.FLIGHT_ERROR);
            }
        }

        stringRedisTemplate.delete(pauseKey(flightId));
        log.info("B端确认 Redis 航班路由与库存就绪，恢复销售 flightId={}", flightId);
        return getSaleState(flightId);
    }

    private String pauseKey(Long flightId) {
        return RedisKey.FLIGHT_SALE_PAUSE_KEY + flightId;
    }

    private boolean isValidStock(String stockValue) {
        if (stockValue == null) {
            return false;
        }
        try {
            return Long.parseLong(stockValue) >= 0;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }
}
