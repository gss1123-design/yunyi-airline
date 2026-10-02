package com.hakimi.aviation.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
@EnableAsync
public class ThreadPoolConfig {

    @Bean(name = "smsThreadPool")
    public ThreadPoolExecutor smsThreadPool() {

        //自定义线程工厂，规范命名
        ThreadFactory smsThreadFactory = new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(1);
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "sms-sender-" + threadNumber.getAndIncrement());
                // 设置为非守护线程，确保 JVM 关闭时能尽量把短信发完
                t.setDaemon(false);
                return t;
            }
        };

        return new ThreadPoolExecutor(
                4,                           // 核心线程数
                8,                           // 最大线程数
                60L,                         // 空闲存活时间
                TimeUnit.SECONDS,            // 时间单位
                new ArrayBlockingQueue<>(100), // 有界阻塞队列
                smsThreadFactory,            // 自定义线程工厂
                new ThreadPoolExecutor.CallerRunsPolicy() // 拒绝策略：主线程兜底
        );
    }

    @Bean("bookingTaskExecutor")
    public Executor bookingTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("booking-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

}
