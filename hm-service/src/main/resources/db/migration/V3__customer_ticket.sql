CREATE TABLE IF NOT EXISTS customer_ticket (
  id VARCHAR(36) PRIMARY KEY,
  conversation_id VARCHAR(36) NOT NULL,
  user_id BIGINT NULL,
  order_id BIGINT NULL,
  status VARCHAR(20) NOT NULL,
  reason VARCHAR(500) NOT NULL,
  summary VARCHAR(1000) NOT NULL,
  idempotency_key VARCHAR(80) NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UNIQUE KEY uk_customer_ticket_idempotency (conversation_id, idempotency_key),
  INDEX idx_customer_ticket_conversation (conversation_id, created_at)
);
