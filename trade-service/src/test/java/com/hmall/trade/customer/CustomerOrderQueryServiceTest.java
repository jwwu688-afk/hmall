package com.hmall.trade.customer;

import com.hmall.common.security.InternalServiceGuard;
import com.hmall.trade.controller.InternalCustomerOrderController;
import com.hmall.trade.domain.po.Order;
import com.hmall.trade.domain.po.OrderDetail;
import com.hmall.trade.domain.po.OrderLogistics;
import com.hmall.trade.service.CustomerOrderQueryService;
import com.hmall.trade.service.CustomerOrderRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
class CustomerOrderQueryServiceTest {
    private Order order;
    private List<OrderDetail> detailRows = List.of();
    private OrderLogistics logisticsRow;
    private final CustomerOrderRepository repository = new CustomerOrderRepository() {
        @Override
        public List<Order> listOwned(Long userId, int limit) {
            return order == null ? List.of() : List.of(order);
        }

        @Override
        public Order findOwned(Long userId, Long orderId) {
            return order;
        }

        @Override
        public List<OrderDetail> listDetails(Long orderId) {
            return detailRows;
        }

        @Override
        public OrderLogistics findLogistics(Long orderId) {
            return logisticsRow;
        }
    };
    private final CustomerOrderQueryService queries = new CustomerOrderQueryService(repository);

    @Test
    void foreignOrderAndLogisticsAreIndistinguishableFromMissing() {
        order = new Order().setId(77L).setUserId(2L).setStatus(3);

        assertThat(queries.getOwnedOrder(1L, 77L)).isNull();
        assertThat(queries.getOwnedLogistics(1L, 77L)).isNull();
        assertThat(detailRows).isEmpty();
        assertThat(logisticsRow).isNull();
    }

    @Test
    void ownedOrderReturnsOnlySafeDetailsAndLogisticsFields() {
        order = new Order().setId(77L).setUserId(1L).setStatus(3).setTotalFee(200);
        detailRows = List.of(
                new OrderDetail().setOrderId(77L).setItemId(9L).setName("safe").setNum(2).setPrice(100));
        logisticsRow = new OrderLogistics().setOrderId(77L).setLogisticsCompany("SF").setLogisticsNumber("NO1")
                .setContact("private").setMobile("13800000000").setStreet("private");

        assertThat(queries.getOwnedOrder(1L, 77L).getDetails()).singleElement()
                .satisfies(detail -> assertThat(detail.getName()).isEqualTo("safe"));
        assertThat(queries.getOwnedLogistics(1L, 77L).getLogisticsNumber()).isEqualTo("NO1");
    }

    @Test
    void controllerRejectsMissingInternalSecret() {
        InternalCustomerOrderController controller = new InternalCustomerOrderController(
                new InternalServiceGuard("secret"), queries);
        assertThatThrownBy(() -> controller.getOrder(null, 1L, 77L))
                .isInstanceOf(com.hmall.common.exception.ForbiddenException.class);
    }
}
