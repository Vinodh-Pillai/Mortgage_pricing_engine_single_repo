CREATE SCHEMA IF NOT EXISTS eligibility;

CREATE TABLE IF NOT EXISTS eligibility.loanpass_stipulation_template (
  template_ref VARCHAR(160) PRIMARY KEY,
  tenant_id VARCHAR(64) NOT NULL,
  product_code VARCHAR(64),
  stipulation_code VARCHAR(120) NOT NULL,
  display_name VARCHAR(240) NOT NULL,
  template_payload JSONB NOT NULL DEFAULT '{}'::JSONB,
  source_system VARCHAR(64) NOT NULL,
  source_provenance VARCHAR(240) NOT NULL,
  source_payload_ref VARCHAR(240),
  synthetic_dev_only BOOLEAN NOT NULL DEFAULT FALSE,
  status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
  effective_start TIMESTAMPTZ,
  effective_end TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT loanpass_stipulation_template_source_ck CHECK (source_system IN ('LOANPASS_PUBLIC', 'LOANHOUSE_PUBLIC', 'SYNTHETIC_DEV', 'INTERNAL_CONFIG')),
  CONSTRAINT loanpass_stipulation_template_status_ck CHECK (status IN ('DRAFT', 'ACTIVE', 'INACTIVE', 'RETIRED')),
  CONSTRAINT loanpass_stipulation_template_synth_ck CHECK (synthetic_dev_only = TRUE OR source_system <> 'SYNTHETIC_DEV'),
  CONSTRAINT loanpass_stipulation_template_window_ck CHECK (effective_end IS NULL OR effective_start IS NULL OR effective_end > effective_start)
);

CREATE TABLE IF NOT EXISTS eligibility.loanpass_stipulation_rule (
  rule_ref VARCHAR(160) PRIMARY KEY,
  tenant_id VARCHAR(64) NOT NULL,
  template_ref VARCHAR(160) NOT NULL REFERENCES eligibility.loanpass_stipulation_template(template_ref),
  product_code VARCHAR(64),
  investor_code VARCHAR(64),
  channel_code VARCHAR(64),
  rule_payload JSONB NOT NULL DEFAULT '{}'::JSONB,
  reason_code_ref VARCHAR(160),
  source_system VARCHAR(64) NOT NULL,
  source_provenance VARCHAR(240) NOT NULL,
  source_payload_ref VARCHAR(240),
  synthetic_dev_only BOOLEAN NOT NULL DEFAULT FALSE,
  status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
  effective_start TIMESTAMPTZ,
  effective_end TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT loanpass_stipulation_rule_source_ck CHECK (source_system IN ('LOANPASS_PUBLIC', 'LOANHOUSE_PUBLIC', 'SYNTHETIC_DEV', 'INTERNAL_CONFIG')),
  CONSTRAINT loanpass_stipulation_rule_status_ck CHECK (status IN ('DRAFT', 'ACTIVE', 'INACTIVE', 'RETIRED')),
  CONSTRAINT loanpass_stipulation_rule_synth_ck CHECK (synthetic_dev_only = TRUE OR source_system <> 'SYNTHETIC_DEV'),
  CONSTRAINT loanpass_stipulation_rule_window_ck CHECK (effective_end IS NULL OR effective_start IS NULL OR effective_end > effective_start)
);

CREATE INDEX IF NOT EXISTS idx_loanpass_stipulation_template_lookup
  ON eligibility.loanpass_stipulation_template (tenant_id, product_code, stipulation_code, status);

CREATE INDEX IF NOT EXISTS idx_loanpass_stipulation_rule_resolution
  ON eligibility.loanpass_stipulation_rule (tenant_id, product_code, investor_code, channel_code, status, effective_start);

CREATE INDEX IF NOT EXISTS idx_loanpass_stipulation_rule_payload
  ON eligibility.loanpass_stipulation_rule USING GIN (rule_payload);
