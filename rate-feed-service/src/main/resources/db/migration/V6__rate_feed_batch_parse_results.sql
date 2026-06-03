create table rate_feed.rate_feed_parse_job (
  tenant_id uuid not null,
  parse_job_id uuid not null,
  batch_id uuid not null,
  mapping_version varchar(128) not null,
  status varchar(32) not null,
  started_at timestamptz not null,
  completed_at timestamptz,
  row_count integer not null default 0,
  error_count integer not null default 0,
  warning_count integer not null default 0,
  idempotency_key varchar(160),
  result_hash varchar(128),
  primary key (tenant_id, parse_job_id)
);

create index rate_feed_parse_job_batch_idx on rate_feed.rate_feed_parse_job (tenant_id, batch_id, started_at desc);

create table rate_feed.rate_feed_raw_row (
  tenant_id uuid not null,
  batch_id uuid not null,
  row_id uuid not null,
  source_row_number integer not null,
  raw_row_sha256 varchar(128) not null,
  raw_cells jsonb not null,
  created_at timestamptz not null default now(),
  primary key (tenant_id, batch_id, row_id)
);

create index rate_feed_raw_row_source_idx on rate_feed.rate_feed_raw_row (tenant_id, batch_id, source_row_number);

create table rate_feed.rate_feed_parsed_field (
  tenant_id uuid not null,
  batch_id uuid not null,
  row_id uuid not null,
  field_name varchar(128) not null,
  raw_value text,
  candidate_value text,
  source_column integer not null,
  severity varchar(16) not null,
  error_code varchar(80),
  message text,
  source_row_number integer not null,
  created_at timestamptz not null default now(),
  primary key (tenant_id, batch_id, row_id, field_name)
);

create index rate_feed_parsed_field_severity_idx on rate_feed.rate_feed_parsed_field (tenant_id, batch_id, severity);
