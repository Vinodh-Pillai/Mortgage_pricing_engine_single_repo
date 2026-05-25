create table if not exists catalog.catalog_version_control (
  tenant_id uuid not null,
  version_control_id uuid primary key,
  catalog_id uuid not null,
  artifact_type varchar(64) not null,
  artifact_id uuid not null,
  artifact_code varchar(128) not null,
  version_number int not null,
  status varchar(32) not null,
  effective_start date,
  effective_end date,
  config_hash varchar(128) not null,
  snapshot_json jsonb not null,
  created_by varchar(128),
  submitted_by varchar(128),
  approved_by varchar(128),
  published_by varchar(128),
  rejected_by varchar(128),
  status_reason text,
  row_version bigint not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint catalog_vc_status_ck check (status in ('DRAFT','VALIDATED','PENDING_APPROVAL','APPROVED','PUBLISHED','SUSPENDED','RETIRED','REJECTED','ROLLED_BACK')),
  constraint catalog_vc_effective_ck check (effective_end is null or effective_start is null or effective_end > effective_start),
  unique (tenant_id, artifact_type, artifact_id, version_number)
);

create table if not exists catalog.catalog_version_validation_issue (
  tenant_id uuid not null,
  issue_id uuid primary key,
  version_control_id uuid not null references catalog.catalog_version_control(version_control_id),
  field_path varchar(240) not null,
  code varchar(80) not null,
  severity varchar(32) not null,
  message text not null,
  created_at timestamptz not null default now(),
  unique (tenant_id, version_control_id, field_path, code)
);

alter table catalog.product_catalog add column if not exists row_version bigint not null default 0;
alter table catalog.product_definition add column if not exists row_version bigint not null default 0;
alter table catalog.investor_program add column if not exists row_version bigint not null default 0;
alter table catalog.reference_entry add column if not exists row_version bigint not null default 0;
alter table catalog.market_area add column if not exists row_version bigint not null default 0;

do $$ begin
  alter table catalog.product_definition add constraint product_effective_window_ck check (effective_to is null or effective_to > effective_from);
exception when duplicate_object then null; end $$;
do $$ begin
  alter table catalog.investor_program add constraint investor_effective_window_ck check (effective_to is null or effective_to > effective_from);
exception when duplicate_object then null; end $$;
do $$ begin
  alter table catalog.reference_entry add constraint reference_effective_window_ck check (effective_to is null or effective_to > effective_from);
exception when duplicate_object then null; end $$;
do $$ begin
  alter table catalog.market_area add constraint market_effective_window_ck check (effective_to is null or effective_to > effective_from);
exception when duplicate_object then null; end $$;
do $$ begin
  alter table catalog.market_area add constraint market_status_ck check (market_status in ('ENABLED','DISABLED','RESTRICTED'));
exception when duplicate_object then null; end $$;
do $$ begin
  alter table catalog.market_area add constraint market_state_ck check (state_code ~ '^[A-Z]{2}$');
exception when duplicate_object then null; end $$;
do $$ begin
  alter table catalog.market_area add constraint market_county_fips_ck check (county_fips is null or county_fips ~ '^[0-9]{5}$');
exception when duplicate_object then null; end $$;

create index if not exists catalog_vc_status_idx on catalog.catalog_version_control (tenant_id, artifact_type, artifact_code, status);
create index if not exists catalog_vc_effective_idx on catalog.catalog_version_control (tenant_id, status, effective_start);
