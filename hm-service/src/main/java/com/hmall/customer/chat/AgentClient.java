package com.hmall.customer.chat;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class AgentClient {
    private final CustomerConversationService conversations;
    private final RestTemplate rest = new RestTemplate();

    @Value("${hm.agent.url:http://127.0.0.1:8001}")
    private String agentUrl;
    @Value("${hm.agent.service-secret}")
    private String serviceSecret;

    public void dispatch(String conversationId, String runId, String message, String token) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Agent-Service-Secret", serviceSecret);
            rest.postForEntity(agentUrl + "/internal/runs", new HttpEntity<>(Map.of(
                    "conversationId", conversationId, "runId", runId,
                    "message", message, "delegationToken", token), headers), Void.class);
        } catch (Exception e) {
            conversations.failRun(runId);
        }
    }
}
