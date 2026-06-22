CREATE TABLE IF NOT EXISTS tenant.tenant_field_configuration (
  tenant_id VARCHAR(160) NOT NULL,
  surface VARCHAR(80) NOT NULL,
  field_id VARCHAR(160) NOT NULL,
  configuration_id VARCHAR(512) NOT NULL,
  origin VARCHAR(80) NOT NULL,
  system_field_ref VARCHAR(512) NOT NULL DEFAULT '',
  name_alias VARCHAR(512) NOT NULL DEFAULT '',
  description_alias TEXT NOT NULL DEFAULT '',
  enabled BOOLEAN NOT NULL,
  omitted BOOLEAN NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  audit_ref VARCHAR(512) NOT NULL DEFAULT '',
  PRIMARY KEY (tenant_id, surface, field_id)
);

CREATE TABLE IF NOT EXISTS tenant.tenant_pipeline_product (
  tenant_id VARCHAR(160) NOT NULL,
  product_id VARCHAR(160) NOT NULL,
  display_name VARCHAR(512) NOT NULL DEFAULT '',
  enabled BOOLEAN NOT NULL,
  PRIMARY KEY (tenant_id, product_id)
);

CREATE TABLE IF NOT EXISTS tenant.tenant_pipeline_product_field_ref (
  tenant_id VARCHAR(160) NOT NULL,
  product_id VARCHAR(160) NOT NULL,
  surface VARCHAR(80) NOT NULL,
  field_id VARCHAR(160) NOT NULL,
  PRIMARY KEY (tenant_id, product_id, surface, field_id)
);

CREATE TABLE IF NOT EXISTS tenant.tenant_pipeline_investor (
  tenant_id VARCHAR(160) NOT NULL,
  investor_id VARCHAR(160) NOT NULL,
  display_name VARCHAR(512) NOT NULL DEFAULT '',
  enabled BOOLEAN NOT NULL,
  PRIMARY KEY (tenant_id, investor_id)
);

CREATE TABLE IF NOT EXISTS tenant.tenant_pipeline_investor_field_ref (
  tenant_id VARCHAR(160) NOT NULL,
  investor_id VARCHAR(160) NOT NULL,
  surface VARCHAR(80) NOT NULL,
  field_id VARCHAR(160) NOT NULL,
  PRIMARY KEY (tenant_id, investor_id, surface, field_id)
);

CREATE TABLE IF NOT EXISTS tenant.tenant_pipeline_company_setting (
  tenant_id VARCHAR(160) NOT NULL,
  setting_key VARCHAR(256) NOT NULL,
  setting_value TEXT NOT NULL DEFAULT '',
  PRIMARY KEY (tenant_id, setting_key)
);

CREATE TABLE IF NOT EXISTS tenant.tenant_pipeline_user_assignment (
  user_id VARCHAR(160) PRIMARY KEY,
  tenant_id VARCHAR(160) NOT NULL
);

CREATE TABLE IF NOT EXISTS tenant.tenant_pipeline_user_setting (
  tenant_id VARCHAR(160) NOT NULL,
  user_id VARCHAR(160) NOT NULL,
  setting_key VARCHAR(256) NOT NULL,
  setting_value TEXT NOT NULL DEFAULT '',
  PRIMARY KEY (tenant_id, user_id, setting_key)
);

