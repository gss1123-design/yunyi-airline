package com.hakimi.aviation.consumer;

import com.alibaba.fastjson.JSONObject;
import com.alipay.api.AlipayClient;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.hakimi.aviation.component.notification.NotificationWebSocketServer;
import com.hakimi.aviation.config.RedisKey;
import com.hakimi.aviation.entity.TicketOrder;
import com.hakimi.aviation.enums.BizCodeEnum;
import com.hakimi.aviation.exception.BizException;
import com.hakimi.aviation.mapper.OrderMapper;
import com.hakimi.aviation.mapper.SegmentInstanceMapper;
import com.hakimi.aviation.message.config.RabbitMQConfig;
import com.hakimi.aviation.message.order.CancelOrderMessage;
import com.hakimi.aviation.message.order.RefundMessage;
import com.hakimi.aviation.service.order.OrderService;
import com.rabbitmq.client.Channel;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class OrderConsumer {

    @Resource
    private OrderMapper orderMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private AlipayClient alipayClient;

    @Resource
    private OrderService orderService;

    @Resource
    private NotificationWebSocketServer notificationWebSocketServer;

    @Autowired
    @Qualifier("rollbackStockScript")
    private DefaultRedisScript<Long> rollbackScript;

    /**
     * 监听正常订单创建队列
     * 注意：这里的常量 RabbitMQConfig.FLIGHT_BOOKING_ORDER_QUEUE 需要你在配置类里对齐
     */
    @RabbitListener(queues = RabbitMQConfig.FLIGHT_BOOKING_ORDER_QUEUE)
    public void handleOrderCreateMessage(TicketOrder ticketOrder,
                                         Channel channel,
                                         @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {

        long orderId = ticketOrder.getId();
        log.info(">>> 接收到异步落库消息，准备插入订单: {}", orderId);

        try {

            orderMapper.insert(ticketOrder);

            //数据库落盘成功后，手动向 MQ 发送 ACK 确认
            // 第二个参数 false 代表只确认当前这条消息，不批量确认
            channel.basicAck(deliveryTag, false);
            log.info(">>> 订单 {} 异步落库成功，已 ACK 回执", orderId);

        } catch (DuplicateKeyException e) {
            //幂等性防御：主键冲突说明是重复消费
            log.warn(">>> 订单 {} 发生主键冲突，触发幂等防御，视为消费成功并直接 ACK", orderId);
            //视为成功，直接告诉 MQ 删掉这条重复的消息
            channel.basicAck(deliveryTag, false);

        } catch (Exception e) {
            //其他未知异常（如数据库宕机、字段超长等）
            log.error(">>> 订单 {} 异步落库失败，进入重试机制: {}", orderId, e.getMessage());
            // 触发 NACK，第三个参数 requeue = true 表示让消息重新回到队列排队，等待下次重试
            // 配合指数退避重试策略，按照 1s, 2s, 4s 的间隔重试
            channel.basicNack(deliveryTag, false, true);
        }
    }

    /**
     * 监听死信传递队列的消费者 负责判断支付状态、回滚库存、修改数据库订单状态
     * @param cancelOrderMessage 消息体
     * @param channel MQ 通道 负责手动确认消费
     * @param deliveryTag 上下文
     * @throws IOException 主要由数据库抛出
     */
    @RabbitListener(queues = RabbitMQConfig.ORDER_CANCEL_QUEUE)
    public void handleOrderCancelMessage(
            CancelOrderMessage cancelOrderMessage,
            Channel channel,
            Message message,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {

        Long userId = cancelOrderMessage.getUserId();
        Long flightId = cancelOrderMessage.getFlightId();
        Long orderId = cancelOrderMessage.getOrderId();
        Integer seatOffset = cancelOrderMessage.getSeatOffset();
        //是否为重试消息
        Boolean isRedelivered = message.getMessageProperties().isRedelivered();

        String unpaidKey = RedisKey.ORDER_UNPAID_KEY + userId;

        try{
            //消费者拿到消息，首先确认未支付状态是否还存在
            //Boolean keyExists = stringRedisTemplate.hasKey(unpaidKey); // 探针 先判断缓存是否过期
            Long removeResult = stringRedisTemplate
                    .opsForSet()
                    .remove(unpaidKey,String.valueOf(orderId));

            if (!isRedelivered && Long.valueOf(0).equals(removeResult)) {
                //NOTE delete unpaid 可以确定 此时订单已被取消或已支付
                // isRedelivered 可以确定 当前消息是否是异常重试的消息 防止部分失败导致的数据不一致 如果是必然异常 短暂的部分失败是不可避免的，但可以通过死信队列记录排查
                log.info(">>> 订单 {} 确认为已支付状态，或当前为重复消息，丢弃死信", orderId);
                channel.basicAck(deliveryTag, false);
                return;

            }

            //NOTE 到这里 可能是正常取消，也可能是异常重试，需要数据库乐观更新
            //乐观更新数据库 将数据库中的订单状态修改为“CANCELLED” 这里只对status为 “UNPAID”的订单进行修改 避免将正常订单取消
            int updatedRows = orderMapper.cancelUnpaidOrder(orderId);
            if(updatedRows == 0 && !isRedelivered){
                //受影响的行数为0且非重试的消息 说明这是一条重复的消息 确认消费后直接丢弃即可
                channel.basicAck(deliveryTag, false);
                return;
            }
            //TODO 此时的Lua已经不用再删除 unpaid 记录了 但不影响 以后可以考虑去掉这个逻辑
            //回滚库存、释放座位位图 并删除相应的记录 用户之后的操作将不会受到影响
            Long rollbackResult = stringRedisTemplate.execute(
                    rollbackScript,
                    List.of(
                            RedisKey.ROUTE_FLIGHT + flightId,      // KEYS[1]
                            RedisKey.ORDER_NOT_FINISH_KEY + userId,// KEYS[2]
                            unpaidKey                              // KEYS[3]
                    ),
                    String.valueOf(orderId),                   // ARGV[1]: 订单ID，用于清理未支付Set
                    String.valueOf(flightId),                  // ARGV[2]: 航班ID，用于清理行程防重Set
                    "1",                                        // ARGV[3]: 退回的票数，传字符串 "1"
                    String.valueOf(seatOffset)
            );

            //结束 手动确认
            channel.basicAck(deliveryTag, false);
            log.info(">>> 订单 {} 超时取消流程全部执行完毕！", orderId);

        }catch(Exception e){
            log.error(">>> 订单 {} 超时取消处理异常，重新入队等待重试: {}", orderId, e.getMessage());
            // 遇到数据库宕机等异常，NACK 并重试
            channel.basicNack(deliveryTag, false, true);
        }

    }

    /**
     * 用以处理退款消息的消费者，避免长事务，故消费者不加事务注解
     * @param message 退款的消息体
     * @param channel 通道
     * @param deliveryTag   上下文
     * @throws IOException  抛出的异常 （受检）
     */
    @RabbitListener(queues = RabbitMQConfig.REFUND_QUEUE)
    public void handlerOrderRefundMessage(
        RefundMessage message,
        Channel channel,
        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag
    ) throws IOException {

        log.info("📥 接收到退款执行消息, orderId: {}, deliveryTag: {}", message.getOrderId(), deliveryTag);

        try {
            // ==========================================
            // 核心业务区
            // 1. 幂等性校验（查库判断是否已经退过款了）
            // 2. 调用第三方支付平台（支付宝/微信）API 进行实际退款
            // 3. 退款成功后：修改数据库订单状态为 REFUNDED
            // 4. 回滚航班座位库存
            // 5. 物理/逻辑删除用户的行程记录
            // ==========================================

            // NOTE: 具体的退款调用和本地事务处理

            // 业务全部执行成功，手动向 Broker 发送 ACK 确认
            // 第二个参数 false 代表不批量确认，只确认当前这一条


            // NOTE 向Redis中 SETNX 插入分布式锁防止重复消费
            Boolean trySetLock = stringRedisTemplate.opsForValue().setIfAbsent(RedisKey.REFUND_LOCK + message.getOrderId(),
                    "Locked",
                    10,
                    TimeUnit.MINUTES);

            if (Boolean.FALSE.equals(trySetLock)){
                //被分布式锁拦住，此为重复消费
                log.warn("订单 {} 正在退款中，触发 Redis 拦截，忽略重复消息", message.getOrderId());
                channel.basicAck(deliveryTag, false);
                return;
            }

            // NOTE 通过分布式锁,进入下一阶段
            try{
                // 修改订单状态，同时做兜底的幂等性校验
                TicketOrder ticketOrder = orderMapper.selectById(message.getOrderId());
                if(ticketOrder == null || !"REFUNDING".equals(ticketOrder.getStatus())){

                    log.warn("订单 {} 状态已被处理 (当前状态: {})，触发数据库兜底幂等，直接 ACK",
                            message.getOrderId(), ticketOrder != null ? ticketOrder.getStatus() : "null");
                    channel.basicAck(deliveryTag, false);
                    return;
                }
                // NOTE 通过校验，开始退款
                // NOTE 调用支付宝第三方接口进行退款 （天然幂等）
                AlipayTradeRefundRequest alipayRequest = new AlipayTradeRefundRequest();

                // 组装业务参数 (推荐使用 fastjson 或者 Jackson 的 ObjectNode)
                JSONObject bizContent = new JSONObject();
                //"out_trade_no"是商户自己的订单号，"trade_no"是支付宝支付后返回的流水号
                bizContent.put("trade_no", message.getPayTradeNo());
                bizContent.put("refund_amount", message.getRefundAmount().toString());
                bizContent.put("out_request_no", message.getOutRequestNo());
                // 可选：退款原因
                bizContent.put("refund_reason", "用户主动发起退票");

                alipayRequest.setBizContent(bizContent.toString());

                AlipayTradeRefundResponse response = alipayClient.execute(alipayRequest);

                if (response.isSuccess()) {
                    log.info("💰 支付宝网关退款成功！流水号: {}", message.getPayTradeNo());

                    // ==========================================
                    // 退款成功后的收尾工作（真正的终态扭转）
                    // ==========================================
                    // NOTE 用短事务先完成订单状态的修改和数据库库存的回滚
                    orderService.finishRefund(message.getOrderId(),
                            message.getUserId(),
                            message.getFlightId(),
                            1);

                    // NOTE 一切正常，到达最后的回滚和行程记录删除阶段


                    // 回滚真实库存（MySQL）与高并发库存（Redis Lua脚本）
                    // NOTE Redis 库存的回滚需要放在最后进行，防止超卖
                    Long rollbackResult = stringRedisTemplate.execute(
                            rollbackScript,
                            List.of(
                                    RedisKey.ROUTE_FLIGHT + message.getFlightId(),      // KEYS[1]
                                    RedisKey.ORDER_NOT_FINISH_KEY + message.getUserId()// KEYS[2]
                                                                  // 不传 KEYS[3] 表示不用删除未支付记录
                            ),
                            String.valueOf(message.getOrderId()),                   // ARGV[1]: 订单ID，用于清理未支付Set
                            String.valueOf(message.getFlightId()),                  // ARGV[2]: 航班ID，用于清理行程防重Set
                            "1",                                        // ARGV[3]: 退回的票数，传字符串 "1"
                            String.valueOf(message.getSeatOffset())
                    );
                    // NOTE 物理/逻辑删除用户的行程记录 已经由 Lua 脚本完成


                    // NOTE 通知用户
                    // 组装一个 JSON 格式的消息告诉前端这是什么类型的通知
                    JSONObject notifyJson = new JSONObject();
                    notifyJson.put("type", "REFUND_SUCCESS");
                    notifyJson.put("orderId", message.getOrderId());
                    notifyJson.put("content", "您的订单已退款成功，金额已原路返回。");

                    notificationWebSocketServer.sendMessage(message.getUserId(), notifyJson.toString());

                } else {
                    // 退款失败（比如沙箱商户余额不足、订单状态异常等）
                    log.error("❌ 支付宝退款受挫！原因: {} - {}", response.getSubCode(), response.getSubMsg());
                    // 此时绝不能 ACK，必须抛出异常让消息重试，或者记录告警人工介入
                    // DISCUSS 此处是否应该将订单状态回滚到 “PAID”？
                    if ("ACQ.TRADE_NOT_EXIST".equals(response.getSubCode()) ||
                            "ACQ.SELLER_BALANCE_NOT_ENOUGH".equals(response.getSubCode()) ||
                            "ACQ.REASON_TRADE_REFUND_FEE_ERR".equals(response.getSubCode())) {

                        log.warn("🚨 触发明确业务驳回，放弃退款。订单号: {}", message.getOrderId());

                        // 状态机回拨：REFUNDING -> PAID
                        // 必须把状态改回去，这样前端轮询接口查到不是 REFUNDING，就会停止轮询！
                        // (需要在 OrderMapper 里加一个 updateStatusToPaid 方法)
                        orderMapper.revertRefundingToPaid(message.getOrderId(), message.getUserId());

                        // 直接 ACK，彻底销毁这条毒消息，不让它重试！
                        channel.basicAck(deliveryTag, false);
                        return;
                    }
                    else {
                        // 4. 只有遇到真正的系统/网络异常（如支付宝系统繁忙、网络超时），才抛出异常等待重试
                        throw new BizException(BizCodeEnum.REFUND_INTERNAL_FAILED);
                    }

                }

            } finally {
                // NOTE 必须释放锁
                stringRedisTemplate.delete(RedisKey.REFUND_LOCK + message.getOrderId());
            }

            channel.basicAck(deliveryTag, false);
            log.info("✅ 退款消息处理成功并 ACK, orderId: {}", message.getOrderId());

        } catch (Exception e) {
            log.error("❌ 退款消息处理发生异常, orderId: {}", message.getOrderId(), e);

            // 发生异常，手动发送 NACK 拒绝消息
            // 第三个参数 requeue = true 代表将消息重新塞回队列尾部，等待下一次重试
            // (后续可以结合重试次数，把 requeue 改为 false，配合死信队列使用)
            channel.basicNack(deliveryTag, false, true);
        }

    }

}
