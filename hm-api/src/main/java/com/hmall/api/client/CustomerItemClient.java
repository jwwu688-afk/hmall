package com.hmall.api.client;

import com.hmall.api.dto.customer.CustomerItemDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "item-service", contextId = "customerItemClient")
public interface CustomerItemClient {
    @GetMapping("/internal/customer/items")
    List<CustomerItemDTO> search(@RequestHeader("X-Internal-Service-Secret") String serviceSecret,
            @RequestParam(value = "key", required = false) String key,
            @RequestParam(value = "brand", required = false) String brand,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "minPrice", required = false) Integer minPrice,
            @RequestParam(value = "maxPrice", required = false) Integer maxPrice,
            @RequestParam(value = "pageSize", required = false) Integer pageSize);

    @GetMapping("/internal/customer/items/{id}")
    CustomerItemDTO get(@RequestHeader("X-Internal-Service-Secret") String serviceSecret,
            @PathVariable("id") Long id);
}
