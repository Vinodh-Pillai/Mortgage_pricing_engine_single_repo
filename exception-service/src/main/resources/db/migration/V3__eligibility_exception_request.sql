create table if not exists eligibility_exception_request (
  tenant_id uuid not null,
  exception_request_id varchar(64) primary key,
  quote_id varchar(128) not null,
  scenario_id varchar(128) not null,
  lock_id varchar(128),
  eligibility_result_id varchar(128) not null,
  finding_id varchar(128) not null,
  rule_code varchar(128) not null,
  rule_version_id varchar(128) not null,
  severity varchar(40) not null,
  exception_scope jsonb not null,
  reason_code varchar(128) not null,
  compensating_factors jsonb not null default '[]'::jsonb,
  status varchar(40) not null,
  policy_version_id varchar(128) not null,
  authority_matrix_version_id varchar(128) not null,
  approval_route_snapshot jsonb not null,
  related_concession_request_id varchar(64),
  idempotency_key varchar(160) not null,
  original_result_hash varchar(128) not null,
  audit_ref varchar(128) not null,
  outbox_event_type varchar(128) not null,
  event_hash varchar(128) not null,
  request_hash varchar(128) not null,
  created_by varchar(128) not null,
  correlation_id varchar(128) not null,
  version int not null default 1,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  unique (tenant_id, idempotency_key),
  foreign key (related_concession_request_id) references concession_request (concession_request_id)
);

create table if not exists eligibility_exception_evidence (
  tenant_id uuid not null,
  exception_request_id varchar(64) not null,
  evidence_uri varchar(512) not null,
  evidence_type varchar(128) not null,
  checksum varchar(128) not null,
  uploaded_by varchar(128) not null,
  uploaded_at timestamptz not null,
  primary key (tenant_id, exception_request_id, evidence_uri),
  foreign key (exception_request_id) references eligibility_exception_request (exception_request_id)
);

create unique index if not exists ux_eligibility_exception_active_finding_scope
  on eligibility_exception_request (tenant_id, eligibility_result_id, finding_id, rule_code, rule_version_id, exception_scope)
  where status in ('DRAFT', 'SUBMITTED');

create index if not exists idx_eligibility_exception_tenant_status
  on eligibility_exception_request (tenant_id, status, updated_at desc);

create index if not exists idx_eligibility_exception_related_concession
  on eligibility_exception_request (tenant_id, related_concession_request_id)
  where related_concession_request_id is not null;
