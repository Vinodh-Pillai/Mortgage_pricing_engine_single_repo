create table if not exists what_if_guardrail_policy (
  tenant_id varchar(128) not null,
  policy_id uuid primary key,
  status varchar(40) not null,
  version int not null default 1,
  rules_json jsonb not null default '[]'::jsonb,
  created_by varchar(128) not null,
  approved_by varchar(128),
  published_by varchar(128),
  created_at timestamptz not null,
  updated_at timestamptz not null,
  constraint what_if_guardrail_policy_sod check (approved_by is null or approved_by <> created_by),
  constraint what_if_guardrail_policy_status check (status in ('DRAFT', 'VALIDATED', 'APPROVED', 'PUBLISHED', 'ROLLED_BACK', 'RETIRED'))
);

create index if not exists idx_what_if_guardrail_policy_tenant_status
  on what_if_guardrail_policy (tenant_id, status, updated_at desc);

create table if not exists what_if_guardrail_rule (
  tenant_id varchar(128) not null,
  rule_id uuid primary key,
  policy_id uuid not null references what_if_guardrail_policy(policy_id),
  rule_code varchar(128) not null,
  action varchar(64) not null,
  severity varchar(16) not null,
  condition_json jsonb not null default '{}'::jsonb,
  message_key varchar(160) not null,
  effective_from timestamptz,
  effective_to timestamptz,
  published_by varchar(128),
  approved_by varchar(128),
  version int not null default 1,
  constraint what_if_guardrail_rule_severity check (severity in ('ALLOW', 'WARN', 'BLOCK'))
);

create index if not exists idx_what_if_guardrail_rule_policy
  on what_if_guardrail_rule (tenant_id, policy_id, action);

create table if not exists what_if_guardrail_decision (
  tenant_id varchar(128) not null,
  decision_id uuid primary key,
  action varchar(64) not null,
  severity varchar(16) not null,
  policy_id uuid,
  policy_version int not null default 0,
  decision_json jsonb not null default '{}'::jsonb,
  actor_id varchar(128) not null,
  correlation_id varchar(128),
  decided_at timestamptz not null,
  constraint what_if_guardrail_decision_severity check (severity in ('ALLOW', 'WARN', 'BLOCK'))
);

create index if not exists idx_what_if_guardrail_decision_tenant_action
  on what_if_guardrail_decision (tenant_id, action, decided_at desc);

create table if not exists what_if_guardrail_exception (
  tenant_id varchar(128) not null,
  exception_id uuid primary key,
  decision_id uuid references what_if_guardrail_decision(decision_id),
  status varchar(40) not null,
  requested_by varchar(128) not null,
  approved_by varchar(128),
  expires_at timestamptz,
  request_json jsonb not null default '{}'::jsonb,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  constraint what_if_guardrail_exception_sod check (approved_by is null or approved_by <> requested_by)
);

create index if not exists idx_what_if_guardrail_exception_tenant_status
  on what_if_guardrail_exception (tenant_id, status, updated_at desc);
