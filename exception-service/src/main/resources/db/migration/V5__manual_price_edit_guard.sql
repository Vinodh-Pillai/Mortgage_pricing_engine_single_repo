create table if not exists price_mutation_guard_policy_version (
  tenant_id uuid not null,
  policy_version_id varchar(96) primary key,
  status varchar(40) not null,
  guarded_fields jsonb not null,
  allowed_command_types jsonb not null,
  workflow_capability_refs jsonb not null,
  effective_from timestamptz not null,
  created_at timestamptz not null,
  updated_at timestamptz not null
);

create table if not exists manual_price_edit_attempt (
  tenant_id uuid not null,
  attempt_id varchar(64) primary key,
  actor_id varchar(128) not null,
  source_surface varchar(96) not null,
  target_type varchar(64) not null,
  quote_id varchar(128) not null,
  lock_id varchar(128),
  field_names jsonb not null,
  payload_hash varchar(128) not null,
  denial_reason varchar(80) not null,
  policy_version_id varchar(96) not null,
  audit_ref varchar(128) not null,
  outbox_event_type varchar(128) not null,
  event_hash varchar(128) not null,
  idempotency_key varchar(160) not null,
  correlation_id varchar(128) not null,
  created_at timestamptz not null,
  unique (tenant_id, idempotency_key),
  foreign key (policy_version_id) references price_mutation_guard_policy_version (policy_version_id)
);

create index if not exists idx_manual_price_edit_attempt_tenant_actor_date
  on manual_price_edit_attempt (tenant_id, actor_id, created_at desc);

create index if not exists idx_manual_price_edit_attempt_tenant_target
  on manual_price_edit_attempt (tenant_id, target_type, quote_id, lock_id, created_at desc);
