alter table catalog.market_area add column if not exists state_name varchar(80);
alter table catalog.market_area add column if not exists restriction_reason_code varchar(40);
alter table catalog.market_area add column if not exists allowed_product_codes jsonb not null default '[]'::jsonb;
alter table catalog.market_area add column if not exists status varchar(16) not null default 'DRAFT';

create table if not exists catalog.market_import_batch (
  tenant_id uuid not null,
  id uuid primary key,
  catalog_id uuid not null,
  import_name varchar(120) not null,
  status varchar(20) not null,
  accepted_rows int not null,
  rejected_rows int not null,
  created_by varchar(128) not null,
  created_at timestamptz not null default now(),
  source_hash varchar(128) not null,
  unique (tenant_id, source_hash)
);

create index if not exists market_area_status_idx on catalog.market_area (tenant_id, status, effective_from);
create index if not exists market_area_allowed_product_gin on catalog.market_area using gin (allowed_product_codes);
create index if not exists market_area_allowed_channel_gin on catalog.market_area using gin (allowed_channels);
