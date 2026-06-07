create table if not exists admin_fee_config_row (
  tenant_id uuid not null,
  version_id uuid not null,
  row_id varchar(128) not null,
  fee_code varchar(128) not null,
  category varchar(128) not null,
  display_label varchar(256) not null,
  context_hash varchar(128) not null,
  amount_type varchar(64) not null,
  amount_value numeric(18, 8) not null,
  cap_value numeric(18, 8),
  floor_value numeric(18, 8),
  jurisdiction_ref varchar(128),
  disclosure_ref varchar(128) not null,
  tolerance_ref varchar(128) not null,
  reason_code varchar(128) not null,
  row_hash varchar(128) not null,
  created_at timestamptz not null,
  primary key (tenant_id, version_id, row_id)
);

create index if not exists idx_admin_fee_config_row_tenant_context
  on admin_fee_config_row (tenant_id, context_hash, fee_code, row_hash);

create table if not exists admin_fee_impact_result (
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

create index if not exists idx_admin_fee_impact_result_version
  on admin_fee_impact_result (tenant_id, version_id, created_at desc);
