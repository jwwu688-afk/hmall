package com.hmall.trade;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@MapperScan("com.hmall.trade.mapper")
@EnableFeignClients(basePackages = "com.hmall.api.client")
@SpringBootApplication
public class TradeApplication {
    public static void main(String[] args) {

        SpringApplication.run(TradeApplication.class, args);
    }

}