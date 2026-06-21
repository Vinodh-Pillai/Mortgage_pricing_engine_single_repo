-- ============================================================
-- V4: Rate sheet domain tables (parser, activation, resolution)
-- ============================================================

-- ============================================================
-- rate_sheet: canonical rate sheet metadata
-- ============================================================
create table rate_feed.rate_sheet (
  sheet_id                     uuid            not null,
  tenant_id                      uuid            not null,
  investor_id                  uuid            not null,
  channel_id                   uuid            not null,
  product_code                 varchar(32)     not null,
  version                      integer         not null,
  status                       varchar(16)     not null default 'DRAFT',
  effective_at                   timestamptz     not null,
  effective_until                timestamptz,
  file_sha256                  varchar(64)     not null,
  grid_hash                      varchar(64),
  grid_points                  jsonb,
  row_count                      integer         not null default 0,
  result_hash                    varchar(128)    not null,
  created_at                     timestamptz     not null default now(),
  created_by                     varchar(128)    not null,
  activated_at                   timestamptz,
  activated_by                   varchar(128),
  rejected_at                    timestamptz,
  rejected_by                    varchar(128),
  rejection_reason             text,
  updated_at                     timestamptz     not null default now(),

  primary key (tenant_id, sheet_id),
  constraint rate_sheet_status_check check (
    status in ('DRAFT', 'PARSING', 'VALIDATED', 'ACTIVE', 'SUPERSEDED', 'REJECTED')
  ),
  constraint rate_sheet_file_hash check (
    file_sha256 ~ '^[0-9a-fA-F]{64}$'
  ),
  constraint rate_sheet_grid_hash check (
    grid_hash is null or grid_hash ~ '^sha256:[0-9a-fA-F]{64}$'
  ),
  constraint rate_sheet_version_positive check (version > 0)
);

-- Unique: version never reused for same (investor, channel, product)
create unique index rate_sheet_version_unique
  on rate_feed.rate_sheet (tenant_id, investor_id, channel_id, product_code, version);

-- Sheet IDs are generated UUIDs and used as global references by downstream domain tables.
create unique index rate_sheet_sheet_id_unique
  on rate_feed.rate_sheet (sheet_id);

create unique index rate_sheet_sheet_id_version_unique
  on rate_feed.rate_sheet (sheet_id, version);

-- Resolution query index (PII-05 primary path)
create index rate_sheet_resolve_idx
  on rate_feed.rate_sheet (tenant_id, investor_id, channel_id, product_code)
  where status = 'ACTIVE';

-- Version ordering index
create index rate_sheet_version_idx
  on rate_feed.rate_sheet (tenant_id, investor_id, channel_id, product_code, version desc);

-- Metadata filter index
create index rate_sheet_status_filter_idx
  on rate_feed.rate_sheet (tenant_id, status);

comment on table rate_feed.rate_sheet is 'Canonical rate sheet with versioned lifecycle';
comment on column rate_feed.rate_sheet.status is 'DRAFT|PARSING|VALIDATED|ACTIVE|SUPERSEDED|REJECTED';


-- ============================================================
-- rate_sheet_version: version lineage
-- ============================================================
create table rate_feed.rate_sheet_version (
  sheet_id                      uuid            not null,
  version                       integer         not null,
  previous_version             integer,
  superseded_by                integer,
  activation_audit_id          uuid,
  delta_summary                 jsonb,
  created_at                    timestamptz     not null default now(),

  primary key (sheet_id, version),
  foreign key (sheet_id) references rate_feed.rate_sheet(sheet_id)
    on delete cascade,
  foreign key (sheet_id, previous_version) references rate_feed.rate_sheet(sheet_id, version)
    on delete set null
);

comment on table rate_feed.rate_sheet_version is 'Version lineage for supersession chain';


