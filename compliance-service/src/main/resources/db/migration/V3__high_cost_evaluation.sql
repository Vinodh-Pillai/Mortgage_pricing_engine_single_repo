create table if not exists high_cost_evaluation (
  id uuid primary key,
  tenant_id uuid not null,
  scenario_id varchar(128) not null,
  quote_id varchar(128),
  as_of_date date not null,
  product_type varchar(80) not null,
  state_code varchar(8) not null,
  status varchar(40) not null,
  result_hash varchar(160) not null,
  federal_rule_pack_version_id varchar(160),
  state_rule_pack_version_ids jsonb not null default '[]'::jsonb,
  threshold_config_version_ids jsonb not null default '[]'::jsonb,
  request_json jsonb not null,
  result_json jsonb not null,
  correlation_id varchar(128) not null,
  idempotency_key varchar(160),
  created_at timestamptz not null,
  created_by_service varchar(128) not null,
  unique (tenant_id, idempotency_key)
);

create index if not exists idx_high_cost_evaluation_scenario
  on high_cost_evaluation (tenant_id, scenario_id);

create index if not exists idx_high_cost_evaluation_quote
  on high_cost_evaluation (tenant_id, quote_id);

create index if not exists idx_high_cost_evaluation_created
  on high_cost_evaluation (tenant_id, created_at);

create table if not exists high_cost_evaluation_ledger (
  id uuid primary key,
  tenant_id uuid not null,
  evaluation_id uuid not null references high_cost_evaluation(id),
  sequence int not null,
  test_code varchar(120) not null,
  input_ref varchar(120) not null,
  formula_ref varchar(160) not null,
  config_version_id varchar(160) not null,
  raw_value numeric(19,8) not null,
  rounded_value numeric(19,8) not null,
  comparison_operator varchar(8) not null,
  threshold_ref varchar(160) not null,
  outcome varchar(40) not null,
  reason_code varchar(160) not null,
  unique (tenant_id, evaluation_id, sequence)
);
