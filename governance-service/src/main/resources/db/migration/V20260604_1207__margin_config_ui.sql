create table if not exists admin_margin_config_row (
  tenant_id uuid not null,
  version_id uuid not null,
  row_id varchar(128) not null,
  context_hash varchar(128) not null,
  product_ref varchar(128),
  investor_ref varchar(128),
  channel_ref varchar(128),
  state_ref varchar(64),
  margin_value numeric(18, 8) not null,
  unit varchar(32) not null,
  cap numeric(18, 8),
  floor numeric(18, 8),
  reason_code varchar(128) not null,
  row_hash varchar(128) not null,
  created_at timestamptz not null,
  primary key (tenant_id, version_id, row_id)
);

create index if not exists idx_admin_margin_config_row_tenant_context
  on admin_margin_config_row (tenant_id, context_hash, row_hash);

create table if not exists admin_margin_impact_result (
  tenant_id uuid not null,
  simulation_id uuid not null,
  version_id uuid not null,
  scenario_fixture_id varchar(256) not null,
  result_hash varchar(128) not null,
  summary_json jsonb not null default '{}'::jsonb,
  created_by varchar(128) not null,
  created_at timestamptz not null,
  primary key (tenant_id, simulation_id)
);

create index if not exists idx_admin_margin_impact_result_version
  on admin_margin_impact_result (tenant_id, version_id, created_at desc);
