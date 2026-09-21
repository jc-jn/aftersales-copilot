package com.aftersales.copilot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.mybatis.spring.annotation.MapperScan;

@SpringBootApplication
@MapperScan("com.aftersales.copilot")
public class AfterSalesApplication {
    public static void main(String[] args) {
        SpringApplication.run(AfterSalesApplication.class, args);
    }
}
