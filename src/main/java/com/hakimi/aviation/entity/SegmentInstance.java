package com.hakimi.aviation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("segment_instance")
public class SegmentInstance {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long templateId;

    // datetime 对应 LocalDateTime，精确到秒
    private LocalDateTime deptTime;

    private LocalDateTime arrTime;

    private Integer availableSeats;

    private BigDecimal price;

    private String status;

    // 🚨 乐观锁神器！MyBatis-Plus 看到这个注解会自动处理并发版本控制
    @Version
    private Integer version;
}