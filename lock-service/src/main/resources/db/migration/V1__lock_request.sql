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
  expires_at timestamptz,
  idempotency_key varchar(160) not null,
  correlation_id varchar(128) not null,
  lock_policy_version_id varchar(128) not null,
  request_hash varchar(128) not null,
  audit_ref varchar(128) not null,
  replay_ref varchar(128) not null,
  constraint rate_locks_tenant_lock unique (tenant_id, lock_id),
  constraint rate_locks_status check (status in ('REQUESTED', 'PENDING_APPROVAL', 'APPROVED', 'PENDING_INVESTOR_CONFIRMATION', 'ACTIVE', 'EXPIRING_SOON', 'INVESTOR_REJECTED', 'REJECTED', 'CANCELLED', 'EXPIRED'))
);

create unique index rate_locks_tenant_idempotency_idx on rate_locks (tenant_id, idempotency_key);
create index rate_locks_tenant_status_updated_idx on rate_locks (tenant_id, status, updated_at desc);
create unique index rate_locks_active_quote_idx on rate_locks (tenant_id, quote_id)
  where status in ('REQUESTED', 'PENDING_APPROVAL', 'APPROVED', 'PENDING_INVESTOR_CONFIRMATION', 'ACTIVE', 'EXPIRING_SOON');
create index rate_locks_tenant_status_expires_idx on rate_locks (tenant_id, status, expires_at)
  where status in ('ACTIVE', 'EXPIRING_SOON');

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

create table lock_confirmations (
  tenant_id uuid not null,
  confirmation_id varchar(64) primary key,
  lock_id varchar(64) not null,
  confirmation_type varchar(40) not null,
  lock_number varchar(128) not null,
  investor_id varchar(128),
  investor_confirmation_ref varchar(160),
  status varchar(40) not null,
  lock_version int not null,
  confirmed_at timestamptz not null,
  expires_at timestamptz not null,
  confirmed_terms_hash varchar(128) not null,
  idempotency_key varchar(160) not null,
  correlation_id varchar(128) not null,
  replay_ref varchar(128) not null,
  constraint lock_confirmations_tenant_confirmation unique (tenant_id, confirmation_id),
  constraint lock_confirmations_type check (confirmation_type in ('INTERNAL', 'INVESTOR_REQUEST', 'INVESTOR_CALLBACK')),
  constraint lock_confirmations_status check (status in ('PENDING_INVESTOR_CONFIRMATION', 'ACTIVE', 'INVESTOR_REJECTED'))
);

create table investor_confirmation_attempts (
  tenant_id uuid not null,
  attempt_id varchar(64) primary key,
  lock_id varchar(64) not null,
  confirmation_id varchar(64) not null,
  investor_id varchar(128) not null,
  external_correlation_id varchar(160) not null,
  payload_hash varchar(128) not null,
  status varchar(40) not null,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  constraint investor_confirmation_attempts_tenant_attempt unique (tenant_id, attempt_id)
);

create unique index lock_confirmations_tenant_idempotency_idx on lock_confirmations (tenant_id, idempotency_key);
create unique index lock_confirmations_active_lock_idx on lock_confirmations (tenant_id, lock_id)
  where status = 'ACTIVE';
create unique index lock_confirmations_tenant_number_idx on lock_confirmations (tenant_id, lock_number)
  where status = 'ACTIVE';
create unique index lock_confirmations_investor_ref_idx on lock_confirmations (tenant_id, investor_id, investor_confirmation_ref)
  where status = 'ACTIVE' and investor_id is not null and investor_confirmation_ref is not null;
create index lock_confirmations_tenant_status_confirmed_idx on lock_confirmations (tenant_id, status, confirmed_at desc);

create table lock_expiration_schedules (
  tenant_id uuid not null,
  lock_id varchar(64) not null,
  expires_at timestamptz not null,
  next_warning_at timestamptz,
  policy_version varchar(128) not null,
  last_evaluated_at timestamptz,
  constraint lock_expiration_schedules_tenant_lock unique (tenant_id, lock_id)
);

create index lock_expiration_schedules_tenant_warning_idx on lock_expiration_schedules (tenant_id, next_warning_at)
  where next_warning_at is not null;

create table lock_expiration_runs (
  tenant_id uuid not null,
  run_id varchar(128) primary key,
  started_at timestamptz not null,
  completed_at timestamptz not null,
  status varchar(40) not null,
  processed_count int not null,
  expiring_soon_count int not null,
  expired_count int not null,
  no_op_count int not null,
  replay_ref varchar(128) not null,
  correlation_id varchar(128) not null,
  constraint lock_expiration_runs_tenant_run unique (tenant_id, run_id)
);
