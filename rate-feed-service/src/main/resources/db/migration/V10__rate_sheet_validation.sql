create table rate_feed.rate_feed_validation_job (
  tenant_id uuid not null,
  validation_job_id uuid not null,
  batch_id uuid not null,
  profile_id uuid not null,
  profile_version varchar(128) not null,
  status varchar(40) not null,
  started_at timestamptz not null,
  completed_at timestamptz,
  blocking_error_count integer not null default 0,
  warning_count integer not null default 0,
  result_hash varchar(128),
  idempotency_key varchar(160),
  primary key (tenant_id, validation_job_id)
);

create index rate_feed_validation_job_batch_idx on rate_feed.rate_feed_validation_job (tenant_id, batch_id, started_at desc);

create table rate_feed.rate_feed_validation_finding (
  tenant_id uuid not null,
  finding_id uuid not null,
  validation_job_id uuid not null,
  batch_id uuid not null,
  entry_id uuid,
  severity varchar(16) not null,
  rule_code varchar(128) not null,
  rule_version varchar(64) not null default 'v1',
  field_name varchar(128),
  message_code varchar(128) not null,
  message_params jsonb not null default '{}'::jsonb,
  remediation_code varchar(128),
  source_row_number integer,
  created_at timestamptz not null default now(),
  primary key (tenant_id, finding_id)
);

create index rate_feed_validation_finding_job_idx on rate_feed.rate_feed_validation_finding (tenant_id, validation_job_id);
create index rate_feed_validation_finding_batch_idx on rate_feed.rate_feed_validation_finding (tenant_id, batch_id, severity);
create index rate_feed_validation_finding_rule_idx on rate_feed.rate_feed_validation_finding (tenant_id, validation_job_id, rule_code);
