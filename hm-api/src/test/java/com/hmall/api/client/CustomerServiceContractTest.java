package com.hmall.api.client;

import com.hmall.api.dto.customer.CustomerItemDTO;
import com.hmall.api.dto.customer.CustomerLogisticsDTO;
import com.hmall.api.dto.customer.CustomerOrderDetailDTO;
import com.hmall.api.dto.customer.CustomerOrderDTO;
import com.hmall.api.dto.customer.UserIdentityDTO;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerServiceContractTest {
    @Test
    void exposesOnlyDedicatedInternalCustomerAndIdentityPaths() throws Exception {
        assertFeignClient(CustomerItemClient.class, "item-service", "customerItemClient");
        assertGetPath(CustomerItemClient.class.getMethod("search", String.class, String.class, String.class,
                String.class, Integer.class, Integer.class, Integer.class), "/internal/customer/items");
        assertGetPath(CustomerItemClient.class.getMethod("get", String.class, Long.class),
                "/internal/customer/items/{id}");

        assertFeignClient(CustomerTradeClient.class, "trade-service", "customerTradeClient");
        assertGetPath(CustomerTradeClient.class.getMethod("listOrders", String.class, Long.class, Integer.class),
                "/internal/customer/orders");
        assertGetPath(CustomerTradeClient.class.getMethod("getOrder", String.class, Long.class, Long.class),
                "/internal/customer/orders/{id}");
        assertGetPath(CustomerTradeClient.class.getMethod("getLogistics", String.class, Long.class, Long.class),
                "/internal/customer/orders/{id}/logistics");

        assertFeignClient(UserIdentityClient.class, "user-service", "userIdentityClient");
        assertGetPath(UserIdentityClient.class.getMethod("resolve", String.class, String.class),
                "/internal/auth/identity");
    }

    @Test
    void customerDtosExcludeContactAddressAndPaymentData() throws Exception {
        assertFields(new CustomerItemDTO(), "id", "name", "price", "stock", "image", "category", "brand", "spec");
        assertFields(new CustomerOrderDTO(), "id", "status", "totalFee", "createTime", "payTime", "consignTime", "details");
        assertFields(new CustomerOrderDetailDTO(), "itemId", "name", "spec", "price", "num", "image");
        assertFields(new CustomerLogisticsDTO(), "orderId", "orderStatus", "logisticsCompany", "logisticsNumber", "consignTime");
        assertFields(new UserIdentityDTO(), "userId");
    }

    private void assertFields(Object value, String... expected) throws IntrospectionException {
        Set<String> properties = Arrays.stream(Introspector.getBeanInfo(value.getClass(), Object.class)
                        .getPropertyDescriptors())
                .map(descriptor -> descriptor.getName())
                .collect(Collectors.toSet());
        assertThat(properties).containsExactlyInAnyOrder(expected);
        assertThat(Set.of(expected)).doesNotContain("address", "phone", "paymentPassword", "username");
    }

    private static void assertFeignClient(Class<?> type, String name, String contextId) {
        FeignClient annotation = type.getAnnotation(FeignClient.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.name()).isEqualTo(name);
        assertThat(annotation.contextId()).isEqualTo(contextId);
    }

    private static void assertGetPath(Method method, String path) {
        assertThat(method.getAnnotation(GetMapping.class).value()).containsExactly(path);
    }
}
