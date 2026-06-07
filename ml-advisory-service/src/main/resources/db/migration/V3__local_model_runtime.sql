create table if not exists ml_model_invocations (
  invocation_id uuid primary key,
  tenant_id uuid not null,
  model_version_id varchar(160) not null,
  snapshot_id varchar(160) not null,
  advisory_type varchar(40) not null,
  status varchar(40) not null,
  latency_ms bigint not null,
  error_code varchar(80),
  created_at timestamptz not null,
  correlation_id varchar(128) not null
);

create index if not exists ml_model_invocations_tenant_status_idx
  on ml_model_invocations (tenant_id, status, created_at desc);

create table if not exists ml_model_runtime_health (
  runtime_id uuid primary key,
  model_version_id varchar(160) not null,
  artifact_checksum varchar(160) not null,
  loaded_at timestamptz not null,
  status varchar(40) not null,
  last_error varchar(160),
  updated_at timestamptz not null
);
