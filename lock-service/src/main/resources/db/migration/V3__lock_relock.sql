alter table rate_locks drop constraint if exists rate_locks_status;
alter table rate_locks add constraint rate_locks_status check (status in (
  'REQUESTED', 'PENDING_APPROVAL', 'APPROVED', 'PENDING_INVESTOR_CONFIRMATION', 'ACTIVE', 'EXPIRING_SOON',
  'EXTENSION_REQUESTED', 'EXTENSION_APPROVED', 'PENDING_INVESTOR_EXTENSION_CONFIRMATION',
  'RELOCK_REQUESTED', 'RELOCK_APPROVED', 'RELOCK_REJECTED', 'PENDING_INVESTOR_RELOCK_CONFIRMATION', 'RELOCKED',
  'INVESTOR_REJECTED', 'REJECTED', 'CANCELLED', 'EXPIRED'
));

drop index if exists rate_locks_active_quote_idx;
create unique index rate_locks_active_quote_idx on rate_locks (tenant_id, quote_id)
  where status in ('REQUESTED', 'PENDING_APPROVAL', 'APPROVED', 'PENDING_INVESTOR_CONFIRMATION', 'ACTIVE', 'EXPIRING_SOON', 'EXTENSION_REQUESTED', 'EXTENSION_APPROVED', 'PENDING_INVESTOR_EXTENSION_CONFIRMATION', 'RELOCK_REQUESTED', 'RELOCK_APPROVED', 'PENDING_INVESTOR_RELOCK_CONFIRMATION');

create table lock_relocks (
  tenant_id uuid not null,
  relock_id varchar(64) primary key,
  source_lock_id varchar(64) not null,
  replacement_lock_id varchar(64) not null,
  current_quote_id varchar(128) not null,
  status varchar(40) not null,
  source_lock_version int not null,
  requested_by varchar(128) not null,
  approved_by varchar(128),
  requested_at timestamptz not null,
  decided_at timestamptz,
  confirmed_at timestamptz,
  reason_code varchar(128) not null,
  policy_version_id varchar(128) not null,
  comparison_hash varchar(128) not null,
  idempotency_key varchar(160) not null,
  correlation_id varchar(128) not null,
  replay_ref varchar(128) not null,
  constraint lock_relocks_tenant_relock unique (tenant_id, relock_id),
  constraint lock_relocks_replacement unique (tenant_id, replacement_lock_id),
  constraint lock_relocks_status check (status in ('PREVIEWED', 'REQUESTED', 'APPROVED', 'REJECTED', 'PENDING_INVESTOR_CONFIRMATION', 'CONFIRMED', 'CANCELLED'))
);

create table relock_comparison_snapshots (
  tenant_id uuid not null,
  relock_id varchar(64) not null,
  original_terms_hash varchar(128) not null,
  current_terms_hash varchar(128) not null,
  selected_terms_hash varchar(128) not null,
  policy_version_id varchar(128) not null,
  eligibility_threshold_ref varchar(160) not null,
  benefit_ledger_ref varchar(160) not null,
  selection_mode_ref varchar(160) not null,
  waiting_period_ref varchar(160) not null,
  fee_treatment_ref varchar(160) not null,
  comparison_hash varchar(128) not null,
  created_at timestamptz not null,
  constraint relock_comparison_snapshots_tenant_relock unique (tenant_id, relock_id)
);

create table relock_decisions (
  tenant_id uuid not null,
  decision_id varchar(64) primary key,
  relock_id varchar(64) not null,
  source_lock_id varchar(64) not null,
  decision varchar(40) not null,
  decided_by varchar(128) not null,
  decided_at timestamptz not null,
  reason_codes jsonb not null,
  policy_version_id varchar(128) not null,
  idempotency_key varchar(160) not null,
  correlation_id varchar(128) not null,
  constraint relock_decisions_tenant_decision unique (tenant_id, decision_id),
  constraint relock_decisions_decision check (decision in ('APPROVE', 'REJECT'))
);

create table relock_confirmations (
  tenant_id uuid not null,
  confirmation_id varchar(64) primary key,
  relock_id varchar(64) not null,
  source_lock_id varchar(64) not null,
  replacement_lock_id varchar(64) not null,
  investor_confirmation_ref varchar(160) not null,
  confirmed_by varchar(128) not null,
  confirmed_at timestamptz not null,
  idempotency_key varchar(160) not null,
  correlation_id varchar(128) not null,
  replay_ref varchar(128) not null,
  constraint relock_confirmations_tenant_confirmation unique (tenant_id, confirmation_id)
);

create unique index lock_relocks_tenant_idempotency_idx on lock_relocks (tenant_id, idempotency_key);
create unique index lock_relocks_open_source_idx on lock_relocks (tenant_id, source_lock_id)
  where status in ('REQUESTED', 'APPROVED', 'PENDING_INVESTOR_CONFIRMATION');
create index lock_relocks_tenant_status_requested_idx on lock_relocks (tenant_id, status, requested_at desc);
create unique index relock_decisions_tenant_idempotency_idx on relock_decisions (tenant_id, idempotency_key);
create unique index relock_confirmations_tenant_idempotency_idx on relock_confirmations (tenant_id, idempotency_key);
