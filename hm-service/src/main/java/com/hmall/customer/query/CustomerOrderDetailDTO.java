package com.hmall.customer.query;

import lombok.Data;

@Data
public class CustomerOrderDetailDTO {
    private Long itemId;
    private String name;
    private String spec;
    private Integer price;
    private Integer num;
    private String image;
}
