package com.hmall.customer.chat;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerConversationService {
    private final JdbcTemplate db;
    private static final SecureRandom RANDOM = new SecureRandom();

    public static String hashGuestKey(String key) {
        if (key == null || key.isBlank()) return null;
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : hash) out.append(String.format("%02x", b & 0xff));
            return out.toString();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    public static boolean owns(CustomerActor actor, Long owner, String guestHash) {
        if (actor.authenticated()) return actor.getUserId().equals(owner);
        String candidate = hashGuestKey(actor.getGuestKey());
        return owner == null && guestHash != null && candidate != null &&
                MessageDigest.isEqual(guestHash.getBytes(StandardCharsets.US_ASCII), candidate.getBytes(StandardCharsets.US_ASCII));
    }

    public static void requireNextSequence(int last, int incoming) {
        if (incoming != last + 1) throw new IllegalArgumentException("事件序号不连续");
    }

    @Transactional
    public Map<String, Object> create(CustomerActor actor) {
        String id = UUID.randomUUID().toString();
        String guestKey = null;
        if (!actor.authenticated()) {
            byte[] bytes = new byte[32];
            RANDOM.nextBytes(bytes);
            guestKey = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        }
        db.update("INSERT INTO customer_conversation(id,user_id,guest_hash) VALUES(?,?,?)",
                id, actor.getUserId(), hashGuestKey(guestKey));
        Map<String, Object> result = new HashMap<>();
        result.put("conversationId", id);
        if (guestKey != null) result.put("guestKey", guestKey);
        return result;
    }

    public void requireOwner(String conversationId, CustomerActor actor) {
        List<Map<String, Object>> rows = db.queryForList(
                "SELECT user_id,guest_hash FROM customer_conversation WHERE id=?", conversationId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在");
        Map<String, Object> row = rows.get(0);
        Object owner = row.get("user_id");
        if (!owns(actor, owner == null ? null : ((Number) owner).longValue(), (String) row.get("guest_hash")))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在");
    }

    @Transactional
    public Map<String, Object> submit(String conversationId, CustomerActor actor, String message, String idempotencyKey) {
        requireOwner(conversationId, actor);
        if (message == null || message.isBlank() || message.length() > 2000
                || idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 80)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "消息或幂等键无效");
        List<Map<String, Object>> existing = db.queryForList(
                "SELECT id,run_id FROM customer_message WHERE conversation_id=? AND idempotency_key=?",
                conversationId, idempotencyKey);
        if (!existing.isEmpty()) return submission(existing.get(0), false);
        String messageId = UUID.randomUUID().toString();
        String runId = UUID.randomUUID().toString();
        try {
            db.update("INSERT INTO customer_message(id,conversation_id,role,content,idempotency_key,run_id) VALUES(?,?,'user',?,?,?)",
                    messageId, conversationId, message.trim(), idempotencyKey, runId);
        } catch (DuplicateKeyException race) {
            List<Map<String, Object>> winner = db.queryForList(
                    "SELECT id,run_id FROM customer_message WHERE conversation_id=? AND idempotency_key=?",
                    conversationId, idempotencyKey);
            if (!winner.isEmpty()) return submission(winner.get(0), false);
            throw race;
        }
        db.update("INSERT INTO customer_run(id,conversation_id,status) VALUES(?,?,'PENDING')", runId, conversationId);
        Map<String, Object> result = new HashMap<>();
        result.put("messageId", messageId);
        result.put("runId", runId);
        result.put("newRun", true);
        return result;
    }

    private Map<String, Object> submission(Map<String, Object> row, boolean newRun) {
        Map<String, Object> result = new HashMap<>();
        result.put("messageId", row.get("id"));
        result.put("runId", row.get("run_id"));
        result.put("newRun", newRun);
        return result;
    }

    @Transactional
    public long appendEvent(String runId, int sequence, String type, String data) {
        List<Map<String, Object>> run = db.queryForList("SELECT status FROM customer_run WHERE id=? FOR UPDATE", runId);
        if (run.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "运行不存在");
        // 同一会话的并行运行必须按提交顺序分配全局事件 ID，避免 SSE 游标跳过迟交事件。
        String conversationId = db.queryForObject(
                "SELECT conversation_id FROM customer_run WHERE id=?", String.class, runId);
        db.queryForObject("SELECT id FROM customer_conversation WHERE id=? FOR UPDATE", String.class, conversationId);
        List<Map<String, Object>> duplicate = db.queryForList(
                "SELECT id FROM customer_event WHERE run_id=? AND sequence_no=?", runId, sequence);
        if (!duplicate.isEmpty()) return ((Number) duplicate.get(0).get("id")).longValue();
        if (!"PENDING".equals(run.get(0).get("status")))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "运行已结束");
        Integer last = db.queryForObject("SELECT COALESCE(MAX(sequence_no),0) FROM customer_event WHERE run_id=?",
                Integer.class, runId);
        try { requireNextSequence(last == null ? 0 : last, sequence); }
        catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage()); }
        if (!List.of("answer", "sources", "ticket", "completed", "error").contains(type) || data == null || data.length() > 20000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "事件无效");
        db.update("INSERT INTO customer_event(run_id,sequence_no,event_type,data) VALUES(?,?,?,?)",
                runId, sequence, type, data);
        Long id = db.queryForObject("SELECT id FROM customer_event WHERE run_id=? AND sequence_no=?",
                Long.class, runId, sequence);
        if ("completed".equals(type)) {
            db.update("INSERT INTO customer_message(id,conversation_id,role,content,run_id) VALUES(?,?,'assistant',?,?)",
                    UUID.randomUUID().toString(), conversationId, data, runId);
            db.update("UPDATE customer_run SET status='COMPLETED',updated_at=NOW(6) WHERE id=?", runId);
        } else if ("error".equals(type)) {
            db.update("UPDATE customer_run SET status='FAILED',updated_at=NOW(6) WHERE id=?", runId);
        }
        return id == null ? 0 : id;
    }

    public void failRun(String runId) {
        Integer next = db.queryForObject("SELECT COALESCE(MAX(sequence_no),0)+1 FROM customer_event WHERE run_id=?",
                Integer.class, runId);
        appendEvent(runId, next == null ? 1 : next, "error", "客服服务暂时不可用，请稍后重试");
    }

    public Map<String, Object> history(String conversationId, CustomerActor actor) {
        requireOwner(conversationId, actor);
        Map<String, Object> result = new HashMap<>();
        result.put("conversationId", conversationId);
        result.put("messages", db.queryForList(
                "SELECT id,role,content,run_id AS runId,created_at AS createdAt FROM customer_message WHERE conversation_id=? ORDER BY created_at DESC,id DESC LIMIT 200",
                conversationId));
        result.put("events", db.queryForList(
                "SELECT e.id,e.run_id AS runId,e.sequence_no AS sequence,e.event_type AS type,e.data " +
                        "FROM customer_event e JOIN customer_run r ON r.id=e.run_id WHERE r.conversation_id=? " +
                        "ORDER BY e.id DESC LIMIT 200", conversationId));
        return result;
    }

    public List<Map<String, Object>> events(String conversationId, CustomerActor actor, long after) {
        requireOwner(conversationId, actor);
        return db.queryForList("SELECT e.id,e.run_id AS runId,e.sequence_no AS sequence,e.event_type AS type,e.data " +
                        "FROM customer_event e JOIN customer_run r ON r.id=e.run_id WHERE r.conversation_id=? AND e.id>? ORDER BY e.id LIMIT 100",
                conversationId, Math.max(0, after));
    }
}
