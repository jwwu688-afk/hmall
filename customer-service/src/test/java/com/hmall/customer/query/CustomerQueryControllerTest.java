package com.hmall.customer.query;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmall.api.client.CustomerItemClient;
import com.hmall.api.client.CustomerTradeClient;
import com.hmall.api.client.UserIdentityClient;
import com.hmall.api.dto.customer.CustomerItemDTO;
import com.hmall.api.dto.customer.CustomerOrderDTO;
import com.hmall.api.dto.customer.UserIdentityDTO;
import com.hmall.customer.chat.CustomerActor;
import com.hmall.customer.chat.CustomerIdentityService;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CustomerQueryControllerTest {
    @Test
    void catalogAdapterUsesInternalCredentialAndReturnsBoundedContract() {
        CustomerItemClient client = mock(CustomerItemClient.class);
        CustomerItemDTO item = new CustomerItemDTO();
        item.setId(1L);
        item.setName("在售商品");
        when(client.search(eq("internal-secret"), eq("手机"), any(), any(), any(), any(), eq(20)))
                .thenReturn(List.of(item));

        CustomerItemQueryService service = new CustomerItemQueryService(client, "internal-secret");
        assertEquals("在售商品", service.search("手机", null, null, null, null, 50).get(0).getName());
    }

    @Test
    void orderAdapterForwardsVerifiedUserAndExposesNoContactFields() throws Exception {
        CustomerTradeClient client = mock(CustomerTradeClient.class);
        CustomerOrderDTO order = new CustomerOrderDTO();
        order.setId(7L);
        order.setStatus(2);
        order.setTotalFee(1299);
        when(client.getOrder("internal-secret", 1L, 7L)).thenReturn(order);

        CustomerOrderDTO result = new CustomerOrderQueryService(client, "internal-secret").getOwnedOrder(1L, 7L);
        String json = new ObjectMapper().writeValueAsString(result);
        assertEquals(7L, result.getId());
        assertFalse(json.contains("mobile"));
        assertFalse(json.contains("street"));
        assertFalse(json.contains("contact"));
    }

    @Test
    void identityAdapterUsesUserServiceWhileGuestsRemainAnonymous() {
        UserIdentityClient client = mock(UserIdentityClient.class);
        UserIdentityDTO identity = new UserIdentityDTO();
        identity.setUserId(42L);
        when(client.resolve("internal-secret", "Bearer user-token")).thenReturn(identity);
        CustomerIdentityService service = new CustomerIdentityService(client, "internal-secret");

        CustomerActor loggedIn = service.actor("Bearer user-token", null);
        CustomerActor guest = service.actor(null, "guest-key");
        assertEquals(42L, loggedIn.getUserId());
        assertEquals("guest-key", guest.getGuestKey());
    }

    @Test
    void anonymousOrderLookupIsUnauthorized() {
        CustomerDelegationTokenService tokens =
                new CustomerDelegationTokenService("local-test-secret-with-adequate-length");
        String token = tokens.issue(null, Set.of("catalog:read"), "conversation-1", "run-1");
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> tokens.verify("Bearer " + token, "order:read"));
        assertEquals(401, error.getRawStatusCode());
    }
}
