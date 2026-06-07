create table if not exists ml_advisory_feedback (
  feedback_id uuid primary key,
  tenant_id uuid not null,
  advisory_id uuid not null,
  model_version_id varchar(160) not null,
  snapshot_id varchar(160) not null,
  advisory_type varchar(80) not null,
  confidence_band varchar(80) not null,
  actor_id varchar(128) not null,
  actor_role varchar(128) not null,
  source_surface varchar(128) not null,
  outcome varchar(40) not null,
  reason_code varchar(128) not null,
  comment_redacted text,
  comment_sensitivity varchar(80) not null,
  created_at timestamptz not null,
  supersedes_feedback_id uuid,
  correlation_id varchar(128) not null,
  constraint ml_advisory_feedback_outcome_chk check (outcome in ('USEFUL', 'NOT_USEFUL', 'DISMISS', 'REPORT_CONCERN'))
);

create unique index if not exists ml_advisory_feedback_active_actor_idx
  on ml_advisory_feedback (tenant_id, advisory_id, actor_id, source_surface)
  where supersedes_feedback_id is null;

create index if not exists ml_advisory_feedback_tenant_model_idx
  on ml_advisory_feedback (tenant_id, model_version_id, advisory_type, confidence_band, created_at desc);

create table if not exists ml_feedback_aggregates (
  tenant_id uuid not null,
  model_version_id varchar(160) not null,
  advisory_type varchar(80) not null,
  confidence_band varchar(80) not null,
  period_start timestamptz not null,
  period_end timestamptz not null,
  useful_count integer not null default 0,
  not_useful_count integer not null default 0,
  concern_count integer not null default 0,
  primary key (tenant_id, model_version_id, advisory_type, confidence_band, period_start)
);
