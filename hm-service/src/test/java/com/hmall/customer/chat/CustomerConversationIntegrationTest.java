package com.hmall.customer.chat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class CustomerConversationIntegrationTest {
    @Autowired CustomerConversationService conversations;

    @Test
    void guest_ownership_idempotency_and_event_sequence_survive_database_reads() {
        Map<String, Object> created = conversations.create(new CustomerActor(null, null));
        String id = (String) created.get("conversationId");
        CustomerActor owner = new CustomerActor(null, (String) created.get("guestKey"));
        ResponseStatusException forbidden = assertThrows(ResponseStatusException.class,
                () -> conversations.history(id, new CustomerActor(null, "other-key")));
        assertEquals(HttpStatus.NOT_FOUND.value(), forbidden.getRawStatusCode());

        Map<String, Object> first = conversations.submit(id, owner, "查询商品", "browser-retry-key");
        Map<String, Object> retry = conversations.submit(id, owner, "查询商品", "browser-retry-key");
        assertEquals(first.get("messageId"), retry.get("messageId"));
        assertEquals(first.get("runId"), retry.get("runId"));
        assertEquals(false, retry.get("newRun"));

        String runId = (String) first.get("runId");
        long eventId = conversations.appendEvent(runId, 1, "completed", "答复");
        assertEquals(eventId, conversations.appendEvent(runId, 1, "completed", "答复"));
        assertEquals(1, conversations.events(id, owner, 0).size());
        assertTrue(conversations.events(id, owner, eventId).isEmpty());
        assertEquals(2, ((java.util.List<?>) conversations.history(id, owner).get("messages")).size());
    }

    @Test
    void sources_and_ticket_events_are_persisted_before_completion() {
        String id = (String) conversations.create(new CustomerActor(1L, null)).get("conversationId");
        CustomerActor actor = new CustomerActor(1L, null);
        String runId = (String) conversations.submit(id, actor, "退货规则", "event-key").get("runId");
        conversations.appendEvent(runId, 1, "sources", "[{\"policyId\":5}]");
        conversations.appendEvent(runId, 2, "ticket", "{\"ticketId\":\"ticket-1\",\"status\":\"QUEUED\"}");
        conversations.appendEvent(runId, 3, "completed", "已创建排队工单");
        assertEquals(3, conversations.events(id, actor, 0).size());
        assertEquals(3, ((java.util.List<?>) conversations.history(id, actor).get("events")).size());
    }
}
