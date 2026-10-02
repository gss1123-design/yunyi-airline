package com.hakimi.aviation.controller;

import com.hakimi.aviation.annotations.LoginOptional;
import com.hakimi.aviation.common.JsonData;
import com.hakimi.aviation.entity.Flight;
import com.hakimi.aviation.model.request.flight.BookingRequest;
import com.hakimi.aviation.model.request.flight.FlightSearchRequest;
import com.hakimi.aviation.model.vo.FlightSearchVO;
import com.hakimi.aviation.model.vo.TicketOrderVO;
import com.hakimi.aviation.service.flight.FlightService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("api/v1/pri/flight")
public class FlightController {

    @Resource
    private FlightService flightService;

    /**
     * 低性能搜索接口 直连数据库
     * @param request DTO
     * @return List<Flight>
     */
    @PostMapping("search_flight")
    @LoginOptional
    public JsonData<List<Flight>> searchFlight(@RequestBody FlightSearchRequest request){

        List<Flight> flights = flightService.searchFlight(request);

        return flights == null || flights.isEmpty() ?
                JsonData.buildError("未查询到航班信息") :
                JsonData.buildSuccess(flights,"已查询到航班信息");
    }

    /**
     * 高性能搜索接口 ES + Redis
     * @return VO 列表
     */
    @PostMapping("search")
    @LoginOptional
    public JsonData<List<FlightSearchVO>> searchFlightWithCache(@RequestBody FlightSearchRequest request){

        List<FlightSearchVO> flightSearchVOS = flightService.searchFlightWithCache(request);

        return !flightSearchVOS.isEmpty()?
                JsonData.buildSuccess(flightSearchVOS,"航班查询成功") :
                JsonData.buildError("未查询到航班信息");
    }


    /**
     * 预定航班 当请求发送 会扣减一个库存 并生成一条订单记录 订单默认状态为未支付 暂仅对Redis库存扣减 不扣减数据库库存
     * 用户在此阶段可以选择先不支付 若超过指定时间未支付 库存回滚 订单失效
     * @return 订单实例
     */
    @PostMapping("booking")
    public JsonData<TicketOrderVO> booking(@RequestBody BookingRequest request,HttpServletRequest servletRequest){

        //从 HTTP 请求里获取 JWT token 解析出用户数据 不信赖前端数据
        Long userId = (Long) servletRequest.getAttribute("user_id");
        String userName = (String) servletRequest.getAttribute("name");

        TicketOrderVO ticketOrderVO = flightService.bookingFlight(request,userId,userName);

        return JsonData.buildSuccess(ticketOrderVO,"预订成功，请在15分钟内完成支付");
    }



}
