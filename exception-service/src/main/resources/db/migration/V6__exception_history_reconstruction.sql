create table if not exists exception_history_projection (
  tenant_id uuid not null,
  projection_id varchar(80) primary key,
  subject_type varchar(40) not null,
  subject_id varchar(128) not null,
  timeline jsonb not null,
  version_graph jsonb not null,
  latest_event_at timestamptz not null,
  projection_hash varchar(128) not null,
  rebuilt_at timestamptz not null
);

create index if not exists idx_exception_history_projection_subject
  on exception_history_projection (tenant_id, subject_type, subject_id);

create table if not exists exception_replay_result (
  tenant_id uuid not null,
  replay_id varchar(80) primary key,
  subject_type varchar(40) not null,
  subject_id varchar(128) not null,
  input_event_ids jsonb not null,
  config_version_ids jsonb not null,
  expected_hash varchar(128) not null,
  actual_hash varchar(128) not null,
  status varchar(40) not null,
  mismatch_classification varchar(80) not null,
  created_by varchar(128) not null,
  created_at timestamptz not null
);

create table if not exists exception_history_export (
  tenant_id uuid not null,
  export_id varchar(80) primary key,
  subject_refs jsonb not null,
  manifest_hash varchar(128) not null,
  storage_ref varchar(256) not null,
  status varchar(40) not null,
  requested_by varchar(128) not null,
  created_at timestamptz not null,
  expires_at timestamptz not null
);

create index if not exists idx_exception_history_export_subject
  on exception_history_export (tenant_id, requested_by, created_at desc);
