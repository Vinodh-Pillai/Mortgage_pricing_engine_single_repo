CREATE SCHEMA IF NOT EXISTS lock_service;

CREATE TABLE IF NOT EXISTS lock_service.loanpass_lock_term_option (
  term_ref VARCHAR(160) PRIMARY KEY,
  tenant_id VARCHAR(64) NOT NULL,
  product_code VARCHAR(64),
  investor_code VARCHAR(64),
  channel_code VARCHAR(64),
  lock_term_days INT NOT NULL,
  adjustment_ref VARCHAR(160),
  float_down_rule_ref VARCHAR(160),
  lock_term_payload JSONB NOT NULL DEFAULT '{}'::JSONB,
  source_system VARCHAR(64) NOT NULL,
  source_provenance VARCHAR(240) NOT NULL,
  source_payload_ref VARCHAR(240),
  synthetic_dev_only BOOLEAN NOT NULL DEFAULT FALSE,
  status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
  effective_start TIMESTAMPTZ,
  effective_end TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT loanpass_lock_term_option_days_ck CHECK (lock_term_days > 0),
  CONSTRAINT loanpass_lock_term_option_source_ck CHECK (source_system IN ('LOANPASS_PUBLIC', 'LOANHOUSE_PUBLIC', 'SYNTHETIC_DEV', 'INTERNAL_CONFIG')),
  CONSTRAINT loanpass_lock_term_option_status_ck CHECK (status IN ('DRAFT', 'ACTIVE', 'INACTIVE', 'RETIRED')),
  CONSTRAINT loanpass_lock_term_option_synth_ck CHECK (synthetic_dev_only = TRUE OR source_system <> 'SYNTHETIC_DEV'),
  CONSTRAINT loanpass_lock_term_option_window_ck CHECK (effective_end IS NULL OR effective_start IS NULL OR effective_end > effective_start)
);

CREATE INDEX IF NOT EXISTS idx_loanpass_lock_term_option_lookup
  ON lock_service.loanpass_lock_term_option (tenant_id, product_code, investor_code, channel_code, status, lock_term_days);

CREATE INDEX IF NOT EXISTS idx_loanpass_lock_term_option_payload
  ON lock_service.loanpass_lock_term_option USING GIN (lock_term_payload);
