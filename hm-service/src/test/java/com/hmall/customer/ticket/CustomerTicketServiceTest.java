package com.hmall.customer.ticket;

import com.hmall.customer.chat.CustomerActor;
import com.hmall.customer.chat.CustomerConversationService;
import com.hmall.customer.query.CustomerOrderQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class CustomerTicketServiceTest {
    @Autowired CustomerConversationService conversations;
    @Autowired CustomerTicketService tickets;
    @MockBean CustomerOrderQueryService orders;

    @Test
    void duplicate_request_returns_same_ticket() {
        Map<String, Object> created = conversations.create(new CustomerActor(1L, null));
        String id = (String) created.get("conversationId");
        CustomerActor actor = new CustomerActor(1L, null);
        CustomerTicket first = tickets.create(id, actor, "需要人工协助", null, "same-key");
        CustomerTicket retry = tickets.create(id, actor, "需要人工协助", null, "same-key");
        assertEquals(first.getTicketId(), retry.getTicketId());
        assertEquals("QUEUED", retry.getStatus());
    }

    @Test
    void foreign_order_is_rejected_without_disclosing_owner() {
        String id = (String) conversations.create(new CustomerActor(1L, null)).get("conversationId");
        when(orders.getOwnedOrder(1L, 77L)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在"));
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> tickets.create(id, new CustomerActor(1L, null), "需要人工", 77L, "one"));
        assertEquals(404, error.getRawStatusCode());
    }

    @Test
    void guest_cannot_attach_order() {
        Map<String, Object> created = conversations.create(new CustomerActor(null, null));
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> tickets.create((String) created.get("conversationId"),
                        new CustomerActor(null, (String) created.get("guestKey")), "需要人工", 7L, "one"));
        assertEquals(400, error.getRawStatusCode());
    }

    @Test
    void summary_redacts_phone_address_and_token() {
        String id = (String) conversations.create(new CustomerActor(1L, null)).get("conversationId");
        conversations.submit(id, new CustomerActor(1L, null),
                "我的收货地址是北京市海淀区知春路1号，配送没有更新，手机13800138000", "context-key");
        CustomerTicket ticket = tickets.create(id, new CustomerActor(1L, null),
                "地址：北京市海淀区某街道，手机 13800138000，token=secret123，请帮我咨询配送", null, "one");
        assertFalse(ticket.getSummary().contains("13800138000"));
        assertFalse(ticket.getSummary().contains("北京市海淀区"));
        assertFalse(ticket.getSummary().contains("secret123"));
        assertTrue(ticket.getSummary().contains("配送"));
        assertTrue(ticket.getSummary().contains("近期对话"));
        assertFalse(ticket.getSummary().contains("知春路"));
    }
}
