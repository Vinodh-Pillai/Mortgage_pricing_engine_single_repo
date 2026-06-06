create table if not exists rate_feed.rate_feed_audit_event (
  tenant_id uuid not null,
  audit_event_id uuid not null,
  event_type varchar(120) not null,
  event_version int not null default 1,
  aggregate_type varchar(80) not null,
  aggregate_id uuid not null,
  actor_id varchar(128) not null,
  actor_type varchar(40) not null default 'USER',
  correlation_id varchar(128) not null,
  causation_id varchar(128),
  occurred_at timestamptz not null,
  source_service varchar(80) not null default 'rate-feed-service',
  before_hash varchar(128),
  after_hash varchar(128),
  evidence_refs jsonb not null default '[]'::jsonb,
  result_hash varchar(128),
  redaction_level varchar(32) not null default 'STANDARD',
  retention_until timestamptz not null,
  legal_hold boolean not null default false,
  payload_redacted jsonb not null default '{}'::jsonb,
  sequence_number bigint,
  primary key (tenant_id, audit_event_id)
);

create index if not exists rate_feed_audit_event_aggregate_idx
  on rate_feed.rate_feed_audit_event (tenant_id, aggregate_type, aggregate_id);

create index if not exists rate_feed_audit_event_correlation_idx
  on rate_feed.rate_feed_audit_event (tenant_id, correlation_id);

create index if not exists rate_feed_audit_event_occurred_idx
  on rate_feed.rate_feed_audit_event (tenant_id, occurred_at desc);

create table if not exists rate_feed.rate_feed_audit_report_snapshot (
  tenant_id uuid not null,
  snapshot_id uuid not null,
  batch_id uuid not null,
  version_id uuid,
  generated_by varchar(128) not null,
  generated_at timestamptz not null default now(),
  filters jsonb not null default '{}'::jsonb,
  format varchar(16) not null,
  storage_object_id varchar(256),
  snapshot_hash varchar(128) not null,
  retention_until timestamptz not null,
  legal_hold boolean not null default false,
  watermark_json jsonb,
  primary key (tenant_id, snapshot_id)
);

create index if not exists rate_feed_audit_report_snapshot_batch_idx
  on rate_feed.rate_feed_audit_report_snapshot (tenant_id, batch_id, generated_at desc);

create table if not exists rate_feed.rate_feed_replay_verification (
  tenant_id uuid not null,
  verification_id uuid not null,
  batch_id uuid not null,
  expected_hash varchar(128) not null,
  actual_hash varchar(128) not null,
  status varchar(32) not null,
  mismatch_classification varchar(80),
  created_by varchar(128) not null,
  correlation_id varchar(128) not null,
  created_at timestamptz not null default now(),
  primary key (tenant_id, verification_id)
);

create index if not exists rate_feed_replay_verification_batch_idx
  on rate_feed.rate_feed_replay_verification (tenant_id, batch_id, created_at desc);
