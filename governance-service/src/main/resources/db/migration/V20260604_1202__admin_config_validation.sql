create table if not exists admin_config_validation_run (
  tenant_id uuid not null,
  run_id uuid primary key,
  artifact_id uuid not null,
  version_id uuid not null,
  run_type varchar(32) not null,
  status varchar(32) not null,
  input_hash char(64) not null,
  policy_version_set_hash char(64) not null,
  result_hash char(64) not null,
  started_at timestamptz not null,
  completed_at timestamptz not null,
  actor_id varchar(128) not null,
  audit_ref uuid not null,
  replay_ref uuid not null,
  correlation_id varchar(128) not null,
  publish_eligible boolean not null,
  unique (tenant_id, run_id)
);

create table if not exists admin_config_validation_finding (
  tenant_id uuid not null,
  finding_id uuid primary key,
  run_id uuid not null references admin_config_validation_run(run_id),
  severity varchar(16) not null,
  code varchar(80) not null,
  json_path varchar(240) not null,
  artifact_ref varchar(160) not null,
  message_key varchar(160) not null,
  message_params jsonb not null default '{}'::jsonb,
  remediation text,
  blocking boolean not null,
  sort_order int not null
);

create index if not exists idx_admin_config_validation_run_artifact
  on admin_config_validation_run (tenant_id, artifact_id, version_id, completed_at desc);

create index if not exists idx_admin_config_validation_finding_severity
  on admin_config_validation_finding (tenant_id, run_id, severity);
