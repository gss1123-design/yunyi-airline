package com.hakimi.aviation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("flight")
public class Flight {

    // 明确使用数据库自增，避开雪花算法的坑
    @TableId(type = IdType.AUTO)
    private Long id;

    private String flightNo;

    private String deptCity;

    private String arrCity;

    private LocalDate flightDate;

    @TableField("dept_time")
    private LocalDateTime deptTime;

    @TableField("arr_time")
    private LocalDateTime arrTime;

    // 金额必须用 BigDecimal
    private BigDecimal totalPrice;
}
