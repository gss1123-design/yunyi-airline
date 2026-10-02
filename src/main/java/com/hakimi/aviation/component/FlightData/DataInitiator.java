package com.hakimi.aviation.component.FlightData;

import com.hakimi.aviation.service.flight.FlightDataService;
import org.springframework.boot.CommandLineRunner;

import java.time.LocalDate;


public class DataInitiator implements CommandLineRunner {

    private final FlightDataService flightDataService;
    private final BlueprintLoader blueprintLoader;

    public DataInitiator(FlightDataService flightDataService, BlueprintLoader blueprintLoader) {
        this.flightDataService = flightDataService;
        this.blueprintLoader = blueprintLoader;
    }

    @Override
    public void run(String... args) {
        // 1. 读取蓝图
        var blueprints = blueprintLoader.loadBlueprints("flight_blueprints.json");

        // 2. 生成明天的航班数据
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        flightDataService.generateDailyData(tomorrow, blueprints);

        System.out.println(">>> [哈航] 试金石数据点火成功！请检查数据库和 Redis。");
    }
}
