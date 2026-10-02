package com.hakimi.aviation.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FlightSaleStateVO {

    private Long flightId;

    private boolean salePaused;

    /**
     * 故障来源，例如 ROUTE_MISSING、STOCK_MISSING:123。
     */
    private String reason;
}
