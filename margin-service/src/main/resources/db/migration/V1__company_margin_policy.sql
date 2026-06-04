create table margin_policy (
  tenant_id uuid not null,
  policy_id uuid primary key,
  policy_type varchar(40) not null default 'COMPANY',
  name varchar(160) not null,
  status varchar(40) not null,
  current_version_id uuid,
  created_by varchar(128) not null,
  updated_by varchar(128) not null,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  correlation_id varchar(128),
  constraint uq_margin_policy_name unique (tenant_id, policy_type, name)
);

create table margin_policy_version (
  tenant_id uuid not null,
  version_id uuid primary key,
  policy_id uuid not null references margin_policy(policy_id),
  version_number int not null,
  scope_json jsonb not null,
  effective_from_utc timestamptz not null,
  effective_to_utc timestamptz,
  approval_json jsonb not null default '{}'::jsonb,
  config_hash varchar(128) not null,
  lifecycle_status varchar(40) not null,
  created_at timestamptz not null,
  constraint uq_margin_policy_version unique (tenant_id, policy_id, version_number)
);

create table margin_rule (
  tenant_id uuid not null,
  rule_id uuid primary key,
  version_id uuid not null references margin_policy_version(version_id),
  priority int not null,
  unit varchar(40) not null,
  amount_ref varchar(256) not null,
  min_ref varchar(256) not null,
  max_ref varchar(256) not null,
  rounding_scale int not null,
  reason_code varchar(80) not null,
  visibility_classification varchar(80) not null default 'INTERNAL',
  constraint ck_margin_rule_unit check (unit in ('BPS', 'PRICE_POINTS'))
);

create table pricing_calculation_step (
  tenant_id uuid not null,
  quote_option_id varchar(128) not null,
  step_id uuid primary key,
  step_type varchar(80) not null,
  source_version_id uuid not null,
  price_before numeric(18, 6) not null,
  price_after numeric(18, 6) not null,
  amount numeric(18, 6) not null,
  rounding_json jsonb not null default '{}'::jsonb,
  replay_hash varchar(128) not null,
  created_at timestamptz not null
);

create index idx_margin_policy_status on margin_policy(tenant_id, status, updated_at desc);
create index idx_margin_policy_version_scope on margin_policy_version using gin (scope_json);
create index idx_pricing_calculation_step_quote on pricing_calculation_step(tenant_id, quote_option_id);
