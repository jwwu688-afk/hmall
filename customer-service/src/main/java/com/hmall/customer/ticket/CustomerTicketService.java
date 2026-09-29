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
        List<String> recent = db.queryForList(
                "SELECT content FROM customer_message WHERE conversation_id=? AND role='user' " +
                        "ORDER BY created_at DESC,id DESC LIMIT 3", String.class, conversationId);
        StringBuilder summary = new StringBuilder("用户请求人工处理；近期对话主题：");
        String context = String.join(" ", recent) + " " + reason;
        List<String> topics = new java.util.ArrayList<>();
        if (context.matches("(?s).*(配送|物流|发货|快递|运费).*")) topics.add("配送物流");
        if (context.matches("(?s).*(订单|下单|购买).*")) topics.add("订单查询");
        if (context.matches("(?s).*(退货|退款|换货|售后).*")) topics.add("售后咨询");
        if (context.matches("(?s).*(商品|价格|库存|规格).*")) topics.add("商品咨询");
        if (context.matches("(?s).*(支付|付款).*")) topics.add("支付咨询");
        summary.append(topics.isEmpty() ? "一般咨询" : String.join("、", topics));
        if (orderId != null) summary.append("；关联订单 ").append(orderId);
        try {
            db.update("INSERT INTO customer_ticket(id,conversation_id,user_id,order_id,status,reason,summary,idempotency_key) " +
                            "VALUES(?,?,?,?,'QUEUED',?,?,?)",
                    id, conversationId, actor.getUserId(), orderId, summary.toString(),
                    summary.toString(), idempotencyKey);
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

}
