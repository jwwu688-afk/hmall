package com.hmall.customer.query;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Service
public class CustomerDelegationTokenService {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final byte[] secret;

    public CustomerDelegationTokenService(@Value("${hm.agent.token-secret}") String secret) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException("HM_AGENT_TOKEN_SECRET 至少需要 32 个字符");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String issue(Long userId, Set<String> scopes, String conversationId, String runId) {
        try {
            Map<String, Object> claims = new LinkedHashMap<>();
            claims.put("aud", "hmall-internal");
            claims.put("sub", userId == null ? null : userId.toString());
            claims.put("scope", scopes);
            claims.put("conversation_id", conversationId);
            claims.put("run_id", runId);
            claims.put("exp", Instant.now().getEpochSecond() + 600);
            String header = encode("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
            String payload = encode(JSON.writeValueAsBytes(claims));
            String signed = header + "." + payload;
            return signed + "." + encode(sign(signed));
        } catch (Exception e) {
            throw new IllegalStateException("无法签发内部令牌", e);
        }
    }

    public Long verify(String authorization, String requiredScope) {
        try {
            if (authorization == null || !authorization.startsWith("Bearer ")) throw new IllegalArgumentException();
            String[] parts = authorization.substring(7).split("\\.", -1);
            if (parts.length != 3) throw new IllegalArgumentException();
            byte[] actual = Base64.getUrlDecoder().decode(parts[2]);
            if (!MessageDigest.isEqual(sign(parts[0] + "." + parts[1]), actual)) throw new IllegalArgumentException();
            Map<String, Object> header = JSON.readValue(Base64.getUrlDecoder().decode(parts[0]), new TypeReference<Map<String, Object>>() {});
            Map<String, Object> claims = JSON.readValue(Base64.getUrlDecoder().decode(parts[1]), new TypeReference<Map<String, Object>>() {});
            if (!"HS256".equals(header.get("alg")) || !"hmall-internal".equals(claims.get("aud"))) throw new IllegalArgumentException();
            if (!claims.containsKey("exp") || ((Number) claims.get("exp")).longValue() <= Instant.now().getEpochSecond()) throw new IllegalArgumentException();
            Object scopes = claims.get("scope");
            if (!(scopes instanceof java.util.List) || !((java.util.List<?>) scopes).contains(requiredScope)) throw new IllegalArgumentException();
            Object subject = claims.get("sub");
            if ("order:read".equals(requiredScope) && subject == null) throw new IllegalArgumentException();
            return subject == null ? null : Long.valueOf(subject.toString());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "内部令牌无效或权限不足");
        }
    }

    private byte[] sign(String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