CREATE TABLE IF NOT EXISTS tenant.tenant_pipeline_access_audit (
  audit_id BIGSERIAL PRIMARY KEY,
  tenant_id VARCHAR(160) NOT NULL,
  user_id VARCHAR(160) NOT NULL DEFAULT '',
  actor_id VARCHAR(160) NOT NULL DEFAULT '',
  actor_type VARCHAR(80) NOT NULL DEFAULT '',
  code VARCHAR(160) NOT NULL,
  entity_type VARCHAR(80) NOT NULL DEFAULT '',
  entity_id VARCHAR(160) NOT NULL DEFAULT '',
  recorded_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_tenant_pipeline_access_audit_tenant ON tenant.tenant_pipeline_access_audit(tenant_id, audit_id);

CREATE TABLE IF NOT EXISTS tenant.audit_log_record (
  audit_id UUID PRIMARY KEY,
  tenant_id VARCHAR(160) NOT NULL,
  occurred_at TIMESTAMPTZ NOT NULL,
  actor_id VARCHAR(160) NOT NULL,
  actor_type VARCHAR(80) NOT NULL,
  action VARCHAR(160) NOT NULL,
  entity_type VARCHAR(160) NOT NULL,
  entity_id VARCHAR(160) NOT NULL,
  entity_version VARCHAR(160) NOT NULL,
  outcome VARCHAR(80) NOT NULL,
  correlation_id VARCHAR(160) NOT NULL,
  causation_id VARCHAR(160) NOT NULL,
  event_id VARCHAR(160) NOT NULL,
  idempotency_key VARCHAR(160) NOT NULL,
  before_ref TEXT NOT NULL,
  after_ref TEXT NOT NULL,
  change_summary_json TEXT NOT NULL,
  data_classification VARCHAR(80) NOT NULL,
  record_hash VARCHAR(160) NOT NULL,
  previous_hash VARCHAR(160) NOT NULL,
  UNIQUE (tenant_id, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_audit_log_record_tenant_occurred ON tenant.audit_log_record(tenant_id, occurred_at, audit_id);

CREATE TABLE IF NOT EXISTS tenant.outbox_event (
  event_id UUID PRIMARY KEY,
  tenant_id VARCHAR(160) NOT NULL,
  aggregate_type VARCHAR(160) NOT NULL,
  aggregate_id VARCHAR(160) NOT NULL,
  topic VARCHAR(256) NOT NULL,
  partition_key VARCHAR(256) NOT NULL,
  schema_ref VARCHAR(256) NOT NULL,
  event_name VARCHAR(256) NOT NULL,
  event_version INTEGER NOT NULL,
  envelope_json TEXT NOT NULL,
  payload_hash VARCHAR(160) NOT NULL,
  status VARCHAR(80) NOT NULL,
  attempt_count INTEGER NOT NULL,
  next_attempt_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  published_at TIMESTAMPTZ,
  actor_id VARCHAR(160) NOT NULL,
  correlation_id VARCHAR(160) NOT NULL,
  causation_id VARCHAR(160) NOT NULL,
  idempotency_key VARCHAR(160) NOT NULL,
  error_code VARCHAR(160) NOT NULL DEFAULT '',
  error_message TEXT NOT NULL DEFAULT '',
  publisher_ref VARCHAR(256) NOT NULL DEFAULT '',
  UNIQUE (tenant_id, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_outbox_event_tenant_status_created ON tenant.outbox_event(tenant_id, status, created_at);

CREATE TABLE IF NOT EXISTS tenant.consumer_inbox_record (
  inbox_id UUID PRIMARY KEY,
  tenant_id VARCHAR(160) NOT NULL,
  consumer_name VARCHAR(160) NOT NULL,
  event_id UUID NOT NULL,
  event_name VARCHAR(256) NOT NULL,
  schema_ref VARCHAR(256) NOT NULL,
  schema_version INTEGER NOT NULL,
  payload_hash VARCHAR(160) NOT NULL,
  status VARCHAR(80) NOT NULL,
  attempt_count INTEGER NOT NULL,
  first_seen_at TIMESTAMPTZ NOT NULL,
  last_attempt_at TIMESTAMPTZ NOT NULL,
  processed_at TIMESTAMPTZ,
  result_payload_hash VARCHAR(160) NOT NULL DEFAULT '',
  correlation_id VARCHAR(160) NOT NULL,
  causation_id VARCHAR(160) NOT NULL,
  last_error_code VARCHAR(160) NOT NULL DEFAULT '',
  last_error_message TEXT NOT NULL DEFAULT '',
  UNIQUE (tenant_id, consumer_name, event_id)
);

CREATE INDEX IF NOT EXISTS idx_consumer_inbox_record_tenant_seen ON tenant.consumer_inbox_record(tenant_id, first_seen_at);
