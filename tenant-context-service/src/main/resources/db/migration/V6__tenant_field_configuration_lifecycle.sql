CREATE TABLE IF NOT EXISTS tenant.tenant_field_configuration_draft (
  tenant_id VARCHAR(160) NOT NULL,
  surface VARCHAR(80) NOT NULL,
  draft_id VARCHAR(512) NOT NULL,
  condition_field_refs_json TEXT NOT NULL DEFAULT '',
  saved_at TIMESTAMPTZ NOT NULL,
  user_id VARCHAR(160) NOT NULL,
  PRIMARY KEY (tenant_id, surface)
);

CREATE TABLE IF NOT EXISTS tenant.tenant_field_configuration_draft_field (
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

CREATE TABLE IF NOT EXISTS tenant.tenant_field_configuration_version (
  tenant_id VARCHAR(160) NOT NULL,
  surface VARCHAR(80) NOT NULL,
  version_number INTEGER NOT NULL,
  published_at TIMESTAMPTZ NOT NULL,
  user_id VARCHAR(160) NOT NULL,
  PRIMARY KEY (tenant_id, surface, version_number)
);

CREATE TABLE IF NOT EXISTS tenant.tenant_field_configuration_version_field (
  tenant_id VARCHAR(160) NOT NULL,
  surface VARCHAR(80) NOT NULL,
  version_number INTEGER NOT NULL,
  snapshot_type VARCHAR(32) NOT NULL,
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
  PRIMARY KEY (tenant_id, surface, version_number, snapshot_type, field_id),
  CONSTRAINT tenant_field_version_snapshot_check CHECK (snapshot_type IN ('PUBLISHED', 'PREVIOUS'))
);

CREATE TABLE IF NOT EXISTS tenant.tenant_field_configuration_audit (
  audit_id BIGSERIAL PRIMARY KEY,
  tenant_id VARCHAR(160) NOT NULL,
  user_id VARCHAR(160) NOT NULL,
  old_value TEXT NOT NULL,
  new_value TEXT NOT NULL,
  affected_surface VARCHAR(80) NOT NULL,
  recorded_at TIMESTAMPTZ NOT NULL,
  action VARCHAR(80) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_tenant_field_configuration_audit_tenant ON tenant.tenant_field_configuration_audit(tenant_id, audit_id);

-- tenant.tenant.tenant_id is UUID. V5 created tenant-field tables with VARCHAR tenant_id, so
-- this additive migration enforces UUID-shaped new tenant-field writes without rewriting already-applied V5 columns.
ALTER TABLE tenant.tenant_field_configuration
  ADD CONSTRAINT tenant_field_configuration_tenant_uuid_shape
  CHECK (tenant_id ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$') NOT VALID;

ALTER TABLE tenant.tenant_field_configuration_draft
  ADD CONSTRAINT tenant_field_configuration_draft_tenant_uuid_shape
  CHECK (tenant_id ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$') NOT VALID;

ALTER TABLE tenant.tenant_field_configuration_draft_field
  ADD CONSTRAINT tenant_field_configuration_draft_field_tenant_uuid_shape
  CHECK (tenant_id ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$') NOT VALID;

ALTER TABLE tenant.tenant_field_configuration_version
  ADD CONSTRAINT tenant_field_configuration_version_tenant_uuid_shape
  CHECK (tenant_id ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$') NOT VALID;

ALTER TABLE tenant.tenant_field_configuration_version_field
  ADD CONSTRAINT tenant_field_configuration_version_field_tenant_uuid_shape
  CHECK (tenant_id ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$') NOT VALID;

ALTER TABLE tenant.tenant_field_configuration_audit
  ADD CONSTRAINT tenant_field_configuration_audit_tenant_uuid_shape
  CHECK (tenant_id ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$') NOT VALID;
