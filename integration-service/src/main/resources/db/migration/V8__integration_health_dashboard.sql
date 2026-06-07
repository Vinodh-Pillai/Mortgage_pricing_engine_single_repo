create table if not exists integration_health_snapshot (
  tenant_id uuid not null,
  snapshot_id uuid not null,
  component_type varchar(60) not null,
  status varchar(40) not null,
  metric_window varchar(80) not null,
  last_success_at timestamptz,
  last_failure_at timestamptz,
  lag_seconds bigint not null default 0,
  error_count bigint not null default 0,
  dlq_count bigint not null default 0,
  credential_expiry_count bigint not null default 0,
  slo_breached boolean not null default false,
  summary jsonb not null default '{}'::jsonb,
  captured_at timestamptz not null,
  correlation_id varchar(128) not null,
  constraint integration_health_snapshot_pk primary key (tenant_id, snapshot_id, component_type)
);

create index if not exists integration_health_snapshot_tenant_component_idx
  on integration_health_snapshot (tenant_id, component_type, captured_at desc);

create index if not exists integration_health_snapshot_tenant_status_idx
  on integration_health_snapshot (tenant_id, status, captured_at desc);
