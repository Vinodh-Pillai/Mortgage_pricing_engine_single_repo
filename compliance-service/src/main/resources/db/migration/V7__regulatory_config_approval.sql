create table regulatory_config_approval (
  id varchar(80) primary key,
  tenant_id varchar(80) not null,
  artifact_type varchar(80) not null,
  artifact_id varchar(120) not null,
  artifact_version varchar(80) not null,
  status varchar(40) not null,
  effective_from date,
  effective_to date,
  author_id varchar(128) not null,
  submitted_at timestamptz,
  approved_by varchar(128),
  approved_at timestamptz,
  published_by varchar(128),
  published_at timestamptz,
  artifact_hash varchar(160) not null,
  approval_package_hash varchar(160) not null,
  rollback_target_ref varchar(200),
  approval_package_json jsonb not null default '{}'::jsonb,
  validation_report_json jsonb,
  simulation_evidence_json jsonb,
  audit_ref varchar(320) not null,
  correlation_id varchar(128) not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint regulatory_config_approval_unique_artifact_version
    unique (tenant_id, artifact_type, artifact_id, artifact_version)
);

create index regulatory_config_approval_tenant_status_idx
  on regulatory_config_approval (tenant_id, status, updated_at desc);

create index regulatory_config_approval_effective_idx
  on regulatory_config_approval (tenant_id, artifact_type, effective_from, effective_to);

create table regulatory_config_approval_evidence (
  id varchar(80) primary key,
  tenant_id varchar(80) not null,
  approval_id varchar(80) not null references regulatory_config_approval(id),
  evidence_type varchar(80) not null,
  source_ref varchar(220) not null,
  payload jsonb not null default '{}'::jsonb,
  payload_hash varchar(160) not null,
  created_at timestamptz not null default now()
);

create index regulatory_config_approval_evidence_lookup_idx
  on regulatory_config_approval_evidence (tenant_id, approval_id, evidence_type);

create table regulatory_config_decision (
  id varchar(80) primary key,
  tenant_id varchar(80) not null,
  approval_id varchar(80) not null references regulatory_config_approval(id),
  actor_id varchar(128) not null,
  actor_role varchar(80) not null,
  comments varchar(2000) not null,
  previous_status varchar(40),
  new_status varchar(40) not null,
  correlation_id varchar(128) not null,
  decided_at timestamptz not null
);

create index regulatory_config_decision_approval_idx
  on regulatory_config_decision (tenant_id, approval_id, decided_at);
