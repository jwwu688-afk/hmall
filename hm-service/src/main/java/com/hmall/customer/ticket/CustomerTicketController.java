package com.hmall.customer.ticket;

import com.hmall.customer.chat.CustomerActor;
import com.hmall.customer.query.CustomerDelegationTokenService;
import com.hmall.utils.JwtTool;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class CustomerTicketController {
    private final CustomerTicketService tickets;
    private final CustomerDelegationTokenService delegationTokens;
    private final JwtTool jwtTool;

    @Value("${hm.agent.service-secret}")
    private String serviceSecret;

    private CustomerActor actor(String authorization, String guestKey) {
        if (authorization != null && !authorization.isBlank())
            return new CustomerActor(jwtTool.parseToken(authorization), null);
        return new CustomerActor(null, guestKey);
    }

    @PostMapping("/customer-service/conversations/{id}/handoff")
    public CustomerTicket handoff(@PathVariable String id,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Guest-Key", required = false) String guestKey,
            @RequestBody Map<String, Object> request) {
        return tickets.create(id, actor(authorization, guestKey),
                string(request, "reason"), longOrNull(request.get("orderId")), string(request, "idempotencyKey"));
    }

    @GetMapping("/customer-service/conversations/{id}/ticket")
    public CustomerTicket latest(@PathVariable String id,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Guest-Key", required = false) String guestKey) {
        return tickets.latest(id, actor(authorization, guestKey));
    }

    @PostMapping("/internal/customer/tickets")
    public CustomerTicket internal(@RequestHeader(value = "X-Agent-Service-Secret", required = false) String supplied,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody Map<String, Object> request) {
        if (supplied == null || !MessageDigest.isEqual(serviceSecret.getBytes(StandardCharsets.UTF_8),
                supplied.getBytes(StandardCharsets.UTF_8)))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "内部服务认证失败");
        Map<String, Object> claims = delegationTokens.verifyClaims(authorization, "ticket:create");
        String conversationId = string(request, "conversationId");
        String idempotencyKey = string(request, "idempotencyKey");
        if (!conversationId.equals(claims.get("conversation_id")) || !idempotencyKey.equals(claims.get("run_id")))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "令牌与会话不匹配");
        Object subject = claims.get("sub");
        Long userId = subject == null ? null : Long.valueOf(subject.toString());
        return tickets.createInternal(conversationId, userId, string(request, "reason"),
                longOrNull(request.get("orderId")), idempotencyKey);
    }

    private static String string(Map<String, Object> request, String key) {
        Object value = request.get(key);
        return value == null ? null : value.toString();
    }

    private static Long longOrNull(Object value) {
        if (value == null) return null;
        try { return Long.valueOf(value.toString()); }
        catch (NumberFormatException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "订单 ID 无效"); }
    }
}
