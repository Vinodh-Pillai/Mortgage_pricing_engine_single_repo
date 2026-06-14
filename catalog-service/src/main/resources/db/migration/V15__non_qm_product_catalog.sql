alter table catalog.product drop constraint if exists product_type_check;

alter table catalog.product
  add constraint product_type_check check (type in ('CONVENTIONAL', 'FHA', 'VA', 'USDA', 'JUMBO', 'NON_QM'));

alter table catalog.product add column if not exists product_family varchar(32);
alter table catalog.product add column if not exists product_type varchar(64);
alter table catalog.product add column if not exists non_qm_attributes jsonb not null default '{}'::jsonb;
alter table catalog.product add column if not exists pricing_metadata jsonb not null default '{}'::jsonb;

update catalog.product
set product_family = case when type = 'NON_QM' then 'NON_QM' else 'CONVENTIONAL' end
where product_family is null;

create table if not exists catalog.product_investor_channel (
  tenant_id uuid not null,
  product_code varchar(64) not null,
  investor_code varchar(64) not null,
  channel_code varchar(32) not null,
  investor_product_code varchar(80),
  status varchar(16) not null default 'ACTIVE',
  pricing_priority int,
  effective_start date,
  effective_end date,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id, product_code, investor_code, channel_code),
  constraint product_investor_channel_status_ck check (status in ('ACTIVE', 'INACTIVE', 'PENDING')),
  constraint product_investor_channel_channel_ck check (channel_code in ('RETAIL', 'CORRESPONDENT', 'WHOLESALE')),
  constraint product_investor_channel_window_ck check (effective_end is null or effective_start is null or effective_end > effective_start)
);

create index if not exists idx_product_nonqm_type
  on catalog.product (tenant_id, product_type)
  where product_family = 'NON_QM';

create index if not exists idx_product_nonqm_attrs
  on catalog.product using gin (non_qm_attributes);

create index if not exists idx_product_investor_channel_lookup
  on catalog.product_investor_channel (tenant_id, investor_code, channel_code, status);

create table if not exists catalog.product_attribute_schema (
  product_family varchar(32) not null,
  product_type varchar(64) not null,
  schema_version varchar(16) not null,
  schema_json jsonb not null,
  active boolean not null default true,
  updated_at timestamptz not null default now(),
  primary key (product_family, product_type, schema_version)
);
