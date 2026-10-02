package com.hakimi.aviation.model.request.flight;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.hakimi.aviation.entity.SegmentInstance;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 创建新的航班所传 DTO 只包含航班号和关联的航段实例 其余所有字段交给后端填充
 */
@Data
public class CreateFlightRequest {

    private String flightNo;

    //关联的航段实例
    private List<Long> segmentInstanceIds;

}
