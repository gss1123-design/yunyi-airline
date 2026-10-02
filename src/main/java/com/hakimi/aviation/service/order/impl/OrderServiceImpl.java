package com.hakimi.aviation.service.order.impl;

import com.hakimi.aviation.config.RedisKey;
import com.hakimi.aviation.entity.Flight;
import com.hakimi.aviation.entity.TicketOrder;
import com.hakimi.aviation.enums.BizCodeEnum;
import com.hakimi.aviation.exception.BizException;
import com.hakimi.aviation.mapper.FlightMapper;
import com.hakimi.aviation.mapper.OrderMapper;
import com.hakimi.aviation.mapper.SegmentInstanceMapper;
import com.hakimi.aviation.message.config.RabbitMQConfig;
import com.hakimi.aviation.message.order.RefundMessage;
import com.hakimi.aviation.model.request.order.CancelOrderRequest;
import com.hakimi.aviation.model.request.order.RefundRequest;
import com.hakimi.aviation.model.vo.CancelOrderVO;
import com.hakimi.aviation.model.vo.OrderRefundVO;
import com.hakimi.aviation.model.vo.OrderVO;
import com.hakimi.aviation.service.order.OrderService;
import com.hakimi.aviation.util.SeatUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class OrderServiceImpl implements OrderService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RabbitTemplate rabbitTemplate;

    @Resource
    private OrderMapper orderMapper;

    @Resource
    private FlightMapper flightMapper;

    @Resource
    private SegmentInstanceMapper segmentInstanceMapper;

    @Autowired
    @Qualifier("rollbackStockScript")
    private DefaultRedisScript<Long> rollbackScript;


    @Override
    @Transactional(rollbackFor = Exception.class)
    public CancelOrderVO cancelOrder(CancelOrderRequest request, Long userId) {

        Long orderId = request.getOrderId();

        Long removeResult = stringRedisTemplate.opsForSet().remove(
                RedisKey.ORDER_UNPAID_KEY + userId,
                orderId.toString()
        );

        if(removeResult == null || removeResult == 0){
            //NOTE 如果删除失败（已被删除）或者为空（异常） 则此次回调已经失败 可能订单被取消、已支付（非正常情况）或者是重复回调
            throw new BizException(BizCodeEnum.ORDER_MISS_OR_EXPIRED);
        }

        int updateRows = orderMapper.cancelUnpaidOrder(orderId);
        if(updateRows == 0){
            //NOTE 到这里说明取消失败：订单不存在 或 订单状态已经为 CANCELLED/不为 UNPAID 直接报告此（未支付）订单不存在
            throw new BizException(BizCodeEnum.ORDER_MISS_OR_EXPIRED);
        }

        Long flightId;
        Integer seatOffset;

        List<Object> hashKeys = new ArrayList<>();
        hashKeys.add("flightId");
        hashKeys.add("seat_offset");
        //到这里说明修改成功，开始释放 Redis 库存 与 座位, 先从 Redis 订单快照获取所需参数，数据库查库兜底
        List<Object> snapshotValues = stringRedisTemplate.opsForHash().multiGet(
                RedisKey.ORDER_SNAPSHOT_KEY + orderId,
                hashKeys
        );

        Object flightIdObj = snapshotValues.get(0);
        Object seatOffsetObj = snapshotValues.get(1);
        if(flightIdObj == null || seatOffsetObj == null){
            //降级到数据库
            log.warn("订单:{}的快照信息丢失 正在降级到数据库查询",orderId);
            TicketOrder ticketOrder = orderMapper.selectById(orderId);
            if(ticketOrder == null){
                throw new BizException(BizCodeEnum.ORDER_MISS_OR_EXPIRED);
            }
            flightId = ticketOrder.getFlightId();
            seatOffset = ticketOrder.getSeatOffset();
        }

        else {
            // 先转成 String，再用 parse 方法转换成对应的数值类型
            flightId = Long.parseLong(String.valueOf(flightIdObj));
            seatOffset = Integer.parseInt(String.valueOf(seatOffsetObj));
        }

        String unpaidKey = RedisKey.ORDER_UNPAID_KEY + userId;

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

        stringRedisTemplate.delete(
                RedisKey.ORDER_SNAPSHOT_KEY + orderId
        );

        return parseToCancelOrderVO(orderId);
    }

    private CancelOrderVO parseToCancelOrderVO(Long orderId){

        CancelOrderVO cancelOrderVO = new CancelOrderVO();
        cancelOrderVO.setOrderId(orderId);
        cancelOrderVO.setCancelTime(LocalDateTime.now());
        
        return cancelOrderVO;
    }

    @Override
    public OrderRefundVO refundOrder(RefundRequest request, Long userId) {

        // DISCUSS 考虑是否可以加入分布式锁降低重复请求造成DB压力和异常率

        Long orderId = request.getOrderId();

        TicketOrder ticketOrder = orderMapper.selectById(orderId);

        RefundMessage refundMessage = this.checkAndParse(ticketOrder);

        int updateRow = orderMapper.updateStatusToRefunding(orderId, userId);

        //订单状态的修改失败，可能是重复操作或者非当前用户机票/订单状态异常
        if(updateRow != 1){
            //抛出异常,终止此次请求
            throw new BizException(BizCodeEnum.ORDER_REFUND_FAILED);
        }

        //发送消息：状态已提交为 REFUNDING 后再发，保证消费者能读到最新状态。
        //若发送失败（如 MQ 宕机），补偿性地把状态回滚到 PAID，避免订单卡死在 REFUNDING 且用户无法重试。
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.REFUND_EXCHANGE,
                    RabbitMQConfig.REFUND_ROUTING_KEY,
                    refundMessage
            );
        } catch (Exception e) {
            log.error("退款消息发送失败，回滚订单 {} 状态至 PAID", orderId, e);
            orderMapper.revertRefundingToPaid(orderId, userId);
            throw new BizException(BizCodeEnum.ORDER_REFUND_FAILED);
        }

        // DISCUSS: 用户此时的订单还没有真正完成退款，考虑此处要不要及时删除缓存与DB中的用户行程，如果此处删除行程，若用户立马复购 且后续退款又失败，可能造成纠纷

        //返回一个 VO
        return OrderRefundVO.builder()
                .orderId(orderId)
                // 真实退款金额在 checkAndParse 里已经算好了，直接从 message 里拿
                .expectedRefundAmount(refundMessage.getRefundAmount())
                .status("REFUNDING")
                .promptMessage("退款申请已受理！系统正在向支付宝发起退款，预计1-3个工作日内原路退回。")
                .build();
    }

    /**
     * 此方法用以校验 退款请求的合法性（订单状态、起飞时间），计算手续费的扣减，最后打包成退款需要的消息
     * @param ticketOrder 查出的实体类
     * @return 消费者需要的消息
     */
    private RefundMessage checkAndParse(TicketOrder ticketOrder){

        if(!"PAID".equals(ticketOrder.getStatus())){
            throw new BizException(BizCodeEnum.ORDER_CAN_NOT_REFUND);
        }

        RefundMessage message = new RefundMessage();

        String redisKey = RedisKey.INFO_FLIGHT + ticketOrder.getFlightId();

        // Redis 序列化与日期解析
        String flightDateStr = (String) stringRedisTemplate.opsForHash().get(redisKey, "flight_date");
        LocalDate flightDate;

        if (flightDateStr != null) {
            flightDate = LocalDate.parse(flightDateStr);
        } else {
            // 缓存击穿兜底
            Flight flight = flightMapper.selectById(ticketOrder.getFlightId());
            if (flight == null) {
                throw new BizException(BizCodeEnum.FLIGHT_INFO_MISS_REFUND_FAILED);
            }
            flightDate = flight.getFlightDate();
            // TODO: 把查出来的日期写回 Redis，修复缓存
        }

        message.setOrderId(ticketOrder.getId());
        message.setUserId(ticketOrder.getUserId());
        message.setPayTradeNo(ticketOrder.getPayTradeNo());
        message.setOutRequestNo("REFUND_" + ticketOrder.getPayTradeNo());
        message.setFlightId(ticketOrder.getFlightId());
        message.setTicketCount(1);
        message.setSeatOffset(ticketOrder.getSeatOffset());


        //TODO 这里简单的按三天内扣减百分之二十计算，后续可以细化
        BigDecimal totalPrice = ticketOrder.getTotalPrice();

        // 如果当前时间 + 3天 已经超过了起飞日期，说明距离起飞不足3天了
        if (LocalDate.now().plusDays(3).isAfter(flightDate)) {
            // 扣除 20% 手续费
            // 使用 BigDecimal 保证财务级精度，防止出现无限小数报错，保留两位小数，向下取整
            BigDecimal refundAmount = totalPrice.multiply(new BigDecimal("0.8"))
                    .setScale(2, RoundingMode.DOWN);
            message.setRefundAmount(refundAmount);
        } else {
            // 全额退款
            message.setRefundAmount(totalPrice);
        }

        return message;

    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void finishRefund(Long orderId, Long userId, Long flightId, int ticketCount) {

        // 修改数据库订单状态: REFUNDING -> REFUNDED
        int updateRows = orderMapper.updateStatusToRefunded(orderId, userId);
        if(updateRows != 1){
            // DISCUSS 这一步按理是不会触发的？
            log.warn("订单 {} 已处理或状态异常，操作失败", orderId);
            // DISCUSS 如果真的到了这一步，抛出异常会让事务回滚
            throw new BizException(BizCodeEnum.FINAL_REFUND_UPDATE_FAILED);
        }

        // NOTE 正常执行库存回滚
        segmentInstanceMapper.rollbackStockByFlightId(flightId, ticketCount);

    }

    @Override
    public List<OrderVO> listOrders(Long userId, String status) {
        List<OrderVO> orders = orderMapper.listOrdersByUser(userId, status);
        //根据座位偏移量补充可读的座位号
        orders.forEach(o -> o.setExactSeat(SeatUtil.toSeatNo(o.getSeatOffset())));
        return orders;
    }

    @Override
    public OrderVO getOrderDetail(Long orderId, Long userId) {
        OrderVO order = orderMapper.getOrderDetail(orderId, userId);
        if (order == null) {
            //查不到（不存在或非本人），统一按"订单不存在"处理，避免泄露他人订单
            throw new BizException(BizCodeEnum.ORDER_MISS_OR_EXPIRED);
        }
        order.setExactSeat(SeatUtil.toSeatNo(order.getSeatOffset()));
        return order;
    }
}
