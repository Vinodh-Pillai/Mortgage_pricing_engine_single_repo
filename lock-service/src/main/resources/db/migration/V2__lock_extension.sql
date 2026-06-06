alter table rate_locks drop constraint if exists rate_locks_status;
alter table rate_locks add constraint rate_locks_status check (status in (
  'REQUESTED', 'PENDING_APPROVAL', 'APPROVED', 'PENDING_INVESTOR_CONFIRMATION', 'ACTIVE', 'EXPIRING_SOON',
  'EXTENSION_REQUESTED', 'EXTENSION_APPROVED', 'PENDING_INVESTOR_EXTENSION_CONFIRMATION',
  'INVESTOR_REJECTED', 'REJECTED', 'CANCELLED', 'EXPIRED'
));

drop index if exists rate_locks_active_quote_idx;
create unique index rate_locks_active_quote_idx on rate_locks (tenant_id, quote_id)
  where status in ('REQUESTED', 'PENDING_APPROVAL', 'APPROVED', 'PENDING_INVESTOR_CONFIRMATION', 'ACTIVE', 'EXPIRING_SOON', 'EXTENSION_REQUESTED', 'EXTENSION_APPROVED', 'PENDING_INVESTOR_EXTENSION_CONFIRMATION');

create table lock_extensions (
  tenant_id uuid not null,
  extension_id varchar(64) primary key,
  lock_id varchar(64) not null,
  status varchar(40) not null,
  lock_version int not null,
  requested_days int not null,
  previous_expires_at timestamptz not null,
  requested_expires_at timestamptz not null,
  reason_code varchar(128) not null,
  requested_by varchar(128) not null,
  approved_by varchar(128),
  requested_at timestamptz not null,
  decided_at timestamptz,
  confirmed_at timestamptz,
  policy_version_id varchar(128) not null,
  cost_snapshot_hash varchar(128) not null,
  idempotency_key varchar(160) not null,
  correlation_id varchar(128) not null,
  replay_ref varchar(128) not null,
  constraint lock_extensions_tenant_extension unique (tenant_id, extension_id),
  constraint lock_extensions_status check (status in ('PREVIEWED', 'REQUESTED', 'APPROVED', 'REJECTED', 'PENDING_INVESTOR_CONFIRMATION', 'CONFIRMED', 'CANCELLED')),
  constraint lock_extensions_positive_days check (requested_days > 0),
  constraint lock_extensions_expires_after_previous check (requested_expires_at > previous_expires_at)
);

create table lock_extension_cost_snapshots (
  tenant_id uuid not null,
  extension_id varchar(64) not null,
  price_adjustment_ref varchar(160) not null,
  fee_amount_ref varchar(160) not null,
  payer_type varchar(80) not null,
  rounding_mode varchar(80) not null,
  reason_code varchar(128) not null,
  policy_version_id varchar(128) not null,
  cost_snapshot_hash varchar(128) not null,
  created_at timestamptz not null,
  constraint lock_extension_cost_snapshots_tenant_extension unique (tenant_id, extension_id)
);

create table lock_extension_decisions (
  tenant_id uuid not null,
  decision_id varchar(64) primary key,
  extension_id varchar(64) not null,
  lock_id varchar(64) not null,
  decision varchar(40) not null,
  decided_by varchar(128) not null,
  decided_at timestamptz not null,
  reason_codes jsonb not null,
  policy_version_id varchar(128) not null,
  idempotency_key varchar(160) not null,
  correlation_id varchar(128) not null,
  constraint lock_extension_decisions_tenant_decision unique (tenant_id, decision_id),
  constraint lock_extension_decisions_decision check (decision in ('APPROVE', 'REJECT'))
);

create table lock_extension_confirmations (
  tenant_id uuid not null,
  confirmation_id varchar(64) primary key,
  extension_id varchar(64) not null,
  lock_id varchar(64) not null,
  investor_confirmation_ref varchar(160) not null,
  confirmed_by varchar(128) not null,
  confirmed_at timestamptz not null,
  idempotency_key varchar(160) not null,
  correlation_id varchar(128) not null,
  replay_ref varchar(128) not null,
  constraint lock_extension_confirmations_tenant_confirmation unique (tenant_id, confirmation_id)
);

create unique index lock_extensions_tenant_idempotency_idx on lock_extensions (tenant_id, idempotency_key);
create unique index lock_extensions_open_lock_idx on lock_extensions (tenant_id, lock_id)
  where status in ('REQUESTED', 'APPROVED', 'PENDING_INVESTOR_CONFIRMATION');
create index lock_extensions_tenant_status_requested_idx on lock_extensions (tenant_id, status, requested_at desc);
create unique index lock_extension_decisions_tenant_idempotency_idx on lock_extension_decisions (tenant_id, idempotency_key);
create unique index lock_extension_confirmations_tenant_idempotency_idx on lock_extension_confirmations (tenant_id, idempotency_key);
