create table if not exists price_boundary_policy_version (
  id varchar(120) primary key,
  tenant_id varchar(120) not null,
  scope varchar(160) not null,
  status varchar(40) not null,
  effective_from timestamptz not null,
  effective_to timestamptz,
  approved_by varchar(128),
  source_digest varchar(160) not null,
  created_at timestamptz not null default now()
);

create table if not exists price_boundary_rule (
  id varchar(120) primary key,
  tenant_id varchar(120) not null,
  policy_version_id varchar(120) not null references price_boundary_policy_version(id),
  condition_json jsonb not null default '{}'::jsonb,
  boundary_type varchar(40) not null,
  bound_value numeric(12,8) not null,
  action varchar(40) not null,
  precedence integer not null,
  reason_code varchar(120) not null,
  created_at timestamptz not null default now(),
  check (action in ('ADJUST', 'BLOCK', 'WARN')),
  check (boundary_type in ('CAP', 'FLOOR'))
);

create table if not exists price_boundary_evaluation (
  id uuid primary key,
  tenant_id varchar(120) not null,
  scenario_hash varchar(160) not null,
  policy_version_id varchar(120) not null references price_boundary_policy_version(id),
  input_price numeric(12,8) not null,
  output_price numeric(12,8),
  action varchar(40) not null,
  result_hash varchar(160) not null,
  ledger jsonb not null,
  correlation_id varchar(128) not null,
  created_at timestamptz not null default now(),
  check (action in ('ADJUST', 'BLOCK', 'WARN', 'CHECK'))
);

create index if not exists idx_price_boundary_policy_effective
  on price_boundary_policy_version (tenant_id, status, effective_from, effective_to);

create index if not exists idx_price_boundary_rule_precedence
  on price_boundary_rule (tenant_id, policy_version_id, precedence);

create index if not exists idx_price_boundary_evaluation_hash
  on price_boundary_evaluation (tenant_id, scenario_hash, result_hash);
