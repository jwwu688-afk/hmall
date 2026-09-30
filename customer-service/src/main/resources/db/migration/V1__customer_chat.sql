CREATE TABLE IF NOT EXISTS customer_conversation (
  id VARCHAR(36) PRIMARY KEY,
  user_id BIGINT NULL,
  guest_hash CHAR(64) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  INDEX idx_customer_conversation_user (user_id, created_at)
);

CREATE TABLE IF NOT EXISTS customer_message (
  id VARCHAR(36) PRIMARY KEY,
  conversation_id VARCHAR(36) NOT NULL,
  role VARCHAR(16) NOT NULL,
  content TEXT NOT NULL,
  idempotency_key VARCHAR(80) NULL,
  run_id VARCHAR(36) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UNIQUE KEY uk_customer_message_idempotency (conversation_id, idempotency_key),
  INDEX idx_customer_message_history (conversation_id, created_at)
);

CREATE TABLE IF NOT EXISTS customer_run (
  id VARCHAR(36) PRIMARY KEY,
  conversation_id VARCHAR(36) NOT NULL,
  status VARCHAR(20) NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);

CREATE TABLE IF NOT EXISTS customer_event (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  run_id VARCHAR(36) NOT NULL,
  sequence_no INT NOT NULL,
  event_type VARCHAR(24) NOT NULL,
  data TEXT NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UNIQUE KEY uk_customer_event_sequence (run_id, sequence_no),
  INDEX idx_customer_event_run (run_id, id)
);
