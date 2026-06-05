create table if not exists pricing_adjustment_version (
  id varchar(120) primary key,
  tenant_id varchar(120) not null,
  product_code varchar(80),
  investor_code varchar(80),
  channel_code varchar(80),
  status varchar(40) not null,
  effective_from timestamptz not null,
  effective_to timestamptz,
  approved_by varchar(128),
  source_digest varchar(160) not null,
  created_at timestamptz not null default now()
);

create table if not exists pricing_adjustment_rule (
  id varchar(120) primary key,
  tenant_id varchar(120) not null,
  version_id varchar(120) not null references pricing_adjustment_version(id),
  scope varchar(80) not null,
  condition_json jsonb not null default '{}'::jsonb,
  operation varchar(40) not null,
  amount numeric(12,8) not null,
  unit varchar(40) not null,
  precedence integer not null,
  reason_code varchar(120) not null,
  created_at timestamptz not null default now()
);

create table if not exists final_price_result (
  id uuid primary key,
  tenant_id varchar(120) not null,
  selection_id uuid not null,
  scenario_hash varchar(160) not null,
  final_price numeric(9,5) not null,
  subtotal numeric(12,8) not null,
  version_graph jsonb not null,
  result_hash varchar(160) not null,
  request_hash varchar(160) not null,
  actor_id varchar(128) not null,
  correlation_id varchar(128) not null,
  idempotency_key varchar(160) not null,
  quote_request_id varchar(160),
  created_at timestamptz not null default now(),
  unique (tenant_id, idempotency_key)
);

create table if not exists final_price_ledger_entry (
  id uuid primary key,
  tenant_id varchar(120) not null,
  final_price_id uuid not null references final_price_result(id),
  ordinal integer not null,
  input_value numeric(18,8),
  output_value numeric(18,8),
  operation varchar(80) not null,
  config_ref varchar(180),
  rounding_ref varchar(180),
  reason_code varchar(120) not null,
  created_at timestamptz not null default now(),
  unique (tenant_id, final_price_id, ordinal)
);

create index if not exists idx_final_price_result_tenant_selection
  on final_price_result (tenant_id, selection_id);

create index if not exists idx_final_price_result_tenant_scenario_hash
  on final_price_result (tenant_id, scenario_hash, result_hash);

create index if not exists idx_pricing_adjustment_rule_tenant_version_precedence
  on pricing_adjustment_rule (tenant_id, version_id, precedence);
