package com.hakimi.aviation.service.admin.async;

import com.hakimi.aviation.config.RedisKey;
import com.hakimi.aviation.entity.TicketOrder;
import com.hakimi.aviation.message.config.RabbitMQConfig;
import com.hakimi.aviation.message.order.CancelOrderMessage;
import jakarta.annotation.Resource;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class BookingAsyncService {

    @Resource
    private RabbitTemplate rabbitTemplate;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Async("bookingTaskExecutor")
    public void postBookingTasks(TicketOrder ticketOrder, Long flightId, Long userId, CancelOrderMessage cancelOrderMessage) {
        //MQ 异步落库
        String routingKey = RabbitMQConfig.FLIGHT_BOOKING_BASE_ROUTING
                + flightId + "." + userId;
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.FLIGHT_BOOKING_ORDER_EXCHANGE,
                routingKey,
                ticketOrder
        );

        //写入未支付订单记录
        stringRedisTemplate.opsForSet().add(
                RedisKey.ORDER_UNPAID_KEY + userId,
                String.valueOf(ticketOrder.getId())
        );
        //设置 unpaid 记录的 TTL 将其设置得稍长 防止提前过期 这样死信消费者和支付回调方法可以将其当作分布式锁使用
        stringRedisTemplate.expire(
                RedisKey.ORDER_UNPAID_KEY + userId,
                24, TimeUnit.HOURS
        );


        //写入订单快照
        // Hash 的 Key: order:snapshot:{orderId}
        String snapshotKey = RedisKey.ORDER_SNAPSHOT_KEY + ticketOrder.getId();
        Integer seatOffset = ticketOrder.getSeatOffset();

        Map<String, String> orderData = new HashMap<>();
        orderData.put("flightId",flightId.toString());
        orderData.put("total_amount", ticketOrder.getTotalPrice().toString());
        orderData.put("subject", "哈航机票预订 乘客 - " + ticketOrder.getPassengerName());
        //写入座位偏移量，方便支付回调方法修改数据库里航段实例的座位
        orderData.put("seat_offset", seatOffset.toString());

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        orderData.put("create_time", ticketOrder.getCreatedAt().format(formatter));

        stringRedisTemplate.opsForHash().putAll(snapshotKey, orderData);
        // 快照设置 TTL 比订单超时时间稍长 防止意外情况
        stringRedisTemplate.expire(snapshotKey, 20, TimeUnit.MINUTES);



        //发送未支付订单的消息到死信队列
        String deadRoutingKey = RabbitMQConfig.ORDER_CANCEL_DEAD_BASE_BINDING
                + "." +ticketOrder.getId();
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.ORDER_CANCEL_DEAD_EXCHANGE,
                deadRoutingKey,
                cancelOrderMessage
        );

    }

}
