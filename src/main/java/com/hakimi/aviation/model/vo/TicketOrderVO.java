package com.hakimi.aviation.model.vo;

import lombok.Data;

@Data
public class TicketOrderVO {
    //订单主键ID，前端后续支付/取消/退款直接使用此数字ID，无需从 orderNo 反解
    private Long orderId;

    private String orderNo;

    private Long flightId;

    private String passengerName;
    //确定的座位号 例如：12A
    private String exactSeat;

    private java.math.BigDecimal totalPrice;

    private String status;

    private int isFinished;

    private java.time.LocalDateTime createdAt;
}
