package com.hmall.trade.service.impl;

import com.hmall.trade.domain.po.Order;
import com.hmall.trade.domain.po.OrderDetail;
import com.hmall.trade.domain.po.OrderLogistics;
import com.hmall.trade.service.CustomerOrderRepository;
import com.hmall.trade.service.IOrderDetailService;
import com.hmall.trade.service.IOrderLogisticsService;
import com.hmall.trade.service.IOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class MybatisCustomerOrderRepository implements CustomerOrderRepository {
    private final IOrderService orders;
    private final IOrderDetailService details;
    private final IOrderLogisticsService logistics;

    @Override
    public List<Order> listOwned(Long userId, int limit) {
        return orders.lambdaQuery()
                .eq(Order::getUserId, userId)
                .orderByDesc(Order::getCreateTime)
                .last("LIMIT " + limit)
                .list();
    }

    @Override
    public Order findOwned(Long userId, Long orderId) {
        return orders.lambdaQuery()
                .eq(Order::getId, orderId)
                .eq(Order::getUserId, userId)
                .one();
    }

    @Override
    public List<OrderDetail> listDetails(Long orderId) {
        return details.lambdaQuery().eq(OrderDetail::getOrderId, orderId).list();
    }

    @Override
    public OrderLogistics findLogistics(Long orderId) {
        return logistics.getById(orderId);
    }
}
