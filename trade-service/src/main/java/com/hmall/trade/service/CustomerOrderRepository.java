package com.hmall.trade.service;

import com.hmall.trade.domain.po.Order;
import com.hmall.trade.domain.po.OrderDetail;
import com.hmall.trade.domain.po.OrderLogistics;

import java.util.List;

public interface CustomerOrderRepository {
    List<Order> listOwned(Long userId, int limit);

    Order findOwned(Long userId, Long orderId);

    List<OrderDetail> listDetails(Long orderId);

    OrderLogistics findLogistics(Long orderId);
}
