CREATE TABLE IF NOT EXISTS loanpass_ratesheet_import_batch (
  batch_ref VARCHAR(160) PRIMARY KEY,
  tenant_id VARCHAR(64) NOT NULL,
  source_system VARCHAR(64) NOT NULL,
  source_provenance VARCHAR(240) NOT NULL,
  source_payload_ref VARCHAR(240),
  source_payload_hash VARCHAR(160),
  synthetic_dev_only BOOLEAN NOT NULL DEFAULT FALSE,
  imported_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  metadata JSONB NOT NULL DEFAULT '{}'::JSONB,
  CONSTRAINT loanpass_ratesheet_import_batch_source_ck CHECK (source_system IN ('LOANPASS_PUBLIC', 'LOANHOUSE_PUBLIC', 'SYNTHETIC_DEV', 'INTERNAL_CONFIG')),
  CONSTRAINT loanpass_ratesheet_import_batch_synth_ck CHECK (synthetic_dev_only = TRUE OR source_system <> 'SYNTHETIC_DEV')
);

CREATE TABLE IF NOT EXISTS loanpass_ratesheet_row (
  row_ref VARCHAR(160) PRIMARY KEY,
  batch_ref VARCHAR(160) NOT NULL REFERENCES loanpass_ratesheet_import_batch(batch_ref),
  tenant_id VARCHAR(64) NOT NULL,
  product_code VARCHAR(64) NOT NULL,
  investor_code VARCHAR(64),
  channel_code VARCHAR(64),
  lock_term_ref VARCHAR(160),
  row_payload JSONB NOT NULL DEFAULT '{}'::JSONB,
  rate_value NUMERIC(9,5),
  price_value NUMERIC(11,5),
  margin_ref VARCHAR(160),
  source_system VARCHAR(64) NOT NULL,
  source_provenance VARCHAR(240) NOT NULL,
  synthetic_dev_only BOOLEAN NOT NULL DEFAULT FALSE,
  effective_start TIMESTAMPTZ,
  effective_end TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT loanpass_ratesheet_row_source_ck CHECK (source_system IN ('LOANPASS_PUBLIC', 'LOANHOUSE_PUBLIC', 'SYNTHETIC_DEV', 'INTERNAL_CONFIG')),
  CONSTRAINT loanpass_ratesheet_row_synth_ck CHECK (synthetic_dev_only = TRUE OR source_system <> 'SYNTHETIC_DEV'),
  CONSTRAINT loanpass_ratesheet_row_window_ck CHECK (effective_end IS NULL OR effective_start IS NULL OR effective_end > effective_start)
);

CREATE TABLE IF NOT EXISTS loanpass_rate_output_ref (
  output_ref VARCHAR(160) PRIMARY KEY,
  tenant_id VARCHAR(64) NOT NULL,
  quote_ref VARCHAR(160),
  row_ref VARCHAR(160) REFERENCES loanpass_ratesheet_row(row_ref),
  product_code VARCHAR(64) NOT NULL,
  investor_code VARCHAR(64),
  channel_code VARCHAR(64),
  lock_term_ref VARCHAR(160),
  output_payload JSONB NOT NULL DEFAULT '{}'::JSONB,
  source_system VARCHAR(64) NOT NULL,
  source_provenance VARCHAR(240) NOT NULL,
  synthetic_dev_only BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT loanpass_rate_output_ref_source_ck CHECK (source_system IN ('LOANPASS_PUBLIC', 'LOANHOUSE_PUBLIC', 'SYNTHETIC_DEV', 'INTERNAL_CONFIG')),
  CONSTRAINT loanpass_rate_output_ref_synth_ck CHECK (synthetic_dev_only = TRUE OR source_system <> 'SYNTHETIC_DEV')
);

CREATE INDEX IF NOT EXISTS idx_loanpass_ratesheet_row_lookup
  ON loanpass_ratesheet_row (tenant_id, product_code, investor_code, channel_code, lock_term_ref, effective_start);

CREATE INDEX IF NOT EXISTS idx_loanpass_ratesheet_row_payload
  ON loanpass_ratesheet_row USING GIN (row_payload);

CREATE INDEX IF NOT EXISTS idx_loanpass_rate_output_ref_quote
  ON loanpass_rate_output_ref (tenant_id, quote_ref, product_code);
