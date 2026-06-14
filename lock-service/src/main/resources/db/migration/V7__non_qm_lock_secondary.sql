CREATE SCHEMA IF NOT EXISTS lock_service;

CREATE TABLE IF NOT EXISTS lock_service.non_qm_lock_policy (
  policy_code VARCHAR(80) PRIMARY KEY,
  investor_code VARCHAR(64) NOT NULL,
  channel_code VARCHAR(64) NOT NULL,
  non_qm_type VARCHAR(32) NOT NULL,
  version INT NOT NULL,
  policy_document JSONB NOT NULL,
  status VARCHAR(16) NOT NULL,
  effective_start TIMESTAMPTZ NOT NULL,
  effective_end TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS lock_service.non_qm_delivery_profile (
  profile_code VARCHAR(80) PRIMARY KEY,
  investor_code VARCHAR(64) NOT NULL,
  delivery_type VARCHAR(32) NOT NULL,
  profile_document JSONB NOT NULL,
  status VARCHAR(16) NOT NULL
);

ALTER TABLE IF EXISTS lock_service.lock ADD COLUMN IF NOT EXISTS non_qm_lock_context JSONB;
ALTER TABLE IF EXISTS lock_service.lock ADD COLUMN IF NOT EXISTS secondary_delivery_profile_code VARCHAR(80);

CREATE INDEX IF NOT EXISTS idx_non_qm_lock_policy_resolution
  ON lock_service.non_qm_lock_policy (investor_code, channel_code, non_qm_type, status, effective_start);

CREATE INDEX IF NOT EXISTS idx_non_qm_delivery_profile_investor
  ON lock_service.non_qm_delivery_profile (investor_code, status);
