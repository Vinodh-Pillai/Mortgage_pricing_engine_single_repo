create table if not exists concession_request (
  tenant_id uuid not null,
  concession_request_id varchar(64) primary key,
  quote_id varchar(128) not null,
  scenario_id varchar(128) not null,
  lock_id varchar(128),
  status varchar(40) not null,
  requested_unit varchar(40) not null,
  requested_value numeric(19, 8) not null,
  currency varchar(8),
  reason_code varchar(128) not null,
  comments_redacted text not null,
  expiration date,
  policy_version_id varchar(128) not null,
  authority_matrix_version_id varchar(128) not null,
  reason_code_version_id varchar(128) not null,
  quote_snapshot_hash varchar(128) not null,
  approval_route_snapshot jsonb not null,
  approval_route_hash varchar(128) not null,
  idempotency_key varchar(160) not null,
  created_by varchar(128) not null,
  correlation_id varchar(128) not null,
  audit_ref varchar(128) not null,
  outbox_event_type varchar(128) not null,
  request_hash varchar(128) not null,
  version int not null default 1,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  unique (tenant_id, concession_request_id),
  unique (tenant_id, idempotency_key)
);

create table if not exists concession_request_evidence (
  tenant_id uuid not null,
  concession_request_id varchar(64) not null,
  evidence_uri varchar(512) not null,
  evidence_type varchar(128) not null,
  checksum varchar(160) not null,
  uploaded_by varchar(128) not null,
  created_at timestamptz not null,
  primary key (tenant_id, concession_request_id, evidence_uri),
  foreign key (concession_request_id) references concession_request (concession_request_id)
);

create index if not exists idx_concession_request_tenant_status_updated
  on concession_request (tenant_id, status, updated_at desc);

create unique index if not exists ux_concession_request_active_quote_scope
  on concession_request (tenant_id, quote_id, scenario_id)
  where status in ('SUBMITTED', 'NEEDS_ELIGIBILITY_EXCEPTION');

create table if not exists concession_approval_decision (
  tenant_id uuid not null,
  decision_id varchar(64) primary key,
  concession_request_id varchar(64) not null,
  route_step_id varchar(128) not null,
  decision varchar(40) not null,
  decision_reason_code varchar(128) not null,
  decision_comment_redacted text not null,
  conditions jsonb not null default '{}'::jsonb,
  authority_matrix_version_id varchar(128) not null,
  actor_id varchar(128) not null,
  actor_role_refs jsonb not null,
  conflict_attestation jsonb not null,
  idempotency_key varchar(160) not null,
  correlation_id varchar(128) not null,
  audit_ref varchar(128) not null,
  outbox_event_type varchar(128) not null,
  event_hash varchar(128) not null,
  aggregate_version int not null,
  created_at timestamptz not null,
  unique (tenant_id, concession_request_id, route_step_id, actor_id, decision),
  unique (tenant_id, idempotency_key),
  foreign key (concession_request_id) references concession_request (concession_request_id)
);

create index if not exists idx_concession_approval_tenant_request_created
  on concession_approval_decision (tenant_id, concession_request_id, created_at desc);
