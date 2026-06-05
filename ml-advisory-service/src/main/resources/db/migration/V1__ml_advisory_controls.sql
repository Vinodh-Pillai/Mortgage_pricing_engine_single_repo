create table if not exists ml_advisory_controls (
  id uuid primary key,
  tenant_id uuid not null,
  scope_type varchar(40) not null default 'TENANT_CHANNEL_PRODUCT',
  channel varchar(80) not null,
  product_family varchar(120) not null,
  advisory_type varchar(40) not null,
  mode varchar(40) not null,
  effective_from timestamptz not null,
  effective_to timestamptz,
  version int not null,
  status varchar(40) not null,
  created_by varchar(128) not null,
  created_at timestamptz not null,
  approved_by varchar(128),
  approval_ref varchar(160),
  change_reason text not null,
  model_risk_ticket varchar(160) not null,
  constraint ml_advisory_controls_mode_chk check (mode in ('DISABLED', 'SHADOW_ONLY', 'ADVISORY_VISIBLE')),
  constraint ml_advisory_controls_type_chk check (advisory_type in ('PRICING', 'ELIGIBILITY_RISK', 'EXPLAINABILITY', 'FEEDBACK', 'DRIFT')),
  constraint ml_advisory_controls_window_chk check (effective_to is null or effective_to > effective_from)
);

create index if not exists ml_advisory_controls_tenant_scope_idx
  on ml_advisory_controls (tenant_id, channel, product_family, advisory_type, status, effective_from desc);

create table if not exists ml_advisory_kill_switch (
  id uuid primary key,
  tenant_id uuid,
  enabled boolean not null,
  reason text not null,
  activated_by varchar(128) not null,
  activated_at timestamptz not null,
  correlation_id varchar(128) not null
);

create unique index if not exists ml_advisory_one_global_kill_switch_idx
  on ml_advisory_kill_switch ((tenant_id is null)) where tenant_id is null and enabled;

create table if not exists ml_feature_snapshots (
  snapshot_id uuid primary key,
  tenant_id uuid not null,
  scenario_id varchar(160) not null,
  pricing_result_id varchar(160) not null,
  eligibility_result_id varchar(160) not null,
  feature_schema_version varchar(80) not null,
  capture_mode varchar(40) not null,
  feature_hash varchar(128) not null,
  created_at timestamptz not null,
  retention_class varchar(80) not null,
  governance_status varchar(80) not null,
  correlation_id varchar(128) not null,
  source_refs_json jsonb not null default '{}'::jsonb,
  constraint ml_feature_snapshots_mode_chk check (capture_mode in ('SHADOW_ONLY', 'ADVISORY_VISIBLE'))
);

create index if not exists ml_feature_snapshots_tenant_scenario_idx
  on ml_feature_snapshots (tenant_id, scenario_id);

create index if not exists ml_feature_snapshots_tenant_hash_idx
  on ml_feature_snapshots (tenant_id, feature_hash);

create index if not exists ml_feature_snapshots_tenant_created_idx
  on ml_feature_snapshots (tenant_id, created_at desc);

create table if not exists ml_feature_values (
  snapshot_id uuid not null,
  tenant_id uuid not null,
  feature_name varchar(160) not null,
  feature_type varchar(80) not null,
  value_json jsonb not null default '{}'::jsonb,
  sensitivity_class varchar(80) not null,
  source_system varchar(120) not null,
  source_field varchar(160) not null,
  included boolean not null,
  exclusion_reason varchar(160) not null default '',
  value_hash varchar(128) not null,
  business_justification text not null,
  primary key (snapshot_id, feature_name),
  constraint ml_feature_values_snapshot_fk foreign key (snapshot_id) references ml_feature_snapshots (snapshot_id)
);
