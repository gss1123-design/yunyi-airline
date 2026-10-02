package com.hakimi.aviation.model.request.flight;

import lombok.Data;

@Data
public class BookingRequest {

    private Long flightId;

    private Long userId;

    private String seatPrefer;

}
