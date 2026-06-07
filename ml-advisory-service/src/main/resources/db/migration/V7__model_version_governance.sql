create table if not exists ml_model_versions (
  model_version_id varchar(80) primary key,
  tenant_id uuid not null,
  model_name varchar(160) not null,
  semantic_version varchar(80) not null,
  advisory_types text not null,
  allowed_use varchar(40) not null,
  status varchar(40) not null,
  artifact_uri text not null,
  artifact_checksum varchar(160) not null,
  feature_schema_version varchar(160) not null,
  created_by varchar(128) not null,
  created_at timestamptz not null,
  approved_by varchar(128),
  approved_at timestamptz,
  retired_at timestamptz,
  lineage_json text not null default '{}',
  version int not null default 1,
  unique (tenant_id, model_name, semantic_version),
  unique (tenant_id, model_version_id),
  check (allowed_use = 'ADVISORY_ONLY'),
  check (artifact_checksum <> '')
);

create table if not exists ml_model_governance_evidence (
  evidence_id varchar(80) primary key,
  model_version_id varchar(80) not null references ml_model_versions(model_version_id),
  tenant_id uuid not null,
  evidence_type varchar(80) not null,
  uri_or_payload text not null,
  metric_json text not null default '{}',
  review_status varchar(40) not null,
  reviewed_by varchar(128),
  reviewed_at timestamptz
);

create table if not exists ml_model_status_history (
  history_id varchar(80) primary key,
  model_version_id varchar(80) not null references ml_model_versions(model_version_id),
  tenant_id uuid not null,
  before_status varchar(40) not null,
  after_status varchar(40) not null,
  actor_id varchar(128) not null,
  reason text not null,
  governance_ticket varchar(160) not null,
  correlation_id varchar(128) not null,
  changed_at timestamptz not null
);

create index if not exists idx_ml_model_versions_tenant_status_updated
  on ml_model_versions (tenant_id, status, created_at desc);

create index if not exists idx_ml_model_status_history_model
  on ml_model_status_history (tenant_id, model_version_id, changed_at desc);
