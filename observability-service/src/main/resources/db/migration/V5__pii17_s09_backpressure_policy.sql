-- owner_story: PII-17-S09
-- purpose: observability-owned backpressure policy and runtime state metadata.
-- rollback notes: drop indexes before dropping tables if the local/dev migration runner needs a manual rollback.

create table if not exists backpressure_policy (
  id uuid primary key,
  tenant_id uuid not null,
  resource varchar(80) not null,
  status varchar(40) not null,
  version int not null,
  effective_from timestamptz not null,
  effective_to timestamptz,
  triggers_json jsonb not null,
  recovery_windows int not null,
  minimum_state_duration_seconds int not null,
  retry_after_seconds int not null,
  created_by varchar(128) not null,
  approved_by varchar(128),
  created_at timestamptz not null,
  updated_at timestamptz not null,
  constraint chk_backpressure_positive_config check (
    version > 0
    and recovery_windows > 0
    and minimum_state_duration_seconds >= 0
    and retry_after_seconds >= 0
  ),
  constraint chk_backpressure_sod check (status <> 'PUBLISHED' or approved_by is not null and approved_by <> created_by)
);

create unique index if not exists uq_backpressure_policy_tenant_resource_version
  on backpressure_policy (tenant_id, resource, version);

create index if not exists idx_backpressure_policy_effective_lookup
  on backpressure_policy (tenant_id, resource, status, effective_from, effective_to);

create table if not exists backpressure_state (
  tenant_id uuid not null,
  resource varchar(80) not null,
  state varchar(40) not null,
  policy_version int not null,
  trigger_metric varchar(120) not null,
  trigger_value numeric not null,
  started_at timestamptz not null,
  updated_at timestamptz not null,
  expires_at timestamptz,
  correlation_id varchar(128) not null,
  primary key (tenant_id, resource)
);

create index if not exists idx_backpressure_state_tenant_state_updated
  on backpressure_state (tenant_id, state, updated_at desc);

create index if not exists idx_backpressure_state_cleanup
  on backpressure_state (expires_at);

create table if not exists backpressure_audit (
  id uuid primary key,
  tenant_id uuid not null,
  action varchar(120) not null,
  actor_id varchar(128) not null,
  before_summary varchar(160) not null,
  after_summary varchar(160) not null,
  policy_config_ref varchar(160) not null,
  correlation_id varchar(128) not null,
  replay_hash varchar(160) not null,
  created_at timestamptz not null
);

create index if not exists idx_backpressure_audit_tenant_created
  on backpressure_audit (tenant_id, created_at desc);
