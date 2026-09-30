package com.hmall.trade.controller;

import com.hmall.api.dto.customer.CustomerLogisticsDTO;
import com.hmall.api.dto.customer.CustomerOrderDTO;
import com.hmall.common.security.InternalServiceGuard;
import com.hmall.trade.service.CustomerOrderQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/internal/customer/orders")
@RequiredArgsConstructor
public class InternalCustomerOrderController {
    private final InternalServiceGuard guard;
    private final CustomerOrderQueryService queries;

    @GetMapping
    public List<CustomerOrderDTO> listOrders(
            @RequestHeader(value = "X-Internal-Service-Secret", required = false) String serviceSecret,
            @RequestParam Long userId,
            @RequestParam(required = false) Integer pageSize) {
        guard.verify(serviceSecret);
        return queries.listOwnedOrders(userId, pageSize);
    }

    @GetMapping("/{id}")
    public CustomerOrderDTO getOrder(
            @RequestHeader(value = "X-Internal-Service-Secret", required = false) String serviceSecret,
            @RequestParam Long userId,
            @PathVariable Long id) {
        guard.verify(serviceSecret);
        CustomerOrderDTO order = queries.getOwnedOrder(userId, id);
        if (order == null) {
            throw notFound();
        }
        return order;
    }

    @GetMapping("/{id}/logistics")
    public CustomerLogisticsDTO getLogistics(
            @RequestHeader(value = "X-Internal-Service-Secret", required = false) String serviceSecret,
            @RequestParam Long userId,
            @PathVariable Long id) {
        guard.verify(serviceSecret);
        CustomerLogisticsDTO logistics = queries.getOwnedLogistics(userId, id);
        if (logistics == null) {
            throw notFound();
        }
        return logistics;
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到可查询的订单");
    }
}
