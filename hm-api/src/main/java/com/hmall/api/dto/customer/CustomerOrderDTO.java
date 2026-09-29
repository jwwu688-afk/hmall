package com.hmall.api.dto.customer;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class CustomerOrderDTO {
    private Long id;
    private Integer status;
    private Integer totalFee;
    private LocalDateTime createTime;
    private LocalDateTime payTime;
    private LocalDateTime consignTime;
    private List<CustomerOrderDetailDTO> details;
}
