package com.hakimi.aviation.controller;

import com.hakimi.aviation.component.FlightData.BlueprintLoader;
import com.hakimi.aviation.entity.Flight;
import com.hakimi.aviation.model.request.flight.CreateFlightRequest;
import com.hakimi.aviation.model.vo.FlightSaleStateVO;
import com.hakimi.aviation.service.admin.HandleFlightService;
import com.hakimi.aviation.service.admin.FlightSaleGuardService;
import com.hakimi.aviation.service.admin.async.FlightSyncService;
import com.hakimi.aviation.service.flight.FlightDataService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/dev")
@Slf4j
public class DevController {

    @Resource
    private FlightSyncService flightSyncService;

    @Resource
    private HandleFlightService handleFlightService;

    @Resource
    private FlightSaleGuardService flightSaleGuardService;

    private final FlightDataService flightDataService;
    private final BlueprintLoader blueprintLoader;

    public DevController(FlightDataService flightDataService, BlueprintLoader blueprintLoader) {
        this.flightDataService = flightDataService;
        this.blueprintLoader = blueprintLoader;
    }

    /**
     * 手动触发数据初始化
     * 访问路径示例: GET http://localhost:8080/dev/init?days=1
     */
    @GetMapping("/init")
    public String initData(@RequestParam(defaultValue = "1") Integer days) {
        log.info(">>> [哈航-B端] 收到手动初始化请求，准备生成 {} 天后的数据...", days);

        // 1. 加载蓝图
        var blueprints = blueprintLoader.loadBlueprints("flight_blueprints.json");

        // 2. 确定目标日期
        LocalDate targetDate = LocalDate.now().plusDays(days);

        // 3. 调用 Service
        flightDataService.generateDailyData(targetDate, blueprints);

        return "SUCCESS: " + targetDate + " 的试金石数据已生成，请检查数据库与 Redis。";
    }

    /**
     * 同步数据库到 Redis 与 ElasticSearch
     * @return 若 service 层未抛出异常 则直接返回 SUCCESS
     */
    @GetMapping("flight/sync")
    public String syncFlight(){

        flightSyncService.syncAllFlights();

        return "SUCCESS";
    }

    /**
     * 创建新的航班 落库并同步到ES与Redis 新航班必须要在已有航段实例基础上才能成功创建
     * @param request DTO 只包含 必要信息
     * @return 航班实例对象 仅作后台日志记录使用
     */
    @PostMapping("flight/new")
    public Flight createNewFlight(@RequestBody CreateFlightRequest request){

        Flight newFlight = handleFlightService.createNewFlight(request);

        return newFlight;
    }

    /** 查询航班是否因 Redis 路由或库存数据异常而暂停销售。 */
    @GetMapping("flight/{flightId}/sale-state")
    public FlightSaleStateVO getFlightSaleState(@PathVariable("flightId") Long flightId) {
        return flightSaleGuardService.getSaleState(flightId);
    }

    /**
     * 修复并同步航班 Redis 数据后，由 B 端确认恢复销售。
     * Service 会重新校验数据库航班、Redis 路由和每个航段库存。
     */
    @PostMapping("flight/{flightId}/sale-resume")
    public FlightSaleStateVO resumeFlightSale(@PathVariable("flightId") Long flightId) {
        return flightSaleGuardService.resumeSale(flightId);
    }

}
