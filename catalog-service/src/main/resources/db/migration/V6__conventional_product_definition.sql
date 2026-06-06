create table if not exists catalog.conventional_product_definition (
  tenant_id uuid not null,
  id uuid primary key,
  catalog_id uuid not null,
  product_code varchar(40) not null,
  created_by varchar(128) not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (tenant_id, product_code)
);

create table if not exists catalog.conventional_product_version (
  tenant_id uuid not null,
  id uuid primary key,
  product_definition_id uuid not null references catalog.conventional_product_definition(id),
  version_number int not null,
  product_name varchar(120) not null,
  taxonomy_type_code varchar(40) not null,
  status varchar(16) not null,
  amortization_type varchar(10) not null,
  arm_index_code varchar(30),
  fixed_period_months int,
  adjustment_period_months int,
  min_loan_amount numeric(14,2) not null,
  max_loan_amount numeric(14,2) not null,
  effective_start timestamptz not null,
  effective_end timestamptz,
  display_priority int not null default 100,
  row_version bigint not null default 0,
  config_hash varchar(128) not null,
  request_json jsonb not null,
  blocking_errors_json jsonb not null default '[]'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (tenant_id, product_definition_id, version_number),
  constraint conv_product_status_ck check (status in ('DRAFT','VALIDATED','PENDING_APPROVAL','APPROVED','PUBLISHED','SUSPENDED','RETIRED','REJECTED','ROLLED_BACK')),
  constraint conv_product_amount_ck check (min_loan_amount <= max_loan_amount),
  constraint conv_product_fixed_arm_ck check (
    (amortization_type = 'FIXED' and arm_index_code is null and fixed_period_months is null and adjustment_period_months is null)
    or (amortization_type = 'ARM' and arm_index_code is not null and fixed_period_months in (60,84,120) and adjustment_period_months = 6)
  ),
  constraint conv_product_effective_ck check (effective_end is null or effective_end > effective_start)
);

create table if not exists catalog.conventional_product_allowed_value (
  tenant_id uuid not null,
  id uuid primary key,
  product_version_id uuid not null references catalog.conventional_product_version(id),
  value_type varchar(24) not null,
  value_code varchar(40) not null,
  referenced_version_id uuid not null,
  created_at timestamptz not null default now(),
  unique (tenant_id, product_version_id, value_type, value_code),
  constraint conv_allowed_value_type_ck check (value_type in ('INVESTOR','CHANNEL','TERM','PROPERTY_TYPE','OCCUPANCY','LOAN_PURPOSE','STATE'))
);

create index if not exists conv_product_version_effective_idx on catalog.conventional_product_version (tenant_id, status, effective_start, effective_end);
create index if not exists conv_product_version_sort_idx on catalog.conventional_product_version (tenant_id, status, display_priority);
create index if not exists conv_product_allowed_lookup_idx on catalog.conventional_product_allowed_value (tenant_id, value_type, value_code, product_version_id);
create index if not exists conv_product_definition_code_idx on catalog.conventional_product_definition (tenant_id, product_code);
