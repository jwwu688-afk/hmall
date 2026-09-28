package com.hmall.customer.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class CustomerConversationControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void guest_history_requires_secret_key() throws Exception {
        String body = mvc.perform(post("/customer-service/conversations"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode created = json.readTree(body);
        String url = "/customer-service/conversations/" + created.get("conversationId").asText();
        mvc.perform(get(url)).andExpect(status().isNotFound());
        mvc.perform(get(url).header("X-Guest-Key", created.get("guestKey").asText()))
                .andExpect(status().isOk());
    }

    @Test
    void callback_without_service_secret_is_rejected() throws Exception {
        mvc.perform(post("/internal/customer-service/runs/unknown/events")
                        .contentType("application/json")
                        .content("{\"runId\":\"unknown\",\"sequence\":1,\"type\":\"completed\",\"data\":\"x\"}"))
                .andExpect(status().isUnauthorized());
    }
}
