package com.hakimi.aviation.service.admin;

import com.hakimi.aviation.model.vo.FlightSaleStateVO;

public interface FlightSaleGuardService {

    FlightSaleStateVO getSaleState(Long flightId);

    FlightSaleStateVO resumeSale(Long flightId);
}
