package com.hmall.customer.chat;

import com.hmall.customer.query.CustomerDelegationTokenService;
import com.hmall.utils.JwtTool;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@RestController
@RequiredArgsConstructor
public class CustomerConversationController {
    private final CustomerConversationService conversations;
    private final CustomerDelegationTokenService delegationTokens;
    private final JwtTool jwtTool;
    private final AgentClient agentClient;

    @org.springframework.beans.factory.annotation.Value("${hm.agent.service-secret}")
    private String serviceSecret;

    private CustomerActor actor(String authorization, String guestKey) {
        if (authorization != null && !authorization.isBlank()) {
            return new CustomerActor(jwtTool.parseToken(authorization), null);
        }
        return new CustomerActor(null, guestKey);
    }

    @PostMapping("/customer-service/conversations")
    public Map<String, Object> create(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return conversations.create(actor(authorization, null));
    }

    @PostMapping("/customer-service/conversations/{id}/messages")
    public ResponseEntity<Map<String, Object>> message(
            @PathVariable String id,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Guest-Key", required = false) String guestKey,
            @RequestBody Map<String, String> request) {
        CustomerActor actor = actor(authorization, guestKey);
        String text = request.get("message");
        Map<String, Object> result = conversations.submit(id, actor, text, request.get("idempotencyKey"));
        if (Boolean.TRUE.equals(result.get("newRun"))) {
            Set<String> scopes = actor.authenticated() ? Set.of("catalog:read", "policy:read", "order:read") : Set.of("catalog:read", "policy:read");
            String runId = (String) result.get("runId");
            String token = delegationTokens.issue(actor.getUserId(), scopes, id, runId);
            agentClient.dispatch(id, runId, text, token);
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "messageId", result.get("messageId"), "runId", result.get("runId")));
    }

    @GetMapping("/customer-service/conversations/{id}")
    public Map<String, Object> history(@PathVariable String id,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Guest-Key", required = false) String guestKey) {
        return conversations.history(id, actor(authorization, guestKey));
    }

    @GetMapping(value = "/customer-service/conversations/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable String id, @RequestParam(defaultValue = "0") long after,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Guest-Key", required = false) String guestKey) {
        CustomerActor actor = actor(authorization, guestKey);
        conversations.requireOwner(id, actor);
        SseEmitter emitter = new SseEmitter(30_000L);
        CompletableFuture.runAsync(() -> {
            long cursor = Math.max(0, after);
            long deadline = System.currentTimeMillis() + 25_000;
            try {
                while (System.currentTimeMillis() < deadline) {
                    List<Map<String, Object>> rows = conversations.events(id, actor, cursor);
                    for (Map<String, Object> row : rows) {
                        cursor = ((Number) row.get("id")).longValue();
                        emitter.send(SseEmitter.event().id(Long.toString(cursor)).name("customer-event").data(row));
                    }
                    Thread.sleep(500);
                }
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    @PostMapping("/internal/customer-service/runs/{runId}/events")
    public Map<String, Long> callback(@PathVariable String runId,
            @RequestHeader(value = "X-Agent-Callback-Secret", required = false) String supplied,
            @RequestBody Map<String, Object> request) {
        if (supplied == null || !MessageDigest.isEqual(serviceSecret.getBytes(StandardCharsets.UTF_8),
                supplied.getBytes(StandardCharsets.UTF_8)))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "内部回调认证失败");
        if (!runId.equals(request.get("runId")))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "运行 ID 不一致");
        Object sequence = request.get("sequence");
        if (!(sequence instanceof Number)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "序号无效");
        long id = conversations.appendEvent(runId, ((Number) sequence).intValue(),
                String.valueOf(request.get("type")), String.valueOf(request.get("data")));
        return Map.of("eventId", id);
    }
}
