-- owner_story: PII-17-S08
-- purpose: observability-owned tenant rate-limit policy and decision audit metadata.
-- rollback notes: drop indexes before dropping tables if the local/dev migration runner needs a manual rollback.

create table if not exists rate_limit_policy (
  id uuid primary key,
  tenant_id uuid not null,
  policy_key varchar(120) not null,
  endpoint_group varchar(120) not null,
  principal_type varchar(40) not null,
  principal_hash varchar(140),
  algorithm varchar(40) not null,
  limit_value int not null,
  burst_value int not null default 0,
  emergency_fallback_limit int not null default 0,
  status varchar(40) not null,
  version int not null,
  effective_from timestamptz not null,
  effective_to timestamptz,
  created_by varchar(128) not null,
  approved_by varchar(128),
  created_at timestamptz not null,
  updated_at timestamptz not null,
  constraint chk_rate_limit_positive_values check (limit_value > 0 and burst_value >= 0 and emergency_fallback_limit >= 0),
  constraint chk_rate_limit_sod check (status <> 'PUBLISHED' or approved_by is not null and approved_by <> created_by)
);

create unique index if not exists uq_rate_limit_policy_tenant_key_version
  on rate_limit_policy (tenant_id, policy_key, version);

create index if not exists idx_rate_limit_policy_effective_lookup
  on rate_limit_policy (tenant_id, endpoint_group, principal_type, status, effective_from, effective_to);

create table if not exists rate_limit_decision_audit (
  id uuid primary key,
  tenant_id uuid not null,
  policy_key varchar(120) not null,
  policy_version int not null,
  principal_hash varchar(140) not null,
  endpoint_group varchar(120) not null,
  decision varchar(40) not null,
  remaining int not null,
  reset_at timestamptz not null,
  correlation_id varchar(128) not null,
  created_at timestamptz not null
);

create index if not exists idx_rate_limit_audit_tenant_created
  on rate_limit_decision_audit (tenant_id, created_at desc);

create index if not exists idx_rate_limit_audit_tenant_decision_created
  on rate_limit_decision_audit (tenant_id, decision, created_at desc);
