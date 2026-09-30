package com.hmall.customer.query;

import com.hmall.api.client.CustomerItemClient;
import com.hmall.api.dto.customer.CustomerItemDTO;
import feign.FeignException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class CustomerItemQueryService {
    private final CustomerItemClient items;
    private final String internalSecret;

    public CustomerItemQueryService(CustomerItemClient items,
            @Value("${hm.internal.service-secret}") String internalSecret) {
        this.items = items;
        this.internalSecret = internalSecret;
    }

    public List<CustomerItemDTO> search(String key, String brand, String category,
            Integer minPrice, Integer maxPrice, Integer pageSize) {
        if (minPrice != null && minPrice < 0 || maxPrice != null && maxPrice < 0
                || minPrice != null && maxPrice != null && minPrice > maxPrice) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "价格范围无效");
        }
        int limit = Math.min(20, Math.max(1, pageSize == null ? 10 : pageSize));
        try {
            List<CustomerItemDTO> result = items.search(internalSecret, trim(key), trim(brand), trim(category),
                    minPrice, maxPrice, limit);
            return result == null ? List.of() : result;
        } catch (FeignException e) {
            throw translate(e, "商品");
        }
    }

    public CustomerItemDTO get(Long id) {
        try {
            CustomerItemDTO result = items.get(internalSecret, id);
            if (result == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "商品不存在");
            }
            return result;
        } catch (FeignException e) {
            throw translate(e, "商品");
        }
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static ResponseStatusException translate(FeignException error, String resource) {
        if (error.status() == 404) {
            return new ResponseStatusException(HttpStatus.NOT_FOUND, resource + "不存在");
        }
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, resource + "服务暂时不可用", error);
    }
}
