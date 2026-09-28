package com.hmall.customer.query;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmall.domain.po.Order;
import com.hmall.domain.po.OrderDetail;
import com.hmall.domain.po.OrderLogistics;
import com.hmall.mapper.OrderDetailMapper;
import com.hmall.mapper.OrderLogisticsMapper;
import com.hmall.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomerOrderQueryService {
    private final OrderMapper orders;
    private final OrderDetailMapper details;
    private final OrderLogisticsMapper logistics;

    private Order owned(Long userId, Long orderId) {
        if (userId == null || orderId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在");
        }
        Order order = orders.selectById(orderId);
        if (order == null || !userId.equals(order.getUserId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在");
        }
        return order;
    }

    public CustomerOrderDTO getOwnedOrder(Long userId, Long orderId) {
        Order order = owned(userId, orderId);
        CustomerOrderDTO dto = new CustomerOrderDTO();
        dto.setId(order.getId());
        dto.setStatus(order.getStatus());
        dto.setTotalFee(order.getTotalFee());
        dto.setCreateTime(order.getCreateTime());
        dto.setPayTime(order.getPayTime());
        dto.setConsignTime(order.getConsignTime());
        List<OrderDetail> rows = details.selectList(new LambdaQueryWrapper<OrderDetail>()
                .eq(OrderDetail::getOrderId, orderId));
        dto.setDetails(rows.stream().map(row -> {
            CustomerOrderDetailDTO detail = new CustomerOrderDetailDTO();
            detail.setItemId(row.getItemId());
            detail.setName(row.getName());
            detail.setSpec(row.getSpec());
            detail.setPrice(row.getPrice());
            detail.setNum(row.getNum());
            detail.setImage(row.getImage());
            return detail;
        }).collect(Collectors.toList()));
        return dto;
    }

    public List<CustomerOrderDTO> listOwnedOrders(Long userId, int limit) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "需要登录");
        }
        List<Order> rows = orders.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, userId).orderByDesc(Order::getCreateTime).last("LIMIT " + Math.min(20, Math.max(1, limit))));
        return rows.stream().map(order -> getOwnedOrder(userId, order.getId())).collect(Collectors.toList());
    }

    public CustomerLogisticsDTO getOwnedLogistics(Long userId, Long orderId) {
        Order order = owned(userId, orderId);
        OrderLogistics row = logistics.selectById(orderId);
        CustomerLogisticsDTO dto = new CustomerLogisticsDTO();
        dto.setOrderId(orderId);
        dto.setOrderStatus(order.getStatus());
        dto.setConsignTime(order.getConsignTime());
        if (row != null) {
            dto.setLogisticsCompany(row.getLogisticsCompany());
            dto.setLogisticsNumber(row.getLogisticsNumber());
        }
        return dto;
    }
}
