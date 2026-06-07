-- owner_story: PII-17-S10
-- purpose: observability-owned read models for the service-local performance dashboard.
-- rollback notes: drop indexes first, then drop performance_alert_snapshot and performance_metric_snapshot if rollback is required in local/dev.

create table if not exists performance_metric_snapshot (
  id uuid primary key,
  tenant_id uuid not null,
  panel varchar(80) not null,
  metric_name varchar(160) not null,
  dimensions_jsonb jsonb not null default '{}'::jsonb,
  value_numeric numeric,
  value_text varchar(160),
  window_start timestamptz not null,
  window_end timestamptz not null,
  freshness_status varchar(40) not null,
  source varchar(160) not null,
  partial boolean not null default false,
  created_at timestamptz not null,
  constraint chk_perf_metric_window check (window_start <= window_end),
  constraint chk_perf_metric_value_present check (value_numeric is not null or value_text is not null)
);

create index if not exists idx_perf_metric_tenant_panel_window
  on performance_metric_snapshot (tenant_id, panel, window_end desc);

create index if not exists idx_perf_metric_tenant_name_window
  on performance_metric_snapshot (tenant_id, metric_name, window_end desc);

create index if not exists idx_perf_metric_dimensions
  on performance_metric_snapshot using gin (dimensions_jsonb);

create table if not exists performance_alert_snapshot (
  id uuid primary key,
  tenant_id uuid not null,
  panel varchar(80) not null,
  severity varchar(40) not null,
  alert_key varchar(160) not null,
  status varchar(40) not null,
  summary varchar(180) not null,
  runbook_slug varchar(120),
  started_at timestamptz not null,
  resolved_at timestamptz,
  correlation_id varchar(128) not null,
  constraint chk_perf_alert_resolved_window check (resolved_at is null or resolved_at >= started_at)
);

create index if not exists idx_perf_alert_tenant_status_severity_started
  on performance_alert_snapshot (tenant_id, status, severity, started_at desc);

create index if not exists idx_perf_alert_tenant_runbook_started
  on performance_alert_snapshot (tenant_id, runbook_slug, started_at desc);
