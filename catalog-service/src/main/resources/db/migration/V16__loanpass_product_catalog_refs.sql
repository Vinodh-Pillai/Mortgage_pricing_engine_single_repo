CREATE SCHEMA IF NOT EXISTS catalog;

CREATE TABLE IF NOT EXISTS catalog.loanpass_product_catalog_ref (
  tenant_id UUID NOT NULL,
  product_code VARCHAR(64) NOT NULL,
  loanpass_product_ref VARCHAR(160) NOT NULL,
  external_product_type VARCHAR(80),
  external_investor_ref VARCHAR(160),
  source_system VARCHAR(64) NOT NULL,
  source_provenance VARCHAR(240) NOT NULL,
  source_payload_ref VARCHAR(240),
  synthetic_dev_only BOOLEAN NOT NULL DEFAULT FALSE,
  metadata JSONB NOT NULL DEFAULT '{}'::JSONB,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  effective_start TIMESTAMPTZ,
  effective_end TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (tenant_id, product_code, loanpass_product_ref),
  CONSTRAINT loanpass_product_catalog_ref_source_ck CHECK (source_system IN ('LOANPASS_PUBLIC', 'LOANHOUSE_PUBLIC', 'SYNTHETIC_DEV', 'INTERNAL_CONFIG')),
  CONSTRAINT loanpass_product_catalog_ref_window_ck CHECK (effective_end IS NULL OR effective_start IS NULL OR effective_end > effective_start),
  CONSTRAINT loanpass_product_catalog_ref_synth_ck CHECK (synthetic_dev_only = TRUE OR source_system <> 'SYNTHETIC_DEV')
);

CREATE TABLE IF NOT EXISTS catalog.loanpass_product_availability_ref (
  availability_ref VARCHAR(160) NOT NULL,
  tenant_id UUID NOT NULL,
  product_code VARCHAR(64) NOT NULL,
  investor_code VARCHAR(64),
  channel_code VARCHAR(64),
  state_code VARCHAR(16),
  availability_status VARCHAR(32) NOT NULL DEFAULT 'CONFIGURED',
  pricing_profile_ref VARCHAR(160),
  eligibility_rule_ref VARCHAR(160),
  stipulation_rule_ref VARCHAR(160),
  lock_term_set_ref VARCHAR(160),
  source_system VARCHAR(64) NOT NULL,
  source_provenance VARCHAR(240) NOT NULL,
  source_payload_ref VARCHAR(240),
  synthetic_dev_only BOOLEAN NOT NULL DEFAULT FALSE,
  metadata JSONB NOT NULL DEFAULT '{}'::JSONB,
  effective_start TIMESTAMPTZ,
  effective_end TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (availability_ref),
  CONSTRAINT loanpass_product_availability_ref_status_ck CHECK (availability_status IN ('CONFIGURED', 'ACTIVE', 'INACTIVE', 'RETIRED')),
  CONSTRAINT loanpass_product_availability_ref_source_ck CHECK (source_system IN ('LOANPASS_PUBLIC', 'LOANHOUSE_PUBLIC', 'SYNTHETIC_DEV', 'INTERNAL_CONFIG')),
  CONSTRAINT loanpass_product_availability_ref_window_ck CHECK (effective_end IS NULL OR effective_start IS NULL OR effective_end > effective_start),
  CONSTRAINT loanpass_product_availability_ref_synth_ck CHECK (synthetic_dev_only = TRUE OR source_system <> 'SYNTHETIC_DEV')
);

CREATE INDEX IF NOT EXISTS idx_loanpass_product_catalog_ref_lookup
  ON catalog.loanpass_product_catalog_ref (tenant_id, loanpass_product_ref, active);

CREATE INDEX IF NOT EXISTS idx_loanpass_product_catalog_ref_metadata
  ON catalog.loanpass_product_catalog_ref USING GIN (metadata);

CREATE INDEX IF NOT EXISTS idx_loanpass_product_availability_ref_lookup
  ON catalog.loanpass_product_availability_ref (tenant_id, product_code, investor_code, channel_code, availability_status);

CREATE UNIQUE INDEX IF NOT EXISTS idx_loanpass_product_availability_ref_natural
  ON catalog.loanpass_product_availability_ref (tenant_id, product_code, COALESCE(investor_code, ''), COALESCE(channel_code, ''), COALESCE(state_code, ''));
