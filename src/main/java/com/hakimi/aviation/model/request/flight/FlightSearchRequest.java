package com.hakimi.aviation.model.request.flight;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Data
public class FlightSearchRequest {

    private String deptCity;
    private String arrCity;

    // 1: 按起飞时间早到晚 (默认)
    // 2: 按价格从低到高
    private Integer sortType = 1;

    // 强制规约前端传标准的 yyyy-MM-dd
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate flightDate;

}
