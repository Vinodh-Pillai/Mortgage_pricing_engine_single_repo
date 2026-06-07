create table if not exists compliance_export_job (
  id varchar(80) primary key,
  tenant_id varchar(80) not null,
  status varchar(40) not null,
  requested_by varchar(128) not null,
  approved_by varchar(128),
  template_version_ref varchar(256) not null,
  redaction_profile_ref varchar(256),
  subject_filter_json varchar(4000) not null,
  delivery_policy_ref varchar(256),
  manifest_hash varchar(128),
  artifact_count int not null default 0,
  idempotency_key varchar(160) not null,
  correlation_id varchar(128) not null,
  requested_at timestamp not null,
  updated_at timestamp not null,
  expires_at timestamp,
  unique (tenant_id, idempotency_key)
);

create index if not exists idx_compliance_export_job_tenant_status
  on compliance_export_job (tenant_id, status, updated_at);

create index if not exists idx_compliance_export_job_tenant_expires
  on compliance_export_job (tenant_id, expires_at);

create table if not exists compliance_export_artifact (
  id varchar(80) primary key,
  tenant_id varchar(80) not null,
  export_job_id varchar(80) not null,
  artifact_type varchar(80) not null,
  source_ref varchar(256) not null,
  file_ref varchar(512) not null,
  content_type varchar(128) not null,
  payload_hash varchar(128) not null,
  redaction_applied boolean not null,
  sequence int not null,
  unique (tenant_id, export_job_id, sequence)
);

create table if not exists compliance_export_access_log (
  id varchar(80) primary key,
  tenant_id varchar(80) not null,
  export_job_id varchar(80) not null,
  actor_id varchar(128) not null,
  purpose varchar(512) not null,
  correlation_id varchar(128) not null,
  accessed_at timestamp not null
);
