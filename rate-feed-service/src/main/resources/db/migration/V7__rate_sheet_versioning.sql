alter table rate_feed.rate_sheet
  add column if not exists version_label varchar(160),
  add column if not exists source_batch_id uuid;

create index if not exists rate_sheet_source_batch_idx
  on rate_feed.rate_sheet (tenant_id, source_batch_id)
  where source_batch_id is not null;

create table if not exists rate_feed.rate_sheet_version_lineage (
  tenant_id uuid not null,
  version_id uuid not null,
  parent_version_id uuid,
  lineage_reason_code varchar(128) not null,
  created_at timestamptz not null default now(),
  primary key (tenant_id, version_id),
  foreign key (tenant_id, version_id) references rate_feed.rate_sheet(tenant_id, sheet_id) on delete cascade,
  foreign key (tenant_id, parent_version_id) references rate_feed.rate_sheet(tenant_id, sheet_id) on delete set null,
  constraint rate_sheet_version_lineage_reason_required check (length(trim(lineage_reason_code)) > 0)
);

create index if not exists rate_sheet_version_lineage_parent_idx
  on rate_feed.rate_sheet_version_lineage (tenant_id, parent_version_id)
  where parent_version_id is not null;
