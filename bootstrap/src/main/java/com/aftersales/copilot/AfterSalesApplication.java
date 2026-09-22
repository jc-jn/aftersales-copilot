package com.aftersales.copilot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@MapperScan("com.aftersales.copilot")
public class AfterSalesApplication {
    public static void main(String[] args) {
        SpringApplication.run(AfterSalesApplication.class, args);
    }
}
