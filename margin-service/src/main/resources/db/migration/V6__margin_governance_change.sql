create table if not exists margin_governance_change (
  tenant_id varchar(64) not null,
  change_id varchar(64) not null,
  target_type varchar(80) not null,
  target_id varchar(128) not null,
  target_version_id varchar(128) not null,
  expected_version int not null,
  status varchar(40) not null,
  risk_tier varchar(40) not null,
  config_hash varchar(128) not null,
  diff_hash varchar(128) not null,
  simulation_hash varchar(128),
  submitter_id varchar(128) not null,
  publisher_id varchar(128),
  rollback_source_change_id varchar(64),
  request_json clob not null,
  result_json clob not null,
  created_at timestamp not null,
  updated_at timestamp not null,
  published_at timestamp,
  correlation_id varchar(128) not null,
  idempotency_key varchar(160),
  primary key (tenant_id, change_id)
);

create unique index if not exists ux_margin_governance_change_idempotency
  on margin_governance_change (tenant_id, idempotency_key);

create index if not exists idx_margin_governance_change_status
  on margin_governance_change (tenant_id, status, updated_at);

create table if not exists margin_governance_approval (
  tenant_id varchar(64) not null,
  approval_id varchar(64) not null,
  change_id varchar(64) not null,
  step varchar(80) not null,
  decision varchar(40) not null,
  actor_id varchar(128) not null,
  comments clob,
  evidence_refs clob not null,
  decided_at timestamp not null,
  primary key (tenant_id, approval_id)
);

create index if not exists idx_margin_governance_approval_change
  on margin_governance_approval (tenant_id, change_id, step);
