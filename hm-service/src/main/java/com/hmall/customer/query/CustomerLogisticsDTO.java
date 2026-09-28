package com.hmall.customer.query;

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
