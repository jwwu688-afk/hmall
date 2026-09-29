package com.hmall.api.client;

import com.hmall.api.dto.customer.CustomerLogisticsDTO;
import com.hmall.api.dto.customer.CustomerOrderDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "trade-service", contextId = "customerTradeClient")
public interface CustomerTradeClient {
    @GetMapping("/internal/customer/orders")
    List<CustomerOrderDTO> listOrders(@RequestHeader("X-Internal-Service-Secret") String serviceSecret,
            @RequestParam("userId") Long userId,
            @RequestParam(value = "pageSize", required = false) Integer pageSize);

    @GetMapping("/internal/customer/orders/{id}")
    CustomerOrderDTO getOrder(@RequestHeader("X-Internal-Service-Secret") String serviceSecret,
            @RequestParam("userId") Long userId,
            @PathVariable("id") Long orderId);

    @GetMapping("/internal/customer/orders/{id}/logistics")
    CustomerLogisticsDTO getLogistics(@RequestHeader("X-Internal-Service-Secret") String serviceSecret,
            @RequestParam("userId") Long userId,
            @PathVariable("id") Long orderId);
}
