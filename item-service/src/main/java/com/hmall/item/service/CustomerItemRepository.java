package com.hmall.item.service;

import com.hmall.item.domain.po.Item;

import java.util.List;

public interface CustomerItemRepository {
    List<Item> search(String key, String brand, String category, Integer minPrice, Integer maxPrice, int limit);

    Item findAvailableById(Long id);
}
