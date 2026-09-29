package com.hmall.customer.query;

import com.hmall.api.dto.customer.CustomerItemDTO;
import com.hmall.api.dto.customer.CustomerLogisticsDTO;
import com.hmall.api.dto.customer.CustomerOrderDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/internal/customer")
@RequiredArgsConstructor
public class CustomerQueryController {
    private final CustomerDelegationTokenService tokens;
    private final CustomerItemQueryService items;
    private final CustomerOrderQueryService orders;

    @GetMapping("/items")
    public List<CustomerItemDTO> search(@RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String key, @RequestParam(required = false) String brand,
            @RequestParam(required = false) String category, @RequestParam(required = false) Integer minPrice,
            @RequestParam(required = false) Integer maxPrice, @RequestParam(required = false) Integer pageSize) {
        tokens.verify(authorization, "catalog:read");
        return items.search(key, brand, category, minPrice, maxPrice, pageSize);
    }

    @GetMapping("/items/{id}")
    public CustomerItemDTO item(@RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id) {
        tokens.verify(authorization, "catalog:read");
        return items.get(id);
    }

    @GetMapping("/orders")
    public List<CustomerOrderDTO> orders(@RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) Integer pageSize) {
        Long userId = tokens.verify(authorization, "order:read");
        return orders.listOwnedOrders(userId, pageSize == null ? 10 : pageSize);
    }

    @GetMapping("/orders/{id}")
    public CustomerOrderDTO order(@RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id) {
        return orders.getOwnedOrder(tokens.verify(authorization, "order:read"), id);
    }

    @GetMapping("/orders/{id}/logistics")
    public CustomerLogisticsDTO logistics(@RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id) {
        return orders.getOwnedLogistics(tokens.verify(authorization, "order:read"), id);
    }
}
