CREATE TABLE IF NOT EXISTS customer_policy (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  policy_key VARCHAR(64) NOT NULL,
  version INT NOT NULL,
  status VARCHAR(16) NOT NULL,
  effective_from DATETIME(6) NOT NULL,
  title VARCHAR(200) NOT NULL,
  category VARCHAR(24) NOT NULL,
  excerpt TEXT NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UNIQUE KEY uk_customer_policy_version (policy_key, version),
  INDEX idx_customer_policy_effective (status, effective_from, category)
);
