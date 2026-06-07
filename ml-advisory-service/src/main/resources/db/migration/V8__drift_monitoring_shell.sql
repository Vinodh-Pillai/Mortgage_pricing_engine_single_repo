create table if not exists ml_monitoring_runs (
  run_id varchar(80) primary key,
  tenant_id uuid not null,
  model_version_id varchar(80) not null,
  advisory_type varchar(40) not null,
  window_start timestamptz not null,
  window_end timestamptz not null,
  policy_version varchar(160) not null,
  feature_schema_version varchar(160) not null,
  data_lineage_ref varchar(240) not null,
  status varchar(40) not null,
  highest_severity varchar(40) not null,
  aggregate_cohort_count int not null,
  minimum_cohort_size int not null,
  created_at timestamptz not null,
  completed_at timestamptz not null,
  correlation_id varchar(128) not null,
  version int not null default 1,
  unique (tenant_id, run_id),
  check (window_end > window_start),
  check (aggregate_cohort_count >= minimum_cohort_size),
  check (minimum_cohort_size > 0)
);

create table if not exists ml_monitoring_metrics (
  metric_id varchar(80) primary key,
  run_id varchar(80) not null references ml_monitoring_runs(run_id),
  metric_type varchar(80) not null,
  metric_name varchar(160) not null,
  value_numeric numeric not null,
  threshold_numeric numeric not null,
  band varchar(40) not null,
  severity varchar(40) not null,
  source_ref varchar(240) not null,
  metadata_json text not null default '{}'
);

create table if not exists ml_monitoring_alerts (
  alert_id varchar(80) primary key,
  tenant_id uuid not null,
  model_version_id varchar(80) not null,
  run_id varchar(80) not null references ml_monitoring_runs(run_id),
  alert_type varchar(80) not null,
  severity varchar(40) not null,
  status varchar(40) not null,
  recommended_action varchar(160) not null,
  created_at timestamptz not null,
  acknowledged_by varchar(128),
  disposition_reason text,
  governance_ticket varchar(160),
  advisory_only boolean not null default true,
  correlation_id varchar(128) not null,
  unique (tenant_id, alert_id)
);

create index if not exists idx_ml_monitoring_runs_tenant_model_window
  on ml_monitoring_runs (tenant_id, model_version_id, window_start);

create index if not exists idx_ml_monitoring_alerts_tenant_type_status
  on ml_monitoring_alerts (tenant_id, alert_type, status);
