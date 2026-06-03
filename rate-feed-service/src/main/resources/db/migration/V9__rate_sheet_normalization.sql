create table rate_feed.rate_feed_normalization_job (
  tenant_id uuid not null,
  normalization_job_id uuid not null,
  batch_id uuid not null,
  profile_id uuid not null,
  profile_version varchar(128) not null,
  status varchar(40) not null,
  started_at timestamptz not null,
  completed_at timestamptz,
  entry_count integer not null default 0,
  error_count integer not null default 0,
  warning_count integer not null default 0,
  result_hash varchar(128),
  idempotency_key varchar(160),
  primary key (tenant_id, normalization_job_id)
);

create index rate_feed_normalization_job_batch_idx on rate_feed.rate_feed_normalization_job (tenant_id, batch_id, started_at desc);

create table rate_feed.normalized_rate_sheet_entry (
  tenant_id uuid not null,
  entry_id uuid not null,
  batch_id uuid not null,
  source_row_id integer not null,
  investor_id uuid not null,
  channel_id uuid not null,
  canonical_product_key varchar(160) not null,
  program_key varchar(160),
  lock_period_days integer not null,
  rate_percent numeric(9,6),
  price_points numeric(9,5),
  adjustment_type varchar(80),
  adjustment_value numeric(12,6),
  adjustment_unit varchar(80),
  effective_at timestamptz not null,
  dimensions jsonb not null default '{}'::jsonb,
  raw_attributes jsonb not null default '{}'::jsonb,
  mapping_refs jsonb not null default '{}'::jsonb,
  severity varchar(16) not null,
  message text,
  created_at timestamptz not null default now(),
  primary key (tenant_id, entry_id)
);

create index normalized_rate_sheet_entry_product_idx on rate_feed.normalized_rate_sheet_entry (tenant_id, batch_id, canonical_product_key);
create index normalized_rate_sheet_entry_lookup_idx on rate_feed.normalized_rate_sheet_entry (tenant_id, investor_id, channel_id, effective_at);
create index normalized_rate_sheet_entry_severity_idx on rate_feed.normalized_rate_sheet_entry (tenant_id, batch_id, severity);
