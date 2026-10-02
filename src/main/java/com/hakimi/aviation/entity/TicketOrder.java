package com.hakimi.aviation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TicketOrder {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String orderNo;

    private Long userId;

    private Long flightId;

    private String passengerName;

    private Integer seatOffset;

    private BigDecimal totalPrice;

    private String payTradeNo;

    //支付状态 默认为UNPAID
    private String status;
    //旅程是否已结束 用以保存历史记录
    private int isFinished;

    private LocalDateTime createdAt;

}
