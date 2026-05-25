create schema if not exists rate_feed;

create table rate_feed.idempotency_record (
  tenant_id uuid not null,
  idempotency_key varchar(128) not null,
  request_hash varchar(128) not null,
  response_type varchar(128) not null,
  response_json jsonb not null,
  created_at timestamptz not null default now(),
  primary key (tenant_id, idempotency_key)
);

create table rate_feed.upload_session (
  tenant_id uuid not null,
  upload_session_id uuid not null,
  investor_id uuid not null,
  channel_id uuid not null,
  feed_format_id uuid not null,
  source_type varchar(32) not null,
  effective_at timestamptz not null,
  timezone varchar(64) not null,
  file_name varchar(255) not null,
  content_type varchar(128) not null,
  content_length_bytes bigint not null,
  supersedes_batch_id uuid,
  notes text,
  status varchar(32) not null,
  expires_at timestamptz not null,
  created_by varchar(128) not null,
  correlation_id varchar(128) not null,
  request_hash varchar(128) not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id, upload_session_id)
);

create index upload_session_status_idx on rate_feed.upload_session (tenant_id, status, created_at desc);

create table rate_feed.raw_file (
  tenant_id uuid not null,
  raw_file_id uuid not null,
  storage_object_id varchar(256) not null,
  file_sha256 varchar(64) not null,
  scan_status varchar(32) not null,
  scan_result_id varchar(128),
  retention_until timestamptz not null,
  legal_hold boolean not null default false,
  created_at timestamptz not null default now(),
  primary key (tenant_id, raw_file_id),
  constraint raw_file_sha256_length check (file_sha256 ~ '^[0-9a-fA-F]{64}$')
);

create table rate_feed.rate_feed_batch (
  tenant_id uuid not null,
  batch_id uuid not null,
  upload_session_id uuid not null,
  investor_id uuid not null,
  channel_id uuid not null,
  feed_format_id uuid not null,
  source_type varchar(32) not null,
  status varchar(32) not null,
  effective_at timestamptz not null,
  timezone varchar(64) not null,
  raw_file_id uuid not null,
  file_sha256 varchar(64) not null,
  file_name varchar(255) not null,
  content_type varchar(128) not null,
  content_length_bytes bigint not null,
  supersedes_batch_id uuid,
  uploaded_by varchar(128) not null,
  correlation_id varchar(128) not null,
  result_hash varchar(128) not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id, batch_id),
  unique (tenant_id, upload_session_id),
  unique (tenant_id, file_sha256, feed_format_id, effective_at)
);

create index rate_feed_batch_status_idx on rate_feed.rate_feed_batch (tenant_id, status, created_at desc);
create index rate_feed_batch_lookup_idx on rate_feed.rate_feed_batch (tenant_id, investor_id, channel_id, effective_at desc);

-- Governance tracking indexes (PII-04)
create index rate_feed_batch_investor_idx on rate_feed.rate_feed_batch (tenant_id, investor_id, created_at desc);
create index rate_feed_batch_channel_idx on rate_feed.rate_feed_batch (tenant_id, channel_id, created_at desc);
create index rate_feed_batch_format_idx on rate_feed.rate_feed_batch (tenant_id, feed_format_id, created_at desc);
create index rate_feed_batch_investor_channel_idx on rate_feed.rate_feed_batch (tenant_id, investor_id, channel_id, feed_format_id, created_at desc);
create index upload_session_investor_idx on rate_feed.upload_session (tenant_id, investor_id, status, created_at desc);
create index upload_session_channel_idx on rate_feed.upload_session (tenant_id, channel_id, status, created_at desc);

create table rate_feed.outbox_event (
  tenant_id uuid not null,
  event_id uuid not null,
  aggregate_type varchar(80) not null,
  aggregate_id uuid not null,
  event_type varchar(120) not null,
  event_version integer not null,
  event_key varchar(200) not null,
  headers_json jsonb not null,
  payload_json jsonb not null,
  status varchar(32) not null default 'PENDING',
  created_at timestamptz not null default now(),
  published_at timestamptz,
  primary key (tenant_id, event_id)
);

create index outbox_pending_idx on rate_feed.outbox_event (tenant_id, created_at) where published_at is null;
create index outbox_aggregate_idx on rate_feed.outbox_event (tenant_id, aggregate_id, event_type);
create index outbox_investor_idx on rate_feed.outbox_event (tenant_id, headers_json ->> 'investorId', event_type);
create index outbox_channel_idx on rate_feed.outbox_event (tenant_id, headers_json ->> 'channelId', event_type);

create table rate_feed.audit_event (
  tenant_id uuid not null,
  audit_event_id uuid not null,
  event_type varchar(120) not null,
  aggregate_type varchar(80) not null,
  aggregate_id uuid not null,
  actor_id varchar(128) not null,
  correlation_id varchar(128) not null,
  causation_id varchar(128),
  before_hash varchar(128),
  after_hash varchar(128),
  payload_redacted jsonb not null,
  result_hash varchar(128) not null,
  occurred_at timestamptz not null default now(),
  primary key (tenant_id, audit_event_id)
);
