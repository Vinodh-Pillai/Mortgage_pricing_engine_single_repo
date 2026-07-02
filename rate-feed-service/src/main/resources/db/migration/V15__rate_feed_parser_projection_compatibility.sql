-- V15: Increment 5 parser/persistence compatibility hardening.

create extension if not exists pgcrypto;

alter table rate_feed.rate_sheet
  alter column status type varchar(40),
  alter column grid_hash type varchar(128),
  alter column result_hash type varchar(128);

alter table rate_feed.activation_audit
  alter column grid_hash_before type varchar(128),
  alter column grid_hash_after type varchar(128);

-- PostgreSQL does not allow changing a column type while views depend on it.
-- Drop only the known compatibility/governance views that project
-- rate_feed.rate_feed_batch.status, then recreate them with the same contract
-- after widening the column. Avoid CASCADE so unexpected dependents fail safely.
drop view if exists rate_feed.rate_feed_ingestion_batch;
drop view if exists rate_feed.governance_rate_feed_batch;

alter table rate_feed.rate_feed_batch
  alter column status type varchar(40),
  alter column grid_hash type varchar(128),
  drop constraint if exists rate_feed_batch_status_check;

alter table rate_feed.rate_feed_batch
  add constraint rate_feed_batch_status_check check (
    status in (
      'UPLOADED','PARSING','PARSED','PARSE_FAILED','NORMALIZED','NORMALIZATION_FAILED',
      'VALIDATION_PASSED','VALIDATION_FAILED','VALIDATED','ACTIVE','SUPERSEDED','REJECTED',
      'OCR_REVIEW_REQUIRED','OCR_APPROVED','OCR_FAILED','OCR_REJECTED',
      'RECEIVED','VALIDATION_PENDING'
    )
  );

create or replace view rate_feed.rate_feed_ingestion_batch as
select
  tenant_id,
  batch_id,
  upload_session_id,
  investor_id,
  channel_id,
  feed_format_id,
  source_type,
  status,
  effective_at,
  timezone,
  raw_file_id,
  file_sha256,
  file_name,
  content_type,
  content_length_bytes,
  supersedes_batch_id,
  uploaded_by,
  correlation_id,
  result_hash,
  created_at,
  updated_at
from rate_feed.rate_feed_batch;

create or replace view rate_feed.governance_rate_feed_batch as
select
  tenant_id,
  batch_id,
  upload_session_id,
  investor_id,
  channel_id,
  feed_format_id,
  source_type,
  status,
  effective_at,
  timezone,
  raw_file_id,
  file_sha256,
  file_name,
  content_type,
  content_length_bytes,
  supersedes_batch_id,
  uploaded_by,
  correlation_id,
  result_hash,
  created_at,
  updated_at,
  case
    when investor_id is null or channel_id is null or feed_format_id is null then false
    when investor_id = '00000000-0000-0000-0000-000000000000'::uuid then false
    when channel_id = '00000000-0000-0000-0000-000000000000'::uuid then false
    when feed_format_id = '00000000-0000-0000-0000-000000000000'::uuid then false
    else true
  end as governance_valid
from rate_feed.rate_feed_batch;

comment on view rate_feed.rate_feed_ingestion_batch is 'Compatibility view over rate_feed.rate_feed_batch for ingestion governance naming.';
comment on view rate_feed.governance_rate_feed_batch is 'Governance-maintained view with governance_valid flag for downstream consumers';

create table if not exists rate_feed.normalization_profile (
  profile_id uuid not null,
  tenant_id uuid not null,
  name varchar(256) not null,
  format_type varchar(80) not null,
  investor_code varchar(80) not null,
  product_code varchar(80) not null,
  version integer not null,
  status varchar(40) not null,
  mapping_config jsonb,
  sample_output jsonb,
  format_fingerprint jsonb,
  created_by varchar(128) not null,
  approved_by varchar(128),
  created_at timestamptz not null default now(),
  approved_at timestamptz,
  updated_at timestamptz not null default now(),
  version_lock bigint not null default 0,
  primary key (profile_id),
  constraint normalization_profile_status_check check (status in ('DRAFT','SIMULATE','APPROVED','PUBLISHED')),
  constraint normalization_profile_version_positive check (version > 0)
);

create index if not exists normalization_profile_tenant_status_idx
  on rate_feed.normalization_profile (tenant_id, status, investor_code, product_code, version desc);

create unique index if not exists normalization_profile_tenant_name_idx
  on rate_feed.normalization_profile (tenant_id, name);

create table if not exists rate_feed.rate_sheet_rulebook_pipeline_projection (
  tenant_id uuid not null,
  sheet_id uuid not null,
  rule_book_id uuid not null,
  rate_sheet varchar(256) not null,
  investor varchar(128) not null,
  status varchar(40) not null,
  rule_count integer not null default 0,
  last_action varchar(128) not null,
  grid_hash varchar(128) not null,
  source_row_count integer not null default 0,
  warning_count integer not null default 0,
  dimensions_used jsonb not null default '[]'::jsonb,
  governance_history jsonb not null default '[]'::jsonb,
  sample_simulation jsonb not null default '{}'::jsonb,
  result_hash varchar(128) not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id, sheet_id, rule_book_id)
);

create index if not exists rate_sheet_rulebook_pipeline_projection_tenant_idx
  on rate_feed.rate_sheet_rulebook_pipeline_projection (tenant_id, updated_at desc);

create or replace function rate_feed.compute_grid_hash(p_sheet_id uuid)
returns varchar as $$
declare
  hash_input text;
begin
  select string_agg(
    rpp.note_rate::text || '|' ||
    rpp.lock_period::text || '|' ||
    rpp.base_price::text || '|' ||
    coalesce(rpp.discount_points::text, 'null') || '|' ||
    coalesce(rpp.yield_index::text, 'null'),
    ',' order by rpp.note_rate asc, rpp.lock_period asc
  ) into hash_input
  from rate_feed.rate_price_point rpp
  where rpp.sheet_id = p_sheet_id;

  return 'sha256:' || encode(digest(coalesce(hash_input, ''), 'sha256'), 'hex');
end;
$$ language plpgsql;
