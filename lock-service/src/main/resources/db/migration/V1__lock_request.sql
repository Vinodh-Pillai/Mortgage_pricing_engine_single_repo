create table rate_locks (
  tenant_id uuid not null,
  lock_id varchar(64) primary key,
  request_id varchar(128) not null,
  quote_id varchar(128) not null,
  loan_id varchar(128) not null,
  scenario_hash varchar(128) not null,
  status varchar(40) not null,
  version int not null default 1,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  idempotency_key varchar(160) not null,
  correlation_id varchar(128) not null,
  lock_policy_version_id varchar(128) not null,
  request_hash varchar(128) not null,
  audit_ref varchar(128) not null,
  replay_ref varchar(128) not null,
  constraint rate_locks_tenant_lock unique (tenant_id, lock_id),
  constraint rate_locks_status check (status in ('REQUESTED', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'CANCELLED'))
);

create unique index rate_locks_tenant_idempotency_idx on rate_locks (tenant_id, idempotency_key);
create index rate_locks_tenant_status_updated_idx on rate_locks (tenant_id, status, updated_at desc);
create unique index rate_locks_active_quote_idx on rate_locks (tenant_id, quote_id)
  where status in ('REQUESTED', 'PENDING_APPROVAL', 'APPROVED');

create table lock_events (
  tenant_id uuid not null,
  event_id uuid primary key,
  event_type varchar(80) not null,
  event_key varchar(160) not null,
  payload_json jsonb not null,
  occurred_at timestamptz not null
);

create table lock_audit_snapshots (
  tenant_id uuid not null,
  audit_ref varchar(128) primary key,
  lock_id varchar(64) not null,
  action varchar(80) not null,
  replay_hash varchar(128) not null,
  payload_json jsonb not null,
  created_at timestamptz not null
);

create table lock_freshness_checks (
  check_id varchar(64) primary key,
  tenant_id uuid not null,
  quote_id varchar(128) not null,
  scenario_hash varchar(128) not null,
  policy_version varchar(128) not null,
  decision varchar(40) not null,
  reason_codes jsonb not null,
  evaluated_at timestamptz not null,
  expires_at timestamptz not null,
  result_hash varchar(128) not null,
  created_by varchar(128) not null,
  correlation_id varchar(128) not null,
  constraint lock_freshness_checks_tenant_check unique (tenant_id, check_id),
  constraint lock_freshness_checks_decision check (decision in ('FRESH', 'EXPIRES_SOON', 'STALE', 'POLICY_SUSPENDED', 'CONFIG_ERROR', 'UNKNOWN'))
);

create index lock_freshness_checks_tenant_quote_evaluated_idx on lock_freshness_checks (tenant_id, quote_id, evaluated_at desc);
create index lock_freshness_checks_tenant_result_hash_idx on lock_freshness_checks (tenant_id, result_hash);
