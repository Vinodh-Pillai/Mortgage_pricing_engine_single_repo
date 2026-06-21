CREATE TABLE IF NOT EXISTS audit_outbox_events (
  id uuid PRIMARY KEY,
  tenant_id uuid NOT NULL,
  aggregate_type varchar(80),
  aggregate_id varchar(120),
  aggregate_version bigint,
  event_type varchar(120),
  event_version int,
  event_key varchar(180),
  partition_key varchar(180),
  payload_json jsonb,
  headers_json jsonb,
  status varchar(24) DEFAULT 'PENDING',
  attempt_count int DEFAULT 0,
  next_attempt_at timestamptz,
  last_error_code varchar(80),
  last_error_message text,
  correlation_id uuid,
  causation_id uuid,
  actor_id varchar(120),
  created_at timestamptz DEFAULT NOW(),
  published_at timestamptz,
  integrity_hash varchar(128),
  CONSTRAINT ux_audit_outbox_events_tenant_event_key_version UNIQUE (tenant_id, event_key, event_version),
  CONSTRAINT chk_audit_outbox_events_status CHECK (status IN ('PENDING', 'IN_FLIGHT', 'PUBLISHED', 'FAILED', 'POISON'))
);

ALTER TABLE audit_outbox_events
  ADD COLUMN IF NOT EXISTS lock_version bigint NOT NULL DEFAULT 0;
