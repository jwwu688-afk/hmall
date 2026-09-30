package com.hmall.customer.query;

import com.hmall.api.client.CustomerTradeClient;
import com.hmall.api.dto.customer.CustomerLogisticsDTO;
import com.hmall.api.dto.customer.CustomerOrderDTO;
import feign.FeignException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class CustomerOrderQueryService {
    private final CustomerTradeClient orders;
    private final String internalSecret;

    public CustomerOrderQueryService(CustomerTradeClient orders,
            @Value("${hm.internal.service-secret}") String internalSecret) {
        this.orders = orders;
        this.internalSecret = internalSecret;
    }

    public CustomerOrderDTO getOwnedOrder(Long userId, Long orderId) {
        requireOrderIdentity(userId, orderId);
        try {
            CustomerOrderDTO result = orders.getOrder(internalSecret, userId, orderId);
            if (result == null) {
                throw notFound();
            }
            return result;
        } catch (FeignException e) {
            throw translate(e);
        }
    }

    public List<CustomerOrderDTO> listOwnedOrders(Long userId, int pageSize) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "需要登录");
        }
        int limit = Math.min(20, Math.max(1, pageSize));
        try {
            List<CustomerOrderDTO> result = orders.listOrders(internalSecret, userId, limit);
            return result == null ? List.of() : result;
        } catch (FeignException e) {
            throw translate(e);
        }
    }

    public CustomerLogisticsDTO getOwnedLogistics(Long userId, Long orderId) {
        requireOrderIdentity(userId, orderId);
        try {
            CustomerLogisticsDTO result = orders.getLogistics(internalSecret, userId, orderId);
            if (result == null) {
                throw notFound();
            }
            return result;
        } catch (FeignException e) {
            throw translate(e);
        }
    }

    private static void requireOrderIdentity(Long userId, Long orderId) {
        if (userId == null || orderId == null) {
            throw notFound();
        }
    }

    private static ResponseStatusException translate(FeignException error) {
        if (error.status() == 404) {
            return notFound();
        }
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "订单服务暂时不可用", error);
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在");
    }
}
