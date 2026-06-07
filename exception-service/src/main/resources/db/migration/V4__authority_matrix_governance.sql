create table if not exists authority_matrix_version (
  tenant_id uuid not null,
  matrix_version_id varchar(64) primary key,
  status varchar(40) not null,
  version_label varchar(128) not null,
  source_version_id varchar(64),
  validation_hash varchar(128) not null,
  approval_ticket_ref varchar(160),
  submitted_by varchar(128) not null,
  approved_by varchar(128),
  published_by varchar(128),
  effective_from timestamptz,
  audit_ref varchar(128) not null,
  outbox_event_type varchar(128) not null,
  event_hash varchar(128) not null,
  idempotency_key varchar(160) not null,
  correlation_id varchar(128) not null,
  version int not null default 1,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  unique (tenant_id, version_label),
  unique (tenant_id, idempotency_key)
);

create table if not exists authority_matrix_rule (
  tenant_id uuid not null,
  matrix_version_id varchar(64) not null,
  rule_id varchar(96) not null,
  request_type varchar(96) not null,
  conditions jsonb not null default '{}'::jsonb,
  amount_unit varchar(40) not null,
  amount_min varchar(80),
  amount_max varchar(80),
  route_template jsonb not null,
  priority int not null,
  fail_closed_reason varchar(256) not null,
  primary key (tenant_id, matrix_version_id, rule_id),
  foreign key (matrix_version_id) references authority_matrix_version (matrix_version_id)
);

create index if not exists idx_authority_matrix_version_tenant_status
  on authority_matrix_version (tenant_id, status, updated_at desc);

create index if not exists idx_authority_matrix_rule_version_priority
  on authority_matrix_rule (tenant_id, matrix_version_id, request_type, amount_unit, priority);
