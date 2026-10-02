package com.hakimi.aviation.util;

import com.hakimi.aviation.enums.BizCodeEnum;
import com.hakimi.aviation.exception.BizException;
import com.hakimi.aviation.model.request.flight.BookingRequest;
import com.hakimi.aviation.model.request.flight.CreateFlightRequest;
import com.hakimi.aviation.model.request.flight.FlightSearchRequest;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.List;

@Slf4j
public class ValidateRequest {

    public static void ValidateFlightSearchReq(FlightSearchRequest request){

        boolean isLegal = true;

        String deptCity = request.getDeptCity();
        String arrCity = request.getArrCity();
        LocalDate flightDate = request.getFlightDate();
        Integer sortType = request.getSortType();

        if(deptCity == null || deptCity.isEmpty()){
            log.error("航班搜索 - 无效请求 - 出发城市为空");
            isLegal = false;

        }
        if (arrCity == null || arrCity.isEmpty()){
            log.error("航班搜索 - 无效请求 - 目的城市为空");
            isLegal = false;
        }
        if (flightDate == null ){
            log.error("航班搜索 - 无效请求 - 日期无效");
            isLegal = false;
        }
        if(sortType == null){
            log.error("航班搜索 - 无效请求 - 排序规则缺失");
            isLegal = false;
        } else if (sortType != 1 && sortType != 2) {
            log.error("航班搜索 - 无效请求 - 排序规则无效");
            isLegal = false;
        }

        if(!isLegal)
            throw new BizException(BizCodeEnum.ILLEGAL_REQUEST);

    }

    public static void ValidateFlightCreateReq(CreateFlightRequest request){

        String flightNo = request.getFlightNo();
        List<Long> segmentInstanceIds = request.getSegmentInstanceIds();

        if(flightNo == null || flightNo.isEmpty()){
            log.error("航班号不合法");
            throw new BizException(BizCodeEnum.ILLEGAL_REQUEST);
        }

        if(segmentInstanceIds == null || segmentInstanceIds.isEmpty()){
            log.error("航段为空");
            throw new BizException(BizCodeEnum.ILLEGAL_REQUEST);
        }

        if(segmentInstanceIds.size() > 2){
            log.error("航段最多只能有两个");
            throw new BizException(BizCodeEnum.ILLEGAL_SEGMENT_SET);
        }
        for(Long id:segmentInstanceIds){
            if(id < 1){
                log.error("航段配置中有非法 ID：小于1");
                throw new BizException(BizCodeEnum.ILLEGAL_SEGMENT_SET);
            }
        }

    }

    public static void ValidateBookingReq(BookingRequest request){
        
        boolean isLegal = true;
        log.info("开始前置校验预订机票请求合法性");
        
        if(request.getFlightId() == null || request.getFlightId() < 1){
            log.error("机票预订 - 无效请求 - 航班ID为空或非法");
            isLegal = false;
        }
        
        if(request.getUserId() == null || request.getUserId() < 1){
            log.error("机票预订 - 无效请求 - 用户ID为空或非法");
            isLegal = false;
        }
        
        String seatPrefer = request.getSeatPrefer();
        if(seatPrefer == null || seatPrefer.isEmpty()){
            log.error("机票预订 - 无效请求 - 座位偏好为空");
            isLegal = false;
        } else if(!seatPrefer.equalsIgnoreCase("window") && 
                  !seatPrefer.equalsIgnoreCase("aisle") && 
                  !seatPrefer.equalsIgnoreCase("middle")){
            log.error("机票预订 - 无效请求 - 座位偏好值非法: {}", seatPrefer);
            isLegal = false;
        }
        
        if(!isLegal){
            throw new BizException(BizCodeEnum.ILLEGAL_REQUEST);
        }
    }


}
