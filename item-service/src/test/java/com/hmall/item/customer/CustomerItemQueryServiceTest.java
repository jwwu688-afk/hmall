package com.hmall.item.customer;

import com.hmall.common.security.InternalServiceGuard;
import com.hmall.item.controller.InternalCustomerItemController;
import com.hmall.item.domain.po.Item;
import com.hmall.item.service.CustomerItemRepository;
import com.hmall.item.service.CustomerItemQueryService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
class CustomerItemQueryServiceTest {
    private List<Item> rows = List.of();
    private final CustomerItemRepository repository = new CustomerItemRepository() {
        @Override
        public List<Item> search(String key, String brand, String category, Integer minPrice, Integer maxPrice,
                int limit) {
            return rows;
        }

        @Override
        public Item findAvailableById(Long id) {
            return rows.stream()
                    .filter(item -> item.getId().equals(id) && item.getStatus() == 1)
                    .findFirst()
                    .orElse(null);
        }
    };
    private final CustomerItemQueryService queries = new CustomerItemQueryService(repository);

    @Test
    void searchHidesOffShelfItemsAndCapsResultsAtTwenty() {
        rows = new ArrayList<>();
        rows.add(item(999L, 2));
        rows.addAll(IntStream.rangeClosed(1, 25)
                .mapToObj(id -> item((long) id, 1))
                .collect(Collectors.toList()));
        assertThat(queries.search(null, null, null, null, null, 100))
                .hasSize(20)
                .allMatch(item -> item.getId() != 999L);
    }

    @Test
    void getReturnsOnlyAvailableItem() {
        rows = List.of(item(1L, 2));
        assertThat(queries.get(1L)).isNull();

        rows = List.of(item(1L, 1));
        assertThat(queries.get(1L)).isNotNull();
    }

    @Test
    void controllerRejectsMissingInternalSecret() {
        InternalCustomerItemController controller = new InternalCustomerItemController(
                new InternalServiceGuard("secret"), queries);
        assertThatThrownBy(() -> controller.get(null, 1L))
                .isInstanceOf(com.hmall.common.exception.ForbiddenException.class);
    }

    private static Item item(Long id, int status) {
        return new Item().setId(id).setName("item-" + id).setPrice(100).setStock(5).setStatus(status);
    }
}
