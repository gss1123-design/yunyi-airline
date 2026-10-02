package com.hakimi.aviation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hakimi.aviation.entity.FlightSegment;
import com.hakimi.aviation.entity.SegmentInstance;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface FlightSegmentMapper extends BaseMapper<FlightSegment> {

    List<SegmentInstance> selectByFlightIds(@Param("flight_ids") List<Long> flightIds);

    int insertBatch(@Param("entity_list") List<FlightSegment> segments);

}
