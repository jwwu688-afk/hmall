package com.hmall.customer.query;

import lombok.Data;

@Data
public class CustomerItemDTO {
    private Long id;
    private String name;
    private Integer price;
    private Integer stock;
    private String image;
    private String category;
    private String brand;
    private String spec;
}
