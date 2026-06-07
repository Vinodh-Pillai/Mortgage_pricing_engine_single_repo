create table if not exists integration_webhook_delivery (
  tenant_id uuid not null,
  delivery_id uuid primary key,
  subscription_id varchar(128) not null,
  source_event_id varchar(160) not null,
  event_type varchar(160) not null,
  status varchar(40) not null,
  attempt_count int not null default 0,
  next_attempt_at timestamptz,
  payload_hash varchar(128) not null,
  signature_version varchar(80) not null,
  last_failure_class varchar(80),
  version int not null default 1,
  created_by varchar(128) not null,
  correlation_id varchar(128) not null,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  constraint uq_integration_webhook_delivery_source unique (tenant_id, subscription_id, source_event_id)
);

create table if not exists integration_webhook_delivery_attempt (
  tenant_id uuid not null,
  attempt_id uuid primary key,
  delivery_id uuid not null references integration_webhook_delivery(delivery_id),
  started_at timestamptz not null,
  completed_at timestamptz not null,
  http_status int not null,
  response_time_ms bigint not null,
  request_headers_hash varchar(128) not null,
  response_body_hash varchar(128) not null,
  failure_class varchar(80) not null,
  retryable boolean not null,
  manual boolean not null default false
);

create index if not exists idx_integration_webhook_delivery_status_next
  on integration_webhook_delivery (tenant_id, status, next_attempt_at);

create index if not exists idx_integration_webhook_delivery_subscription_created
  on integration_webhook_delivery (tenant_id, subscription_id, created_at);

create index if not exists idx_integration_webhook_delivery_source_event
  on integration_webhook_delivery (tenant_id, source_event_id);

create index if not exists idx_integration_webhook_delivery_attempt_delivery
  on integration_webhook_delivery_attempt (tenant_id, delivery_id, started_at);
