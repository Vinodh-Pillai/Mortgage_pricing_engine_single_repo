create table if not exists eligibility.conforming_loan_limit_set (
  tenant_id uuid not null,
  limit_set_id uuid primary key,
  agency varchar(16) not null,
  year int not null,
  status varchar(16) not null,
  effective_from date not null,
  effective_to date,
  version int not null,
  source_name varchar(128),
  source_document_uri varchar(512),
  approved_by varchar(128),
  approved_at_utc timestamptz,
  created_at_utc timestamptz not null default now(),
  updated_at_utc timestamptz not null default now(),
  constraint limit_set_status_ck check (status in ('DRAFT','APPROVED','PUBLISHED','SUSPENDED','RETIRED')),
  constraint limit_set_effective_ck check (effective_to is null or effective_to > effective_from),
  unique (tenant_id, agency, year, version)
);

create table if not exists eligibility.conforming_loan_limit_row (
  tenant_id uuid not null,
  limit_row_id uuid primary key,
  limit_set_id uuid not null references eligibility.conforming_loan_limit_set(limit_set_id),
  state_code char(2) not null,
  county_name varchar(128),
  county_fips varchar(5),
  units int not null,
  limit_amount decimal(18,2) not null,
  high_cost_area boolean not null default false,
  row_hash varchar(128) not null,
  constraint limit_row_units_ck check (units between 1 and 4),
  constraint limit_row_amount_ck check (limit_amount > 0),
  constraint limit_row_state_ck check (state_code ~ '^[A-Z]{2}$'),
  constraint limit_row_fips_ck check (county_fips is null or county_fips ~ '^[0-9]{5}$')
);

create table if not exists eligibility.fico_ltv_matrix_set (
  tenant_id uuid not null,
  matrix_set_id uuid primary key,
  product_family varchar(32) not null,
  investor_code varchar(64),
  channel varchar(32),
  status varchar(16) not null,
  effective_from date not null,
  effective_to date,
  version int not null,
  created_by varchar(128),
  approved_by varchar(128),
  created_at_utc timestamptz not null default now(),
  updated_at_utc timestamptz not null default now(),
  constraint matrix_set_status_ck check (status in ('DRAFT','APPROVED','PUBLISHED','SUSPENDED','RETIRED')),
  constraint matrix_set_effective_ck check (effective_to is null or effective_to > effective_from)
);

create table if not exists eligibility.fico_ltv_matrix_row (
  tenant_id uuid not null,
  matrix_row_id uuid primary key,
  matrix_set_id uuid not null references eligibility.fico_ltv_matrix_set(matrix_set_id),
  fico_min int not null,
  fico_max int not null,
  max_ltv decimal(7,5) not null,
  max_cltv decimal(7,5),
  loan_purpose varchar(32) not null,
  occupancy_type varchar(32) not null,
  property_type varchar(32),
  units_min int not null default 1,
  units_max int not null default 4,
  documentation_type varchar(32),
  aus_type varchar(32),
  severity_if_missing_fico varchar(32) not null default 'WARNING',
  reason_code varchar(64) not null,
  row_hash varchar(128) not null,
  constraint matrix_fico_ck check (fico_min between 300 and 850 and fico_max between 300 and 850 and fico_min <= fico_max),
  constraint matrix_ltv_ck check (max_ltv > 0 and max_ltv <= 2),
  constraint matrix_units_ck check (units_min between 1 and 4 and units_max between 1 and 4 and units_min <= units_max)
);

create table if not exists eligibility.eligibility_explanation_read_model (
  tenant_id uuid not null,
  quote_id uuid not null,
  quote_option_id uuid not null,
  eligibility_status varchar(32) not null,
  summary_json jsonb not null,
  rules_json jsonb not null,
  audit_package_id uuid not null,
  result_hash varchar(128) not null,
  created_at_utc timestamptz not null default now(),
  updated_at_utc timestamptz not null default now(),
  primary key (tenant_id, quote_option_id)
);

create index if not exists limit_set_resolve_idx on eligibility.conforming_loan_limit_set (tenant_id, agency, status, effective_from, effective_to);
create index if not exists limit_row_resolve_idx on eligibility.conforming_loan_limit_row (tenant_id, state_code, county_fips, units);
create index if not exists matrix_set_resolve_idx on eligibility.fico_ltv_matrix_set (tenant_id, product_family, investor_code, channel, status, effective_from, effective_to);
create index if not exists matrix_row_resolve_idx on eligibility.fico_ltv_matrix_row (tenant_id, fico_min, fico_max, loan_purpose, occupancy_type, property_type);
