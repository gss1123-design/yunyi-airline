package com.hakimi.aviation.model.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class FlightSearchVO {

    private Long id;

    private String flightNo;

    private String deptCity;

    private String arrCity;

    // MySQL 中的 date 类型
    private LocalDate flightDate;

    // 价格
    private BigDecimal totalPrice;

    // 航段起飞时间
    private LocalDateTime firstDeptTime;

    // 航段降落时间
    private LocalDateTime lastArrTime;

    // 机票库存 通常从 Redis 拉取
    private Integer availableSeats;

}
