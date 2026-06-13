alter table catalog.investor add column if not exists investor_code varchar(32);
alter table catalog.investor add column if not exists type varchar(16) default 'PORTFOLIO';
alter table catalog.investor add column if not exists delivery_type varchar(16) default 'FLOW';
alter table catalog.investor add column if not exists settlement_days int default 30;
alter table catalog.investor add column if not exists api_endpoint varchar(500);
alter table catalog.investor add column if not exists api_credentials jsonb;
alter table catalog.investor add column if not exists updated_at timestamptz not null default now();

update catalog.investor set investor_code = code where investor_code is null;

create table if not exists catalog.investor_eligibility (
  tenant_id uuid not null,
  id uuid not null,
  investor_id uuid not null references catalog.investor(investor_id),
  loan_purpose varchar(32) not null,
  property_type varchar(32) not null,
  occupancy_type varchar(32) not null,
  min_fico int,
  max_fico int,
  max_ltv decimal(5,2),
  max_cltv decimal(5,2),
  max_dti decimal(5,2),
  min_loan_amount decimal(15,2),
  max_loan_amount decimal(15,2),
  allowed_states jsonb not null default '[]'::jsonb,
  excluded_counties jsonb not null default '[]'::jsonb,
  overlays jsonb not null default '{}'::jsonb,
  effective_date date not null,
  expiration_date date,
  is_active boolean not null default true,
  primary key (tenant_id, id),
  constraint investor_eligibility_effective_ck check (expiration_date is null or expiration_date >= effective_date)
);

create index if not exists investor_eligibility_lookup_idx on catalog.investor_eligibility (tenant_id, investor_id, effective_date, expiration_date, is_active);
