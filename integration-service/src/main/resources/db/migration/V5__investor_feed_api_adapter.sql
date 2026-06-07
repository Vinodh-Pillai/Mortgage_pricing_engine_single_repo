create table if not exists integration_investor_api_adapter (
  tenant_id uuid not null,
  adapter_id varchar(128) not null,
  investor_id varchar(128) not null,
  base_url_hash varchar(128) not null,
  credential_ref varchar(256) not null,
  status varchar(40) not null,
  schema_version varchar(80) not null,
  poll_schedule varchar(160) not null,
  rate_limit_per_minute int not null,
  enabled boolean not null default true,
  effective_from date not null,
  version int not null default 1,
  created_by varchar(128) not null,
  correlation_id varchar(128) not null,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  constraint pk_integration_investor_api_adapter primary key (tenant_id, adapter_id),
  constraint ck_integration_investor_api_adapter_rate_limit check (rate_limit_per_minute > 0)
);

create table if not exists integration_investor_feed_run (
  tenant_id uuid not null,
  run_id uuid not null,
  adapter_id varchar(128) not null,
  feed_type varchar(80) not null,
  status varchar(40) not null,
  cursor_ref varchar(256),
  source_checksum varchar(128) not null,
  normalized_checksum varchar(128) not null,
  source_count int not null default 0,
  normalized_count int not null default 0,
  error_summary varchar(512),
  actor_id varchar(128) not null,
  correlation_id varchar(128) not null,
  started_at timestamptz not null,
  completed_at timestamptz,
  constraint pk_integration_investor_feed_run primary key (tenant_id, run_id),
  constraint fk_integration_investor_feed_run_adapter foreign key (tenant_id, adapter_id) references integration_investor_api_adapter(tenant_id, adapter_id)
);

create table if not exists integration_investor_feed_record_staging (
  tenant_id uuid not null,
  run_id uuid not null,
  record_id varchar(256) not null,
  external_record_id varchar(256) not null,
  record_type varchar(80) not null,
  normalized_json jsonb not null,
  validation_status varchar(40) not null,
  reason_codes varchar(512) not null default '',
  created_at timestamptz not null default now(),
  constraint pk_integration_investor_feed_record_staging primary key (tenant_id, run_id, record_id),
  constraint fk_integration_investor_feed_record_run foreign key (tenant_id, run_id) references integration_investor_feed_run(tenant_id, run_id)
);

create index if not exists idx_integration_investor_api_adapter_status
  on integration_investor_api_adapter (tenant_id, status, updated_at desc);

create index if not exists idx_integration_investor_feed_run_adapter_status
  on integration_investor_feed_run (tenant_id, adapter_id, status, started_at desc);

create index if not exists idx_integration_investor_feed_record_run
  on integration_investor_feed_record_staging (tenant_id, run_id, record_type);
