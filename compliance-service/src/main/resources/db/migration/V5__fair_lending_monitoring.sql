create table if not exists fair_lending_monitor_config (
  id uuid primary key,
  tenant_id varchar(64) not null,
  version int not null,
  status varchar(40) not null,
  effective_from date not null,
  effective_to date,
  metric_definitions text not null,
  peer_group_refs text not null,
  protected_class_policy_ref varchar(160) not null,
  alert_policy_refs text not null,
  config_hash varchar(160) not null,
  approved_by varchar(128),
  approved_at timestamp,
  created_at timestamp not null,
  updated_at timestamp not null
);

create unique index if not exists idx_fair_lending_monitor_config_tenant_version
  on fair_lending_monitor_config (tenant_id, version);

create index if not exists idx_fair_lending_monitor_config_tenant_effective
  on fair_lending_monitor_config (tenant_id, status, effective_from, effective_to);

create table if not exists fair_lending_snapshot (
  id uuid primary key,
  tenant_id varchar(64) not null,
  config_version_id uuid not null,
  period_start date not null,
  period_end date not null,
  status varchar(40) not null,
  population_count int not null,
  data_completeness_score numeric(19,8) not null,
  result_hash varchar(160) not null,
  correlation_id varchar(128) not null,
  created_at timestamp not null,
  updated_at timestamp not null,
  foreign key (config_version_id) references fair_lending_monitor_config(id)
);

create index if not exists idx_fair_lending_snapshot_tenant_period
  on fair_lending_snapshot (tenant_id, period_start, period_end);

create index if not exists idx_fair_lending_snapshot_tenant_status
  on fair_lending_snapshot (tenant_id, status, updated_at);

create table if not exists fair_lending_metric_result (
  id uuid primary key,
  tenant_id varchar(64) not null,
  snapshot_id uuid not null,
  metric_code varchar(80) not null,
  peer_group_key varchar(160) not null,
  comparison_group_key varchar(160) not null,
  outcome_measure varchar(80) not null,
  value numeric(19,8) not null,
  threshold_ref varchar(160) not null,
  severity varchar(40) not null,
  reason_code varchar(120) not null,
  supporting_refs text not null,
  foreign key (snapshot_id) references fair_lending_snapshot(id)
);

create index if not exists idx_fair_lending_metric_tenant_snapshot
  on fair_lending_metric_result (tenant_id, snapshot_id);

create table if not exists fair_lending_alert (
  id uuid primary key,
  tenant_id varchar(64) not null,
  snapshot_id uuid not null,
  metric_result_id uuid,
  severity varchar(40) not null,
  status varchar(40) not null,
  assigned_to varchar(128),
  disposition varchar(120),
  review_comments text,
  created_at timestamp not null,
  updated_at timestamp not null,
  foreign key (snapshot_id) references fair_lending_snapshot(id),
  foreign key (metric_result_id) references fair_lending_metric_result(id)
);

create index if not exists idx_fair_lending_alert_tenant_status_severity
  on fair_lending_alert (tenant_id, status, severity, updated_at);
