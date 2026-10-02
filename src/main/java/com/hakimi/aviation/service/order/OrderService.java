package com.hakimi.aviation.service.order;

import com.hakimi.aviation.model.request.order.CancelOrderRequest;
import com.hakimi.aviation.model.request.order.RefundRequest;
import com.hakimi.aviation.model.vo.CancelOrderVO;
import com.hakimi.aviation.model.vo.OrderRefundVO;
import com.hakimi.aviation.model.vo.OrderVO;

import java.util.List;

public interface OrderService {

    CancelOrderVO cancelOrder(CancelOrderRequest request, Long userId);

    OrderRefundVO refundOrder(RefundRequest request, Long userId);

    void finishRefund(Long orderId, Long userId, Long flightId, int ticketCount);

    //查询当前用户的订单列表，status 为空查全部
    List<OrderVO> listOrders(Long userId, String status);

    //查询单个订单详情（含归属校验）
    OrderVO getOrderDetail(Long orderId, Long userId);

}
