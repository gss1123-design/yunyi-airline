package com.hakimi.aviation.service.admin;

import com.hakimi.aviation.entity.Flight;
import com.hakimi.aviation.model.request.flight.CreateFlightRequest;

public interface HandleFlightService {

    Flight createNewFlight(CreateFlightRequest request);

    String cancelFlight(Long flightId);

}
