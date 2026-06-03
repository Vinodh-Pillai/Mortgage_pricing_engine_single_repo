alter table rate_feed.rate_sheet
  drop constraint if exists rate_sheet_status_check;

alter table rate_feed.rate_sheet
  alter column status type varchar(40);

alter table rate_feed.rate_sheet
  add constraint rate_sheet_status_check check (
    status in ('DRAFT', 'PARSING', 'VALIDATED', 'PENDING_APPROVAL', 'APPROVED', 'SCHEDULED', 'ACTIVE', 'PUBLISHED', 'SUPERSEDED', 'REJECTED', 'ROLLBACK_PUBLISHED')
  );

alter table rate_feed.rate_sheet
  add column if not exists submitted_by varchar(128),
  add column if not exists submitted_at timestamptz,
  add column if not exists approved_by varchar(128),
  add column if not exists approved_at timestamptz,
  add column if not exists approval_status varchar(40),
  add column if not exists workflow_change_summary text;

create table if not exists rate_feed.rate_sheet_workflow_decision (
  tenant_id uuid not null,
  decision_id uuid primary key,
  version_id uuid not null,
  decision_type varchar(40) not null,
  decision varchar(40) not null,
  reason_code varchar(128) not null,
  comment_redacted text,
  actor_id varchar(128) not null,
  actor_role varchar(128) not null,
  created_at timestamptz not null default now(),
  correlation_id varchar(128) not null,
  foreign key (tenant_id, version_id) references rate_feed.rate_sheet(tenant_id, sheet_id) on delete cascade,
  constraint rate_sheet_workflow_decision_reason_required check (length(trim(reason_code)) > 0)
);

create index if not exists rate_sheet_workflow_decision_version_idx
  on rate_feed.rate_sheet_workflow_decision (tenant_id, version_id, created_at desc);

create table if not exists rate_feed.published_rate_sheet_read_model (
  tenant_id uuid not null,
  version_id uuid not null,
  investor_id uuid not null,
  channel_id uuid not null,
  effective_from timestamptz not null,
  effective_to timestamptz,
  status varchar(40) not null,
  coverage_hash varchar(128) not null,
  published_at timestamptz not null,
  published_by varchar(128) not null,
  cache_invalidation_command_id varchar(128) not null,
  primary key (tenant_id, version_id),
  foreign key (tenant_id, version_id) references rate_feed.rate_sheet(tenant_id, sheet_id) on delete cascade
);

create unique index if not exists published_rate_sheet_one_active_idx
  on rate_feed.published_rate_sheet_read_model (tenant_id, investor_id, channel_id)
  where status = 'ACTIVE';

create index if not exists published_rate_sheet_resolution_idx
  on rate_feed.published_rate_sheet_read_model (tenant_id, investor_id, channel_id, effective_from desc)
  where status = 'ACTIVE';
