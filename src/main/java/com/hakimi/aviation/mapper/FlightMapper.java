package com.hakimi.aviation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hakimi.aviation.entity.Flight;
import com.hakimi.aviation.model.request.flight.FlightSearchRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@Mapper
public interface FlightMapper extends BaseMapper<Flight> {

    List<Flight> searchFlight(@Param("request") FlightSearchRequest request);

}
