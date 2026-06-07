CREATE TABLE quote_replay_runs (
  run_id uuid PRIMARY KEY,
  tenant_id uuid NOT NULL,
  source_quote_id varchar(140),
  source_audit_record_id uuid NOT NULL REFERENCES audit_records(id),
  requested_by varchar(128) NOT NULL,
  reason varchar(500) NOT NULL,
  mode varchar(24) NOT NULL,
  status varchar(24) NOT NULL,
  original_hash varchar(128) NOT NULL,
  replay_hash varchar(128) NOT NULL,
  mismatch_code varchar(80),
  config_version_refs jsonb NOT NULL,
  event_sequence_ref varchar(180) NOT NULL,
  started_at timestamptz NOT NULL,
  completed_at timestamptz NOT NULL,
  correlation_id uuid NOT NULL,
  idempotency_key varchar(160) NOT NULL,
  request_hash varchar(128) NOT NULL,
  CONSTRAINT ux_quote_replay_runs_tenant_run UNIQUE (tenant_id, run_id),
  CONSTRAINT ux_quote_replay_runs_tenant_idempotency UNIQUE (tenant_id, idempotency_key),
  CONSTRAINT chk_quote_replay_runs_mode CHECK (mode IN ('VERIFY', 'DIAGNOSE')),
  CONSTRAINT chk_quote_replay_runs_status CHECK (status IN ('REQUESTED', 'RUNNING', 'MATCH', 'MISMATCH', 'FAILED', 'CANCELLED'))
);

CREATE INDEX idx_quote_replay_runs_tenant_status_completed
  ON quote_replay_runs (tenant_id, status, completed_at DESC);

CREATE INDEX idx_quote_replay_runs_tenant_source_quote
  ON quote_replay_runs (tenant_id, source_quote_id);

CREATE TABLE quote_replay_artifacts (
  artifact_id uuid PRIMARY KEY,
  tenant_id uuid NOT NULL,
  run_id uuid NOT NULL REFERENCES quote_replay_runs(run_id),
  input_snapshot_json jsonb NOT NULL,
  replay_ledger_json jsonb NOT NULL,
  diff_json jsonb NOT NULL,
  evidence_export_ref varchar(240) NOT NULL,
  integrity_hash varchar(128) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT NOW(),
  CONSTRAINT ux_quote_replay_artifacts_tenant_run UNIQUE (tenant_id, run_id)
);
