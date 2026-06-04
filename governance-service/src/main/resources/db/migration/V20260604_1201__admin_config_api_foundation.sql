create table if not exists admin_config_artifact (
  tenant_id uuid not null,
  artifact_id uuid primary key,
  artifact_type varchar(64) not null,
  display_name varchar(160) not null,
  owner_group varchar(80),
  lifecycle_policy_id uuid,
  created_by varchar(128) not null,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  unique (tenant_id, artifact_type, display_name)
);

create table if not exists admin_config_version (
  tenant_id uuid not null,
  version_id uuid primary key,
  artifact_id uuid not null references admin_config_artifact(artifact_id),
  version_number int not null,
  status varchar(32) not null,
  schema_version varchar(32) not null,
  payload_json jsonb not null,
  payload_hash char(64) not null,
  context_json jsonb not null default '{}'::jsonb,
  effective_start timestamptz not null,
  effective_end timestamptz,
  change_summary text,
  etag varchar(80) not null,
  created_by varchar(128) not null,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  unique (tenant_id, artifact_id, version_number),
  check (effective_end is null or effective_end > effective_start)
);

create table if not exists admin_config_idempotency (
  tenant_id uuid not null,
  idempotency_key varchar(160) not null,
  request_hash char(64) not null,
  response_ref uuid not null,
  expires_at timestamptz,
  unique (tenant_id, idempotency_key)
);

create index if not exists idx_admin_config_artifact_tenant_type
  on admin_config_artifact (tenant_id, artifact_type);

create index if not exists idx_admin_config_version_tenant_artifact_status_effective
  on admin_config_version (tenant_id, artifact_id, status, effective_start);
