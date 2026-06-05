create table rate_feed.investor_feed_integration (
  tenant_id uuid not null,
  integration_id uuid not null,
  investor_id uuid not null,
  channel_id uuid not null,
  feed_format_id uuid not null,
  tenant_external_key varchar(128) not null,
  investor_external_key varchar(128) not null,
  channel_external_key varchar(128) not null,
  feed_format varchar(64) not null,
  schema_version varchar(64) not null,
  auth_subject_hash varchar(128),
  active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id, integration_id),
  unique (tenant_external_key, investor_external_key, channel_external_key, feed_format)
);

create index investor_feed_integration_external_idx on rate_feed.investor_feed_integration (tenant_external_key, investor_external_key, channel_external_key, feed_format) where active = true;

create table rate_feed.investor_feed_submission (
  tenant_id uuid not null,
  submission_id uuid not null,
  batch_id uuid not null,
  tenant_external_key varchar(128) not null,
  investor_external_key varchar(128) not null,
  channel_external_key varchar(128) not null,
  feed_format varchar(64) not null,
  schema_version varchar(64) not null,
  submission_kind varchar(32) not null,
  status varchar(32) not null,
  file_name varchar(255) not null,
  content_type varchar(128) not null,
  content_length_bytes bigint not null,
  request_hash varchar(128) not null,
  result_hash varchar(128) not null,
  created_by varchar(128) not null,
  correlation_id varchar(128) not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id, submission_id),
  unique (tenant_id, request_hash)
);

create index investor_feed_submission_status_idx on rate_feed.investor_feed_submission (tenant_id, status, created_at desc);
create index investor_feed_submission_external_idx on rate_feed.investor_feed_submission (tenant_external_key, submission_id);
