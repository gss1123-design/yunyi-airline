package com.hakimi.aviation.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class FlightBlueprintDTO {
    private String flightNo;
    private String deptCity;
    private String arrCity;
    private BigDecimal totalPrice;
    // 咱们之前约定的极简版：只存物理模板的 ID 列表！
    private List<Long> segmentTemplateIds;
}