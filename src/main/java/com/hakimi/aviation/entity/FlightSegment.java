package com.hakimi.aviation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("flight_segment")
public class FlightSegment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long flightId;

    private Long segmentInstanceId;

    // tinyint 映射为 Integer 即可，方便 Java 层面对比和计算
    private Integer segOrder;
}