-- ============================================================
-- rate_price_point: atomic pricing grid points
-- ============================================================
create table rate_feed.rate_price_point (
  sheet_id                      uuid            not null,
  note_rate                      numeric(10, 5)  not null,
  lock_period                    integer         not null,
  base_price                     numeric(10, 2)  not null,
  discount_points              numeric(10, 4),
  yield_index                   numeric(10, 5),
  grid_position                  integer         not null,

  primary key (sheet_id, note_rate, lock_period),
  foreign key (sheet_id) references rate_feed.rate_sheet(sheet_id)
    on delete cascade,
  constraint rpp_base_price_positive check (base_price >= 0),
  constraint rpp_note_rate_positive check (note_rate > 0),
  constraint rpp_lock_period_positive check (lock_period > 0)
);

-- Lookup index for resolution queries
create index rate_price_point_lookup_idx
  on rate_feed.rate_price_point (sheet_id, note_rate, lock_period);

-- Covering index for grid retrieval
create index rate_price_point_grid_idx
  on rate_feed.rate_price_point (sheet_id)
  include (note_rate, lock_period, base_price, discount_points);

comment on table rate_feed.rate_price_point is 'Atomic price points for base pricing grid';
comment on column rate_feed.rate_price_point.base_price is 'Price in basis points (0.01% resolution)';


-- ============================================================
-- activation_audit: immutable activation events
-- ============================================================
create table rate_feed.activation_audit (
  audit_id                       uuid            not null,
  sheet_id                      uuid            not null,
  version                       integer         not null,
  actor_id                      varchar(128)    not null,
  correlation_id                varchar(128)    not null,
  activated_at                   timestamptz     not null default now(),
  approval_reference           varchar(256),
  grid_hash_before              varchar(64),
  grid_hash_after               varchar(64)     not null,
  notes                       text,

  primary key (audit_id),
  foreign key (sheet_id, version) references rate_feed.rate_sheet_version(sheet_id, version)
    on delete cascade,
  constraint audit_after_hash check (
    grid_hash_after ~ '^sha256:[0-9a-fA-F]{64}$'
  )
);

create index activation_audit_sheet_idx
  on rate_feed.activation_audit (sheet_id, version desc);

comment on table rate_feed.activation_audit is 'Immutable activation event audit trail';


-- ============================================================
-- rejection: rejection records (audit trail)
-- ============================================================
create table rate_feed.rejection (
  sheet_id                      uuid            not null,
  version                       integer         not null,
  rejected_at                  timestamptz     not null default now(),
  rejected_by                   varchar(128)    not null,
  reason                       text            not null,
  validation_errors           jsonb,

  primary key (sheet_id, version),
  foreign key (sheet_id, version) references rate_feed.rate_sheet_version(sheet_id, version)
    on delete cascade
);

comment on table rate_feed.rejection is 'Rejection records for VALIDATED -> REJECTED sheets';


-- ============================================================
-- Modify: rate_feed.rate_feed_batch
-- ============================================================

-- V-001: Add status check constraint (enum enforcement)
alter table rate_feed.rate_feed_batch
  drop constraint if exists rate_feed_batch_status_check;

alter table rate_feed.rate_feed_batch
  add constraint rate_feed_batch_status_check check (
    status in ('UPLOADED', 'PARSING', 'VALIDATED', 'ACTIVE', 'SUPERSEDED', 'REJECTED')
  );

-- Add sheet_id FK and grid_hash to batch
alter table rate_feed.rate_feed_batch
  add column sheet_id uuid,
  add column grid_hash varchar(64);

create index rate_feed_batch_sheet_idx
  on rate_feed.rate_feed_batch (sheet_id)
  where sheet_id is not null;

comment on column rate_feed.rate_feed_batch.sheet_id is 'FK to rate_sheet.sheet_id; NULL for legacy unparsed batches';
comment on column rate_feed.rate_feed_batch.grid_hash is 'SHA-256 of parsed grid; NULL for unparsed batches';


-- ============================================================
-- Function: compute deterministic grid hash
-- ============================================================
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

  if hash_input is null then
    return 'sha256:' || encode(sha256(''), 'hex');
  end if;

  return 'sha256:' || encode(sha256(hash_input::bytea), 'hex');
end;
$$ language plpgsql;

comment on function rate_feed.compute_grid_hash is 'Deterministic grid hash sorted by noteRate ASC, lockPeriod ASC';
