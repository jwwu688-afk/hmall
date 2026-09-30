package com.hmall.item.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hmall.item.domain.po.Item;
import com.hmall.item.service.CustomerItemRepository;
import com.hmall.item.service.IItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class MybatisCustomerItemRepository implements CustomerItemRepository {
    private final IItemService items;

    @Override
    public List<Item> search(String key, String brand, String category, Integer minPrice, Integer maxPrice, int limit) {
        LambdaQueryWrapper<Item> query = Wrappers.lambdaQuery(Item.class)
                .eq(Item::getStatus, 1)
                .like(StringUtils.hasText(key), Item::getName, key)
                .eq(StringUtils.hasText(brand), Item::getBrand, brand)
                .eq(StringUtils.hasText(category), Item::getCategory, category)
                .ge(minPrice != null, Item::getPrice, minPrice)
                .le(maxPrice != null, Item::getPrice, maxPrice)
                .orderByDesc(Item::getUpdateTime)
                .last("LIMIT " + limit);
        return items.list(query);
    }

    @Override
    public Item findAvailableById(Long id) {
        return items.lambdaQuery()
                .eq(Item::getId, id)
                .eq(Item::getStatus, 1)
                .one();
    }
}
