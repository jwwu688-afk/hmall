package com.hmall.customer.ticket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmall.customer.chat.CustomerActor;
import com.hmall.customer.chat.CustomerConversationService;
import com.hmall.customer.query.CustomerDelegationTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CustomerTicketControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired CustomerConversationService conversations;
    @Autowired CustomerDelegationTokenService tokens;
    @Value("${hm.agent.service-secret}") String serviceSecret;

    @Test
    void internal_ticket_requires_service_secret_and_matching_conversation() throws Exception {
        String id = (String) conversations.create(new CustomerActor(1L, null)).get("conversationId");
        String token = tokens.issue(1L, Set.of("ticket:create"), id, "run-1");
        String body = "{\"conversationId\":\"" + id + "\",\"reason\":\"需要人工\",\"idempotencyKey\":\"run-1\"}";
        mvc.perform(post("/internal/customer/tickets").header("Authorization", "Bearer " + token)
                        .contentType("application/json").content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/internal/customer/tickets").header("Authorization", "Bearer " + token)
                        .header("X-Agent-Service-Secret", serviceSecret).contentType("application/json")
                        .content(body.replace(id, "another-conversation")))
                .andExpect(status().isForbidden());

        String first = mvc.perform(post("/internal/customer/tickets").header("Authorization", "Bearer " + token)
                        .header("X-Agent-Service-Secret", serviceSecret).contentType("application/json").content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String retry = mvc.perform(post("/internal/customer/tickets").header("Authorization", "Bearer " + token)
                        .header("X-Agent-Service-Secret", serviceSecret).contentType("application/json").content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode a = json.readTree(first);
        JsonNode b = json.readTree(retry);
        assertEquals("QUEUED", a.get("status").asText());
        assertEquals(a.get("ticketId").asText(), b.get("ticketId").asText());
    }
}
