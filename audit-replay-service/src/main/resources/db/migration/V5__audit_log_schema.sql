CREATE TABLE audit_snapshots (
  id uuid PRIMARY KEY,
  tenant_id uuid NOT NULL,
  content_hash varchar(128) NOT NULL,
  encryption_key_ref varchar(180) NOT NULL,
  encrypted_snapshot_json jsonb NOT NULL,
  created_at timestamptz NOT NULL DEFAULT NOW(),
  CONSTRAINT ux_audit_snapshots_tenant_id UNIQUE (tenant_id, id)
);

CREATE TABLE audit_records (
  id uuid PRIMARY KEY,
  tenant_id uuid NOT NULL,
  event_id uuid NOT NULL,
  action varchar(120) NOT NULL,
  subject_type varchar(80) NOT NULL,
  subject_id varchar(140) NOT NULL,
  subject_version bigint,
  actor_type varchar(40) NOT NULL,
  actor_id varchar(120) NOT NULL,
  actor_display varchar(160),
  correlation_id uuid NOT NULL,
  causation_id uuid,
  request_id uuid NOT NULL,
  source_ip_hash varchar(128),
  user_agent_hash varchar(128),
  before_ref uuid REFERENCES audit_snapshots(id),
  after_ref uuid REFERENCES audit_snapshots(id),
  snapshot_json jsonb NOT NULL,
  redaction_profile varchar(80) NOT NULL,
  config_version_refs jsonb NOT NULL,
  result varchar(32) NOT NULL,
  reason_code varchar(80),
  occurred_at timestamptz NOT NULL,
  created_at timestamptz NOT NULL DEFAULT NOW(),
  retention_until date NOT NULL,
  legal_hold boolean NOT NULL DEFAULT false,
  integrity_hash varchar(128) NOT NULL,
  previous_hash varchar(128),
  CONSTRAINT ux_audit_records_tenant_id UNIQUE (tenant_id, id),
  CONSTRAINT ux_audit_records_tenant_request UNIQUE (tenant_id, request_id),
  CONSTRAINT chk_audit_records_result CHECK (result IN ('SUCCESS', 'DENIED', 'FAILED', 'NOOP'))
);

CREATE INDEX idx_audit_records_tenant_occurred_at
  ON audit_records (tenant_id, occurred_at DESC);

CREATE INDEX idx_audit_records_tenant_subject
  ON audit_records (tenant_id, subject_type, subject_id);

CREATE INDEX idx_audit_records_tenant_correlation
  ON audit_records (tenant_id, correlation_id);

CREATE INDEX idx_audit_records_config_version_refs_gin
  ON audit_records USING GIN (config_version_refs);

CREATE OR REPLACE FUNCTION reject_audit_log_mutation()
RETURNS trigger AS $$
BEGIN
  RAISE EXCEPTION 'audit log schema is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_records_no_update
  BEFORE UPDATE OR DELETE ON audit_records
  FOR EACH ROW EXECUTE FUNCTION reject_audit_log_mutation();

CREATE TRIGGER trg_audit_snapshots_no_update
  BEFORE UPDATE OR DELETE ON audit_snapshots
  FOR EACH ROW EXECUTE FUNCTION reject_audit_log_mutation();
