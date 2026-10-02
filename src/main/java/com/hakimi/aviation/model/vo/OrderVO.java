package com.hakimi.aviation.model.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 订单查询 VO：订单信息 + 航班展示信息（由 ticket_order LEFT JOIN flight 装配），
 * 供「我的订单」列表与订单详情共用。
 */
@Data
public class OrderVO {

    // ===== 订单信息 =====
    private Long orderId;
    private String orderNo;
    /** 订单状态：UNPAID / PAID / CANCELLED / REFUNDING / REFUNDED */
    private String status;
    private BigDecimal totalPrice;
    /** 座位偏移量，仅用于计算 exactSeat */
    private Integer seatOffset;
    /** 物理座位号，如 "12A"，由服务层根据 seatOffset 计算 */
    private String exactSeat;
    private String passengerName;
    /** 支付流水号，未支付时为 null */
    private String payTradeNo;
    private LocalDateTime createdAt;

    // ===== 航班展示信息 =====
    private Long flightId;
    private String flightNo;
    private String deptCity;
    private String arrCity;
    private LocalDate flightDate;
    private LocalDateTime deptTime;
    private LocalDateTime arrTime;
}
