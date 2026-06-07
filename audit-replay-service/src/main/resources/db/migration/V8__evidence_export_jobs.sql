CREATE TABLE evidence_export_jobs (
  export_id uuid PRIMARY KEY,
  tenant_id uuid NOT NULL,
  requested_by varchar(128) NOT NULL,
  purpose varchar(500) NOT NULL,
  format varchar(32) NOT NULL,
  redaction_profile varchar(80) NOT NULL,
  source_refs jsonb NOT NULL,
  status varchar(24) NOT NULL,
  artifact_uri varchar(240) NOT NULL,
  manifest_json jsonb NOT NULL,
  manifest_hash varchar(128) NOT NULL,
  expires_at timestamptz,
  retention_until date NOT NULL,
  legal_hold boolean NOT NULL DEFAULT false,
  correlation_id uuid NOT NULL,
  idempotency_key varchar(160) NOT NULL,
  request_hash varchar(128) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT NOW(),
  updated_at timestamptz NOT NULL DEFAULT NOW(),
  CONSTRAINT ux_evidence_export_jobs_tenant_export UNIQUE (tenant_id, export_id),
  CONSTRAINT ux_evidence_export_jobs_tenant_idempotency UNIQUE (tenant_id, idempotency_key),
  CONSTRAINT chk_evidence_export_jobs_status CHECK (status IN ('REQUESTED','BUILDING','READY','FAILED','EXPIRED','REVOKED')),
  CONSTRAINT chk_evidence_export_jobs_format CHECK (format IN ('ZIP_JSON','PDF_SUMMARY'))
);

CREATE INDEX idx_evidence_export_jobs_tenant_status_updated
  ON evidence_export_jobs (tenant_id, status, updated_at DESC);

CREATE INDEX idx_evidence_export_jobs_source_refs_gin
  ON evidence_export_jobs USING GIN (source_refs);
