package com.hakimi.aviation.service.flight;

import com.hakimi.aviation.entity.Flight;
import com.hakimi.aviation.model.request.flight.BookingRequest;
import com.hakimi.aviation.model.request.flight.FlightSearchRequest;
import com.hakimi.aviation.model.vo.FlightSearchVO;
import com.hakimi.aviation.model.vo.TicketOrderVO;

import java.util.List;

public interface FlightService {

    List<Flight> searchFlight(FlightSearchRequest request);

    List<FlightSearchVO> searchFlightWithCache(FlightSearchRequest request);

    TicketOrderVO bookingFlight(BookingRequest request, Long userId, String userName);


}
