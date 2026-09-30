package com.hmall.item.controller;

import com.hmall.api.dto.customer.CustomerItemDTO;
import com.hmall.common.security.InternalServiceGuard;
import com.hmall.item.service.CustomerItemQueryService;
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
@RequestMapping("/internal/customer/items")
@RequiredArgsConstructor
public class InternalCustomerItemController {
    private final InternalServiceGuard guard;
    private final CustomerItemQueryService queries;

    @GetMapping
    public List<CustomerItemDTO> search(
            @RequestHeader(value = "X-Internal-Service-Secret", required = false) String serviceSecret,
            @RequestParam(required = false) String key,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer minPrice,
            @RequestParam(required = false) Integer maxPrice,
            @RequestParam(required = false) Integer pageSize) {
        guard.verify(serviceSecret);
        return queries.search(key, brand, category, minPrice, maxPrice, pageSize);
    }

    @GetMapping("/{id}")
    public CustomerItemDTO get(
            @RequestHeader(value = "X-Internal-Service-Secret", required = false) String serviceSecret,
            @PathVariable Long id) {
        guard.verify(serviceSecret);
        CustomerItemDTO item = queries.get(id);
        if (item == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到可查询的商品");
        }
        return item;
    }
}
