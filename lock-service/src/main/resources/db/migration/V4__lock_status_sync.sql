create table lock_sync_targets (
  tenant_id uuid not null,
  target_id varchar(128) not null,
  system varchar(128) not null,
  enabled boolean not null,
  contract_version varchar(128) not null,
  policy_version varchar(128) not null,
  constraint lock_sync_targets_tenant_target unique (tenant_id, target_id)
);

create table lock_sync_attempts (
  tenant_id uuid not null,
  attempt_id varchar(64) primary key,
  lock_id varchar(64) not null,
  event_id varchar(160) not null,
  target_id varchar(128) not null,
  status varchar(40) not null,
  payload_hash varchar(128) not null,
  retry_count int not null default 0,
  next_retry_at timestamptz,
  ack_ref varchar(160),
  correlation_id varchar(128) not null,
  policy_version varchar(128) not null,
  contract_version varchar(128) not null,
  updated_at timestamptz not null,
  constraint lock_sync_attempts_tenant_attempt unique (tenant_id, attempt_id),
  constraint lock_sync_attempts_event_target unique (tenant_id, event_id, target_id),
  constraint lock_sync_attempts_status check (status in ('PENDING', 'SENT', 'ACKED', 'FAILED', 'DLQ', 'RECONCILED'))
);

create table lock_sync_acknowledgements (
  tenant_id uuid not null,
  ack_id varchar(64) primary key,
  lock_id varchar(64) not null,
  event_id varchar(160) not null,
  target_id varchar(128) not null,
  ack_status varchar(40) not null,
  ack_ref varchar(160) not null,
  payload_hash varchar(128) not null,
  received_at timestamptz not null,
  correlation_id varchar(128) not null,
  constraint lock_sync_acknowledgements_tenant_ack unique (tenant_id, ack_id),
  constraint lock_sync_acknowledgements_status check (ack_status in ('ACKED', 'RECONCILED'))
);

create table lock_reconciliation_records (
  tenant_id uuid not null,
  record_id varchar(64) primary key,
  lock_id varchar(64) not null,
  target_system varchar(128) not null,
  drift_type varchar(128) not null,
  resolution varchar(128) not null,
  actor_id varchar(128) not null,
  reconciled_at timestamptz not null,
  replay_ref varchar(128) not null,
  correlation_id varchar(128) not null,
  constraint lock_reconciliation_records_tenant_record unique (tenant_id, record_id)
);

create index lock_sync_attempts_status_retry_idx on lock_sync_attempts (tenant_id, status, next_retry_at);
create index lock_sync_attempts_lock_status_idx on lock_sync_attempts (tenant_id, lock_id, status, updated_at desc);
