create table if not exists catalog.investor_catalog_entry (
  tenant_id uuid not null,
  id uuid primary key,
  catalog_id uuid not null,
  investor_code varchar(40) not null,
  created_by varchar(128) not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (tenant_id, investor_code)
);

create table if not exists catalog.investor_catalog_version (
  tenant_id uuid not null,
  id uuid primary key,
  investor_id uuid not null references catalog.investor_catalog_entry(id),
  version_number int not null,
  legal_name varchar(160) not null,
  investor_type varchar(30) not null,
  agency varchar(30),
  delivery_types jsonb not null,
  active_channel_codes jsonb not null,
  requires_mi_validation boolean not null default false,
  status varchar(16) not null,
  effective_start timestamptz not null,
  effective_end timestamptz,
  row_version bigint not null default 0,
  config_hash varchar(128) not null,
  request_json jsonb not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (tenant_id, investor_id, version_number),
  constraint investor_catalog_status_ck check (status in ('DRAFT','VALIDATED','PENDING_APPROVAL','APPROVED','PUBLISHED','SUSPENDED','RETIRED','REJECTED','ROLLED_BACK')),
  constraint investor_catalog_type_ck check (investor_type in ('AGENCY','AGGREGATOR','PORTFOLIO','WHOLESALE_INVESTOR')),
  constraint investor_catalog_effective_ck check (effective_end is null or effective_end > effective_start)
);

create table if not exists catalog.investor_seller_servicer (
  tenant_id uuid not null,
  id uuid primary key,
  investor_version_id uuid not null references catalog.investor_catalog_version(id),
  channel_code varchar(40) not null,
  seller_id varchar(32) not null,
  servicer_id varchar(32) not null,
  created_at timestamptz not null default now(),
  unique (tenant_id, investor_version_id, channel_code, seller_id)
);

create index if not exists investor_catalog_version_effective_idx on catalog.investor_catalog_version (tenant_id, status, effective_start, effective_end);
create index if not exists investor_catalog_entry_code_idx on catalog.investor_catalog_entry (tenant_id, investor_code);
create index if not exists investor_seller_channel_idx on catalog.investor_seller_servicer (tenant_id, investor_version_id, channel_code);
