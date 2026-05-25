create schema if not exists catalog;

create table if not exists catalog.product_catalog (
  tenant_id uuid not null,
  catalog_id uuid not null,
  version int not null,
  status varchar(40) not null,
  replay_hash varchar(128) not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id, catalog_id)
);

create table if not exists catalog.reference_entry (
  tenant_id uuid not null,
  entry_id uuid primary key,
  catalog_id uuid not null,
  catalog_type varchar(60) not null,
  code varchar(80) not null,
  label varchar(160) not null,
  category varchar(80),
  attributes jsonb not null default '{}'::jsonb,
  effective_from date not null,
  effective_to date,
  unique (tenant_id, catalog_type, code)
);

create table if not exists catalog.product_definition (
  tenant_id uuid not null,
  product_id uuid primary key,
  catalog_id uuid not null,
  product_code varchar(80) not null,
  product_name varchar(160) not null,
  product_family varchar(80) not null,
  allowed_channels jsonb not null,
  allowed_states jsonb not null,
  effective_from date not null,
  effective_to date,
  unique (tenant_id, product_code)
);

create table if not exists catalog.investor_program (
  tenant_id uuid not null,
  investor_id uuid primary key,
  catalog_id uuid not null,
  investor_code varchar(80) not null,
  investor_name varchar(160) not null,
  channels jsonb not null,
  product_codes jsonb not null,
  effective_from date not null,
  effective_to date,
  unique (tenant_id, investor_code)
);

create table if not exists catalog.market_area (
  tenant_id uuid not null,
  market_id uuid primary key,
  catalog_id uuid not null,
  state_code varchar(2) not null,
  county_fips varchar(5),
  county_name varchar(120),
  market_status varchar(40) not null,
  allowed_channels jsonb not null default '[]'::jsonb,
  effective_from date not null,
  effective_to date,
  unique (tenant_id, state_code, county_fips)
);

create table if not exists catalog.product_config_snapshot (
  snapshot_id uuid primary key,
  tenant_id uuid not null,
  snapshot_hash varchar(128) not null,
  request_json jsonb not null,
  snapshot_json jsonb not null,
  created_at timestamptz not null default now(),
  unique (tenant_id, snapshot_hash)
);

create table if not exists catalog.catalog_idempotency_record (
  tenant_id uuid not null,
  idempotency_key varchar(160) not null,
  request_hash varchar(128) not null,
  response_json jsonb not null,
  created_at timestamptz not null default now(),
  primary key (tenant_id, idempotency_key)
);

create table if not exists catalog.catalog_outbox_event (
  tenant_id uuid not null,
  event_id uuid primary key,
  catalog_id uuid not null,
  event_type varchar(120) not null,
  event_version int not null,
  payload_json jsonb not null,
  occurred_at timestamptz not null default now(),
  published_at timestamptz
);

create table if not exists catalog.catalog_audit_record (
  tenant_id uuid not null,
  audit_id uuid primary key,
  catalog_id uuid not null,
  action varchar(120) not null,
  replay_hash varchar(128) not null,
  payload_json jsonb not null,
  occurred_at timestamptz not null default now()
);

create index if not exists reference_entry_lookup_idx on catalog.reference_entry (tenant_id, catalog_type, code, effective_from, effective_to);
create index if not exists product_definition_lookup_idx on catalog.product_definition (tenant_id, product_family, effective_from, effective_to);
create index if not exists investor_program_lookup_idx on catalog.investor_program (tenant_id, investor_code, effective_from, effective_to);
create index if not exists market_area_lookup_idx on catalog.market_area (tenant_id, state_code, county_fips, effective_from, effective_to);
create index if not exists catalog_outbox_unpublished_idx on catalog.catalog_outbox_event (tenant_id, occurred_at) where published_at is null;
