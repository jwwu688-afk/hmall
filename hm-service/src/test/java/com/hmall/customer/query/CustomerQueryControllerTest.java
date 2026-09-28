package com.hmall.customer.query;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmall.common.utils.UserContext;
import com.hmall.controller.OrderController;
import com.hmall.domain.po.Item;
import com.hmall.domain.po.Order;
import com.hmall.domain.po.OrderDetail;
import com.hmall.mapper.ItemMapper;
import com.hmall.mapper.OrderDetailMapper;
import com.hmall.mapper.OrderLogisticsMapper;
import com.hmall.mapper.OrderMapper;
import com.hmall.service.IOrderService;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CustomerQueryControllerTest {
    private final OrderMapper orders = mock(OrderMapper.class);
    private final OrderDetailMapper details = mock(OrderDetailMapper.class);
    private final OrderLogisticsMapper logistics = mock(OrderLogisticsMapper.class);
    private final ItemMapper items = mock(ItemMapper.class);

    @Test
    void public_order_route_rejects_foreign_order() {
        IOrderService service = mock(IOrderService.class);
        when(service.getById(77L)).thenReturn(new Order().setId(77L).setUserId(2L));
        UserContext.setUser(1L);
        try {
            ResponseStatusException error = assertThrows(ResponseStatusException.class,
                    () -> new OrderController(service).queryOrderById(77L));
            assertEquals(404, error.getRawStatusCode());
        } finally {
            UserContext.removeUser();
        }
    }

    @Test
    void foreign_order_and_missing_order_return_same_404() {
        CustomerOrderQueryService service = new CustomerOrderQueryService(orders, details, logistics);
        when(orders.selectById(77L)).thenReturn(new Order().setId(77L).setUserId(2L));
        ResponseStatusException foreign = assertThrows(ResponseStatusException.class,
                () -> service.getOwnedOrder(1L, 77L));
        when(orders.selectById(77L)).thenReturn(null);
        ResponseStatusException missing = assertThrows(ResponseStatusException.class,
                () -> service.getOwnedOrder(1L, 77L));
        assertEquals(404, foreign.getRawStatusCode());
        assertEquals(foreign.getRawStatusCode(), missing.getRawStatusCode());
        assertEquals(foreign.getReason(), missing.getReason());
    }

    @Test
    void anonymous_order_lookup_is_401() {
        CustomerDelegationTokenService tokens =
                new CustomerDelegationTokenService("local-test-secret-with-adequate-length");
        String token = tokens.issue(null, Set.of("catalog:read"), "conversation-1", "run-1");
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> tokens.verify("Bearer " + token, "order:read"));
        assertEquals(401, error.getRawStatusCode());
    }

    @Test
    void owned_order_contains_details_but_no_address_or_phone() throws Exception {
        CustomerOrderQueryService service = new CustomerOrderQueryService(orders, details, logistics);
        when(orders.selectById(7L)).thenReturn(new Order().setId(7L).setUserId(1L).setStatus(2).setTotalFee(1299));
        when(details.selectList(any())).thenReturn(List.of(
                new OrderDetail().setOrderId(7L).setItemId(3L).setName("测试商品").setNum(1).setPrice(1299)));
        CustomerOrderDTO result = service.getOwnedOrder(1L, 7L);
        String json = new ObjectMapper().writeValueAsString(result);
        assertTrue(json.contains("测试商品"));
        assertTrue(json.contains("1299"));
        assertFalse(json.contains("mobile"));
        assertFalse(json.contains("street"));
        assertFalse(json.contains("contact"));
    }

    @Test
    void logistics_without_row_returns_known_order_with_no_tracking() {
        CustomerOrderQueryService service = new CustomerOrderQueryService(orders, details, logistics);
        when(orders.selectById(7L)).thenReturn(new Order().setId(7L).setUserId(1L).setStatus(2));
        when(logistics.selectById(7L)).thenReturn(null);
        CustomerLogisticsDTO result = service.getOwnedLogistics(1L, 7L);
        assertEquals(2, result.getOrderStatus());
        assertNull(result.getLogisticsCompany());
        assertNull(result.getLogisticsNumber());
    }

    @Test
    void catalog_excludes_status_2_and_3() {
        CustomerItemQueryService service = new CustomerItemQueryService(items);
        when(items.selectList(any())).thenReturn(List.of(
                new Item().setId(1L).setStatus(1).setName("在售商品"),
                new Item().setId(2L).setStatus(2).setName("下架商品"),
                new Item().setId(3L).setStatus(3).setName("删除商品")));
        List<CustomerItemDTO> result = service.search(null, null, null, null, null, 50);
        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getId());
    }
}
