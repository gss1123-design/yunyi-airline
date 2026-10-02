package com.hakimi.aviation;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableAsync
@EnableTransactionManagement
@MapperScan("com.hakimi.aviation.mapper")
public class AviationSystemApplication {

	public static void main(String[] args) {
		SpringApplication.run(AviationSystemApplication.class, args);
	}

}
