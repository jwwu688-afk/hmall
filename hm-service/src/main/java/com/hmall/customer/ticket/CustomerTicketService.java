package com.hmall.customer.ticket;

import com.hmall.customer.chat.CustomerActor;
import com.hmall.customer.chat.CustomerConversationService;
import com.hmall.customer.query.CustomerOrderQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerTicketService {
    private final JdbcTemplate db;
    private final CustomerConversationService conversations;
    private final CustomerOrderQueryService orders;

    @Transactional
    public CustomerTicket create(String conversationId, CustomerActor actor,
                                 String reason, Long orderId, String idempotencyKey) {
        conversations.requireOwner(conversationId, actor);
        return createValidated(conversationId, actor, reason, orderId, idempotencyKey);
    }

    @Transactional
    public CustomerTicket createInternal(String conversationId, Long userId,
                                         String reason, Long orderId, String idempotencyKey) {
        List<Map<String, Object>> rows = db.queryForList(
                "SELECT user_id FROM customer_conversation WHERE id=?", conversationId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在");
        Object owner = rows.get(0).get("user_id");
        if (owner == null ? userId != null : userId == null || ((Number) owner).longValue() != userId)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在");
        return createValidated(conversationId, new CustomerActor(userId, null), reason, orderId, idempotencyKey);
    }

    private CustomerTicket createValidated(String conversationId, CustomerActor actor,
                                           String reason, Long orderId, String idempotencyKey) {
        if (reason == null || reason.isBlank() || reason.length() > 500
                || idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 80)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "工单内容或幂等键无效");
        List<Map<String, Object>> previous = db.queryForList(
                "SELECT id,status,order_id,summary,created_at FROM customer_ticket WHERE conversation_id=? AND idempotency_key=?",
                conversationId, idempotencyKey);
        if (!previous.isEmpty()) return toTicket(conversationId, previous.get(0));
        if (orderId != null) {
            if (!actor.authenticated()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "游客不能关联订单");
            orders.getOwnedOrder(actor.getUserId(), orderId);
        }
        String id = UUID.randomUUID().toString();
        String sanitized = sanitize(reason);
        String summary = "用户请求人工处理：" + sanitized;
        try {
            db.update("INSERT INTO customer_ticket(id,conversation_id,user_id,order_id,status,reason,summary,idempotency_key) " +
                            "VALUES(?,?,?,?,'QUEUED',?,?,?)",
                    id, conversationId, actor.getUserId(), orderId, sanitized, summary, idempotencyKey);
        } catch (DuplicateKeyException race) {
            List<Map<String, Object>> winner = db.queryForList(
                    "SELECT id,status,order_id,summary,created_at FROM customer_ticket WHERE conversation_id=? AND idempotency_key=?",
                    conversationId, idempotencyKey);
            if (!winner.isEmpty()) return toTicket(conversationId, winner.get(0));
            throw race;
        }
        return getById(id, conversationId);
    }

    public CustomerTicket latest(String conversationId, CustomerActor actor) {
        conversations.requireOwner(conversationId, actor);
        List<Map<String, Object>> rows = db.queryForList(
                "SELECT id,status,order_id,summary,created_at FROM customer_ticket WHERE conversation_id=? " +
                        "ORDER BY created_at DESC,id DESC LIMIT 1", conversationId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "暂无人工工单");
        return toTicket(conversationId, rows.get(0));
    }

    private CustomerTicket getById(String id, String conversationId) {
        return toTicket(conversationId, db.queryForMap(
                "SELECT id,status,order_id,summary,created_at FROM customer_ticket WHERE id=?", id));
    }

    private CustomerTicket toTicket(String conversationId, Map<String, Object> row) {
        CustomerTicket ticket = new CustomerTicket();
        ticket.setTicketId((String) row.get("id"));
        ticket.setConversationId(conversationId);
        ticket.setStatus((String) row.get("status"));
        Object orderId = row.get("order_id");
        if (orderId != null) ticket.setOrderId(((Number) orderId).longValue());
        ticket.setSummary((String) row.get("summary"));
        Object created = row.get("created_at");
        ticket.setCreatedAt(created instanceof java.time.LocalDateTime
                ? (java.time.LocalDateTime) created
                : ((java.sql.Timestamp) created).toLocalDateTime());
        return ticket;
    }

    static String sanitize(String value) {
        return value.replaceAll("地址\\s*[:：]\\s*[^，,。]*", "地址[已省略]")
                .replaceAll("(?i)(token|password|密码)\\s*[:=：]\\s*[^\\s，,。]+", "$1=[已省略]")
                .replaceAll("(?<!\\d)1[3-9]\\d{9}(?!\\d)", "[手机号已省略]")
                .replaceAll("(?<!\\d)\\d{6,}(?!\\d)", "[数字已省略]");
    }
}
