create table if not exists rate_feed.rate_sheet_cache_invalidation (
  tenant_id uuid not null,
  cache_invalidation_id uuid not null,
  version_id uuid not null,
  reason varchar(40) not null,
  status varchar(40) not null,
  affected_patterns jsonb not null default '[]'::jsonb,
  requested_by varchar(128) not null,
  created_at timestamptz not null default now(),
  completed_at timestamptz,
  retry_count int not null default 0,
  last_error_code varchar(80),
  correlation_id varchar(128) not null,
  expected_version_hash varchar(128) not null,
  investor_id uuid not null,
  channel_id uuid not null,
  effective_at timestamptz,
  result_hash varchar(128) not null,
  updated_at timestamptz not null default now(),
  primary key (tenant_id, cache_invalidation_id),
  foreign key (tenant_id, version_id) references rate_feed.rate_sheet(tenant_id, sheet_id) on delete cascade,
  constraint rate_sheet_cache_invalidation_reason_check check (reason in ('PUBLISH','ROLLBACK','GOVERNANCE_CHANGE','MANUAL_RETRY')),
  constraint rate_sheet_cache_invalidation_status_check check (status in ('PENDING','COMPLETED','PARTIAL','FAILED','RETRYING','BROKER_UNAVAILABLE')),
  constraint rate_sheet_cache_invalidation_retry_nonnegative check (retry_count >= 0)
);

create unique index if not exists rate_sheet_cache_invalidation_idempotency_idx
  on rate_feed.rate_sheet_cache_invalidation (tenant_id, version_id, reason, expected_version_hash);

create index if not exists rate_sheet_cache_invalidation_status_idx
  on rate_feed.rate_sheet_cache_invalidation (tenant_id, status, created_at desc);

create index if not exists rate_sheet_cache_invalidation_version_idx
  on rate_feed.rate_sheet_cache_invalidation (tenant_id, version_id);

create table if not exists rate_feed.rate_sheet_cache_ack (
  tenant_id uuid not null,
  ack_id uuid not null,
  cache_invalidation_id uuid not null,
  consumer_name varchar(128) not null,
  consumer_instance varchar(128) not null,
  status varchar(40) not null,
  acked_at timestamptz not null default now(),
  details jsonb not null default '{}'::jsonb,
  primary key (tenant_id, ack_id),
  foreign key (tenant_id, cache_invalidation_id) references rate_feed.rate_sheet_cache_invalidation(tenant_id, cache_invalidation_id) on delete cascade
);

create index if not exists rate_sheet_cache_ack_command_idx
  on rate_feed.rate_sheet_cache_ack (tenant_id, cache_invalidation_id, acked_at desc);
