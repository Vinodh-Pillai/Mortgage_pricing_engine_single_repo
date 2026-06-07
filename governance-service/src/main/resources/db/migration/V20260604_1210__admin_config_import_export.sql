create table if not exists admin_config_import_job (
  tenant_id uuid not null,
  import_job_id uuid not null,
  profile_id varchar(128) not null,
  profile_version varchar(128) not null,
  status varchar(64) not null,
  mode varchar(32) not null,
  artifact_type varchar(128) not null,
  source_system varchar(128) not null,
  file_name varchar(256) not null,
  file_format varchar(64) not null,
  file_hash varchar(128) not null,
  idempotency_key varchar(160) not null,
  result_hash varchar(128) not null,
  actor_id varchar(128) not null,
  started_at timestamptz not null,
  completed_at timestamptz not null,
  correlation_id varchar(128) not null,
  replay_ref varchar(128) not null,
  primary key (tenant_id, import_job_id)
);

create unique index if not exists uq_admin_config_import_idempotency
  on admin_config_import_job (tenant_id, idempotency_key);

create index if not exists idx_admin_config_import_status_time
  on admin_config_import_job (tenant_id, status, completed_at desc);

create table if not exists admin_config_import_finding (
  tenant_id uuid not null,
  finding_id uuid not null,
  import_job_id uuid not null,
  severity varchar(32) not null,
  artifact_ref varchar(256) not null,
  row_number int not null,
  field_path varchar(256) not null,
  code varchar(128) not null,
  message_key varchar(256) not null,
  remediation varchar(1024) not null,
  blocking boolean not null,
  sort_order int not null,
  primary key (tenant_id, finding_id)
);

create index if not exists idx_admin_config_import_finding_job
  on admin_config_import_finding (tenant_id, import_job_id, sort_order);

create table if not exists admin_config_import_artifact (
  tenant_id uuid not null,
  import_job_id uuid not null,
  artifact_id uuid not null,
  version_id uuid not null,
  artifact_type varchar(128) not null,
  source_path varchar(512) not null,
  payload_hash varchar(128) not null,
  provenance_json jsonb not null,
  primary key (tenant_id, import_job_id, artifact_id, version_id)
);

create table if not exists admin_config_export_job (
  tenant_id uuid not null,
  export_job_id uuid not null,
  profile_id varchar(128) not null,
  profile_version varchar(128) not null,
  status varchar(64) not null,
  artifact_refs_json jsonb not null,
  redaction_level varchar(64) not null,
  package_hash varchar(128) not null,
  manifest_json jsonb not null,
  actor_id varchar(128) not null,
  created_at timestamptz not null,
  expires_at timestamptz not null,
  downloaded_at timestamptz,
  correlation_id varchar(128) not null,
  replay_ref varchar(128) not null,
  primary key (tenant_id, export_job_id)
);

create index if not exists idx_admin_config_export_status_time
  on admin_config_export_job (tenant_id, status, created_at desc);

create index if not exists idx_admin_config_export_expiry
  on admin_config_export_job (tenant_id, expires_at);
