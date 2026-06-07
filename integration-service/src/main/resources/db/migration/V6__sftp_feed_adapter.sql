create table if not exists integration_sftp_adapter (
  tenant_id uuid not null,
  adapter_id varchar(128) not null,
  partner_id varchar(128) not null,
  host varchar(256) not null,
  port int not null,
  remote_path varchar(512) not null,
  file_pattern varchar(160) not null,
  credential_ref varchar(256) not null,
  known_host_fingerprint varchar(256) not null,
  feed_type varchar(80) not null,
  schema_version varchar(80) not null,
  archive_path varchar(512) not null,
  poll_schedule varchar(160) not null,
  status varchar(40) not null,
  version int not null default 1,
  created_by varchar(128) not null,
  correlation_id varchar(128) not null,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  constraint pk_integration_sftp_adapter primary key (tenant_id, adapter_id),
  constraint ck_integration_sftp_adapter_port check (port > 0 and port <= 65535)
);

create table if not exists integration_sftp_poll_run (
  tenant_id uuid not null,
  run_id uuid not null,
  adapter_id varchar(128) not null,
  status varchar(40) not null,
  discovered_count int not null default 0,
  normalized_count int not null default 0,
  archived_count int not null default 0,
  error_summary varchar(512),
  actor_id varchar(128) not null,
  correlation_id varchar(128) not null,
  started_at timestamptz not null,
  completed_at timestamptz,
  constraint pk_integration_sftp_poll_run primary key (tenant_id, run_id),
  constraint fk_integration_sftp_poll_run_adapter foreign key (tenant_id, adapter_id) references integration_sftp_adapter(tenant_id, adapter_id)
);

create table if not exists integration_sftp_file (
  tenant_id uuid not null,
  file_id uuid not null,
  adapter_id varchar(128) not null,
  remote_path_hash varchar(128) not null,
  file_name varchar(256) not null,
  size_bytes bigint not null,
  checksum varchar(128) not null,
  status varchar(40) not null,
  run_id uuid not null,
  discovered_at timestamptz not null,
  processed_at timestamptz,
  failed_at timestamptz,
  error_summary varchar(512),
  constraint pk_integration_sftp_file primary key (tenant_id, file_id),
  constraint uq_integration_sftp_file_checksum unique (tenant_id, adapter_id, checksum),
  constraint fk_integration_sftp_file_adapter foreign key (tenant_id, adapter_id) references integration_sftp_adapter(tenant_id, adapter_id),
  constraint fk_integration_sftp_file_run foreign key (tenant_id, run_id) references integration_sftp_poll_run(tenant_id, run_id)
);

create table if not exists integration_sftp_feed_record_staging (
  tenant_id uuid not null,
  file_id uuid not null,
  run_id uuid not null,
  row_number int not null,
  external_record_id varchar(256) not null,
  normalized_json jsonb not null,
  validation_status varchar(40) not null,
  reason_codes varchar(512) not null default '',
  created_at timestamptz not null default now(),
  constraint pk_integration_sftp_feed_record_staging primary key (tenant_id, file_id, run_id, row_number),
  constraint fk_integration_sftp_feed_record_file foreign key (tenant_id, file_id) references integration_sftp_file(tenant_id, file_id),
  constraint fk_integration_sftp_feed_record_run foreign key (tenant_id, run_id) references integration_sftp_poll_run(tenant_id, run_id)
);

create index if not exists idx_integration_sftp_adapter_status
  on integration_sftp_adapter (tenant_id, status, updated_at desc);

create index if not exists idx_integration_sftp_file_adapter_status
  on integration_sftp_file (tenant_id, adapter_id, status, discovered_at desc);

create index if not exists idx_integration_sftp_staging_run
  on integration_sftp_feed_record_staging (tenant_id, run_id, external_record_id);
