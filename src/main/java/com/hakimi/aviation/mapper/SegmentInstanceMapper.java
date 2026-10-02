package com.hakimi.aviation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hakimi.aviation.entity.SegmentInstance;
import org.apache.ibatis.annotations.Param;

public interface SegmentInstanceMapper extends BaseMapper<SegmentInstance> {

    int deductStockByFlightId(@Param("flight_id") Long flightId, @Param("num") Integer num);

    int rollbackStockByFlightId(@Param("flight_id") Long flightId, @Param("num") Integer num);

}
