package com.hmall.api.dto.customer;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CustomerLogisticsDTO {
    private Long orderId;
    private Integer orderStatus;
    private String logisticsCompany;
    private String logisticsNumber;
    private LocalDateTime consignTime;
}
