package com.hmall.customer.query;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmall.domain.po.Item;
import com.hmall.mapper.ItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomerItemQueryService {
    private final ItemMapper items;

    public List<CustomerItemDTO> search(String key, String brand, String category,
                                         Integer minPrice, Integer maxPrice, Integer pageSize) {
        if (minPrice != null && minPrice < 0 || maxPrice != null && maxPrice < 0
                || minPrice != null && maxPrice != null && minPrice > maxPrice) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "价格范围无效");
        }
        int limit = Math.min(20, Math.max(1, pageSize == null ? 10 : pageSize));
        QueryWrapper<Item> query = new QueryWrapper<Item>()
                .eq("status", 1).orderByDesc("sold").last("LIMIT " + limit);
        if (key != null && !key.isBlank()) query.like("name", key.trim());
        if (brand != null && !brand.isBlank()) query.eq("brand", brand.trim());
        if (category != null && !category.isBlank()) query.eq("category", category.trim());
        if (minPrice != null) query.ge("price", minPrice);
        if (maxPrice != null) query.le("price", maxPrice);
        return items.selectList(query).stream().filter(row -> Integer.valueOf(1).equals(row.getStatus()))
                .limit(limit).map(this::toDto).collect(Collectors.toList());
    }

    public CustomerItemDTO get(Long id) {
        Item row = items.selectById(id);
        if (row == null || !Integer.valueOf(1).equals(row.getStatus())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "商品不存在");
        }
        return toDto(row);
    }

    private CustomerItemDTO toDto(Item row) {
        CustomerItemDTO dto = new CustomerItemDTO();
        dto.setId(row.getId());
        dto.setName(row.getName());
        dto.setPrice(row.getPrice());
        dto.setStock(row.getStock());
        dto.setImage(row.getImage());
        dto.setCategory(row.getCategory());
        dto.setBrand(row.getBrand());
        dto.setSpec(row.getSpec());
        return dto;
    }
}
