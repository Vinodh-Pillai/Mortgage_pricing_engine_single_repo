-- PII-01-S03: Loan structure capture, metric trace, and persistence tables

create table if not exists scenario.scenario_loan_terms (
  tenant_id uuid not null,
  scenario_loan_terms_id uuid not null,
  scenario_id uuid not null,
  scenario_version_id uuid not null,
  loan_purpose varchar(40) not null,
  loan_amount decimal(18,2) not null,
  lien_position varchar(20) not null,
  term_months integer not null,
  amortization_type varchar(40) not null,
  subordinate_financing_amount decimal(18,2) not null default 0,
  heloc_drawn_amount decimal(18,2) not null default 0,
  heloc_limit_amount decimal(18,2) not null default 0,
  requested_lock_period_days integer not null,
  property_value_for_ltv decimal(18,2),
  calculation_trace_id uuid not null,
  quality_status varchar(40) not null,
  created_at_utc timestamptz not null default now(),
  primary key (tenant_id, scenario_loan_terms_id),
  unique (tenant_id, scenario_version_id),
  foreign key (tenant_id, scenario_id) references scenario.scenario(tenant_id, scenario_id),
  check (loan_amount > 0),
  check (subordinate_financing_amount >= 0),
  check (heloc_drawn_amount >= 0),
  check (heloc_limit_amount >= 0),
  check (property_value_for_ltv is null or property_value_for_ltv > 0)
);

create table if not exists scenario.scenario_loan_metric (
  tenant_id uuid not null,
  scenario_version_id uuid not null,
  metric_code varchar(40) not null,
  ratio_value decimal(7,5),
  bps_value decimal(9,4),
  numerator_amount decimal(18,2) not null,
  denominator_amount decimal(18,2),
  rounding_rule varchar(80) not null,
  quality_status varchar(40) not null,
  created_at_utc timestamptz not null default now(),
  primary key (tenant_id, scenario_version_id, metric_code),
  check (numerator_amount >= 0),
  check (denominator_amount is null or denominator_amount > 0)
);

create index if not exists slt_idx_scenario_version
  on scenario.scenario_loan_terms (tenant_id, scenario_id, scenario_version_id);

create index if not exists slm_idx_status
  on scenario.scenario_loan_metric (tenant_id, quality_status, scenario_version_id);
