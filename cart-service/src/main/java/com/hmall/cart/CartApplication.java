package com.hmall.cart;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

@MapperScan("com.hmall.cart.mapper")
@EnableFeignClients(basePackages = "com.hmall.api.client")
@SpringBootApplication
public class CartApplication {
    public static void main(String[] args) {

        SpringApplication.run(CartApplication.class, args);
    }

}