package com.hakimi.aviation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hakimi.aviation.entity.TicketOrder;
import com.hakimi.aviation.model.vo.OrderVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OrderMapper extends BaseMapper<TicketOrder> {

    List<Long> getOrderHistory(@Param("user_id") Long userId);

    int cancelUnpaidOrder(@Param("order_id") Long orderId);

    int updateStatusToPaid(@Param("order_id") Long orderId, @Param("pay_trade_no") String tradeNo);

    int updateStatusToRefunding(@Param("order_id") Long orderId,@Param("user_id") Long userId);

    int updateStatusToRefunded(@Param("order_id") Long orderId,@Param("user_id") Long userId);

    //退款消息发送失败时的补偿：将 REFUNDING 回滚为 PAID，允许用户重试
    int revertRefundingToPaid(@Param("order_id") Long orderId,@Param("user_id") Long userId);

    String selectStatusByOrderId(@Param("order_id") Long orderId);

    Integer selectSeatOffsetById(@Param("order_id") Long orderId);

    //查询某用户的订单列表（LEFT JOIN 航班信息），status 为空则查全部，按下单时间倒序
    List<OrderVO> listOrdersByUser(@Param("userId") Long userId, @Param("status") String status);

    //查询单个订单详情（含航班信息），带 userId 做归属校验，非本人返回 null
    OrderVO getOrderDetail(@Param("orderId") Long orderId, @Param("userId") Long userId);

}
