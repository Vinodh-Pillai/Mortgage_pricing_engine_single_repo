CREATE TABLE retention_policies (
  policy_id uuid PRIMARY KEY,
  tenant_id uuid NOT NULL,
  evidence_type varchar(80) NOT NULL,
  jurisdiction varchar(80) NOT NULL,
  product_filter varchar(160),
  channel_filter varchar(160),
  retention_days int NOT NULL,
  status varchar(32) NOT NULL,
  version int NOT NULL DEFAULT 1,
  effective_from date NOT NULL,
  effective_to date,
  created_by varchar(128) NOT NULL,
  approved_by varchar(128) NOT NULL,
  published_at timestamptz NOT NULL,
  created_at timestamptz NOT NULL DEFAULT NOW(),
  updated_at timestamptz NOT NULL DEFAULT NOW(),
  correlation_id uuid NOT NULL,
  idempotency_key varchar(160) NOT NULL,
  CONSTRAINT ux_retention_policies_tenant_policy UNIQUE (tenant_id, policy_id),
  CONSTRAINT ux_retention_policies_tenant_idempotency UNIQUE (tenant_id, idempotency_key),
  CONSTRAINT chk_retention_policies_status CHECK (status IN ('DRAFT','PUBLISHED','SUSPENDED','ROLLED_BACK')),
  CONSTRAINT chk_retention_policies_retention_days CHECK (retention_days > 0),
  CONSTRAINT chk_retention_policies_sod CHECK (created_by <> approved_by)
);

CREATE INDEX idx_retention_policies_tenant_lookup
  ON retention_policies (tenant_id, evidence_type, jurisdiction, status, effective_from DESC);

CREATE TABLE legal_holds (
  hold_id uuid PRIMARY KEY,
  tenant_id uuid NOT NULL,
  case_id varchar(160) NOT NULL,
  scope_json jsonb NOT NULL,
  reason varchar(500) NOT NULL,
  status varchar(32) NOT NULL,
  applied_by varchar(128) NOT NULL,
  approved_by varchar(128) NOT NULL,
  applied_at timestamptz NOT NULL,
  released_by varchar(128),
  release_approved_by varchar(128),
  released_at timestamptz,
  correlation_id uuid NOT NULL,
  idempotency_key varchar(160) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT NOW(),
  updated_at timestamptz NOT NULL DEFAULT NOW(),
  CONSTRAINT ux_legal_holds_tenant_hold UNIQUE (tenant_id, hold_id),
  CONSTRAINT ux_legal_holds_tenant_idempotency UNIQUE (tenant_id, idempotency_key),
  CONSTRAINT chk_legal_holds_status CHECK (status IN ('ACTIVE','RELEASED')),
  CONSTRAINT chk_legal_holds_apply_sod CHECK (applied_by <> approved_by),
  CONSTRAINT chk_legal_holds_release_sod CHECK (released_by IS NULL OR release_approved_by IS NULL OR released_by <> release_approved_by)
);

CREATE INDEX idx_legal_holds_tenant_status
  ON legal_holds (tenant_id, status, applied_at DESC);

CREATE TABLE legal_hold_items (
  id uuid PRIMARY KEY,
  tenant_id uuid NOT NULL,
  hold_id uuid NOT NULL,
  subject_type varchar(80) NOT NULL,
  subject_id varchar(160) NOT NULL,
  audit_record_id uuid,
  export_id uuid,
  replay_run_id uuid,
  status varchar(32) NOT NULL,
  CONSTRAINT fk_legal_hold_items_hold FOREIGN KEY (hold_id) REFERENCES legal_holds (hold_id),
  CONSTRAINT chk_legal_hold_items_status CHECK (status IN ('ACTIVE','RELEASED'))
);

CREATE INDEX idx_legal_hold_items_tenant_hold
  ON legal_hold_items (tenant_id, hold_id, status);

CREATE INDEX idx_legal_hold_items_audit_record
  ON legal_hold_items (tenant_id, audit_record_id, status);

CREATE TABLE retention_purge_runs (
  run_id uuid PRIMARY KEY,
  tenant_id uuid NOT NULL,
  cutoff date NOT NULL,
  candidate_count int NOT NULL,
  purged_count int NOT NULL,
  skipped_held_count int NOT NULL,
  status varchar(32) NOT NULL,
  report_json jsonb NOT NULL,
  report_hash varchar(128) NOT NULL,
  requested_by varchar(128) NOT NULL,
  correlation_id uuid NOT NULL,
  idempotency_key varchar(160) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT NOW(),
  CONSTRAINT ux_retention_purge_runs_tenant_run UNIQUE (tenant_id, run_id),
  CONSTRAINT ux_retention_purge_runs_tenant_idempotency UNIQUE (tenant_id, idempotency_key),
  CONSTRAINT chk_retention_purge_runs_status CHECK (status IN ('DRY_RUN','COMPLETED'))
);

CREATE INDEX idx_retention_purge_runs_tenant_created
  ON retention_purge_runs (tenant_id, created_at DESC);
