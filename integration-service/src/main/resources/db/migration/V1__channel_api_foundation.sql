create schema if not exists integration;

create table if not exists integration.integration_channel_client (
  tenant_id uuid not null,
  channel_id uuid not null,
  external_ref varchar(128) not null,
  name varchar(160) not null,
  channel_type varchar(40) not null,
  status varchar(32) not null,
  allowed_products jsonb not null default '[]'::jsonb,
  rate_limit_policy jsonb not null,
  metadata jsonb not null default '{}'::jsonb,
  version int not null,
  created_by varchar(128) not null,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  correlation_id varchar(128) not null,
  primary key (tenant_id, channel_id),
  unique (tenant_id, external_ref)
);

create index if not exists idx_integration_channel_client_tenant_status
  on integration.integration_channel_client (tenant_id, status);

create index if not exists idx_integration_channel_client_tenant_type
  on integration.integration_channel_client (tenant_id, channel_type);

create table if not exists integration.integration_idempotency_key (
  tenant_id uuid not null,
  channel_id uuid,
  idempotency_key varchar(160) not null,
  request_hash varchar(64) not null,
  response_code varchar(16) not null,
  response_body_ref varchar(256) not null,
  expires_at timestamptz not null,
  primary key (tenant_id, channel_id, idempotency_key)
);

create table if not exists integration.integration_outbox_event (
  event_id uuid primary key,
  tenant_id uuid not null,
  channel_id uuid not null,
  event_type varchar(160) not null,
  schema_version int not null,
  payload_hash varchar(64) not null,
  payload jsonb not null,
  correlation_id varchar(128) not null,
  causation_id varchar(128) not null,
  idempotency_key varchar(160),
  actor varchar(128) not null,
  occurred_at timestamptz not null,
  published_at timestamptz
);
