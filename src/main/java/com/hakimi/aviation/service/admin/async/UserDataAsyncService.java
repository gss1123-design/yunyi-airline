package com.hakimi.aviation.service.admin.async;

import com.hakimi.aviation.config.RedisKey;
import com.hakimi.aviation.mapper.OrderMapper;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class UserDataAsyncService {

    @Resource
    private OrderMapper orderMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;


    @Async("bookingTaskExecutor")  //登录是低并发操作 复用之前的线程池即可
    public void preheatUserOrder(Long userId) {

        System.out.println("用户已登录 自动的异步加载行程记录到Redis");

        List<Long> orderHistoryInDb = orderMapper.getOrderHistory(userId);

        // 先转成String列表，哨兵也转成字符串
        List<String> elements = new ArrayList<>();
        elements.add("-1");  // 哨兵
        orderHistoryInDb.forEach(id -> elements.add(String.valueOf(id)));

        stringRedisTemplate.opsForSet().add(
                RedisKey.ORDER_NOT_FINISH_KEY + userId,
                elements.toArray(new String[0])  // 转成String[]
        );

        stringRedisTemplate.expire(
                RedisKey.ORDER_NOT_FINISH_KEY + userId,
                7, TimeUnit.DAYS
        );

    }

}
