package com.aftersales.copilot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@MapperScan(basePackages="com.aftersales.copilot", annotationClass=org.apache.ibatis.annotations.Mapper.class)
public class AfterSalesApplication {
    public static void main(String[] args) {
        SpringApplication.run(AfterSalesApplication.class, args);
    }
}
