create table if not exists admin_config_lifecycle_transition (
  tenant_id uuid not null,
  transition_id uuid primary key,
  artifact_id uuid not null,
  version_id uuid not null,
  from_status varchar(40) not null,
  to_status varchar(40) not null,
  action varchar(40) not null,
  reason_code varchar(80) not null,
  actor_id varchar(128) not null,
  correlation_id varchar(128) not null,
  created_at timestamptz not null,
  policy_version_id varchar(128) not null
);

create table if not exists admin_config_approval_request (
  tenant_id uuid not null,
  approval_request_id uuid primary key,
  artifact_id uuid not null,
  version_id uuid not null,
  status varchar(32) not null,
  required_policy_json jsonb not null,
  submitted_by varchar(128) not null,
  submitted_at timestamptz not null,
  expires_at timestamptz
);

create table if not exists admin_config_approval_decision (
  tenant_id uuid not null,
  decision_id uuid primary key,
  approval_request_id uuid not null references admin_config_approval_request(approval_request_id),
  decision varchar(32) not null,
  approver_id varchar(128) not null,
  approver_group varchar(128) not null,
  reason_code varchar(80) not null,
  comments text,
  decided_at timestamptz not null
);

create table if not exists admin_config_publish_schedule (
  tenant_id uuid not null,
  schedule_id uuid primary key,
  artifact_id uuid not null,
  version_id uuid not null,
  requested_effective_start timestamptz not null,
  requested_effective_end timestamptz,
  status varchar(32) not null,
  scheduled_by varchar(128) not null,
  scheduled_at timestamptz not null,
  executed_at timestamptz
);

create unique index if not exists uq_admin_config_approval_request_open
  on admin_config_approval_request (tenant_id, version_id)
  where status = 'OPEN';

create index if not exists idx_admin_config_lifecycle_transition_version
  on admin_config_lifecycle_transition (tenant_id, artifact_id, version_id, created_at desc);

create index if not exists idx_admin_config_publish_schedule_window
  on admin_config_publish_schedule (tenant_id, artifact_id, requested_effective_start, requested_effective_end);
