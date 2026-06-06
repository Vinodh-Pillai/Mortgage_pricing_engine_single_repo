create schema if not exists scenario;

create table if not exists scenario.scenario (
  tenant_id uuid not null,
  scenario_id uuid,
  lineage_id uuid not null,
  version int not null,
  status varchar(40) not null,
  quote_intent varchar(40) not null,
  channel varchar(40) not null,
  scenario_name varchar(120),
  external_loan_id varchar(128),
  source_system varchar(40) not null,
  raw_facts_json jsonb not null default '{}'::jsonb,
  normalized_facts_json jsonb not null default '{}'::jsonb,
  derived_fields_json jsonb not null default '{}'::jsonb,
  replay_hash varchar(128) not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id, scenario_id)
);

create table if not exists scenario.scenario_version (
  tenant_id uuid not null,
  scenario_id uuid,
  version int not null,
  reason varchar(80) not null,
  replay_hash varchar(128) not null,
  snapshot_json jsonb not null,
  created_at timestamptz not null default now(),
  primary key (tenant_id, scenario_id, version)
);

create table if not exists scenario.scenario_validation_issue (
  tenant_id uuid not null,
  scenario_id uuid not null,
  version int not null,
  code varchar(80) not null,
  field_path varchar(240) not null,
  severity varchar(20) not null,
  message text not null,
  created_at timestamptz not null default now()
);

create table if not exists scenario.scenario_idempotency_record (
  tenant_id uuid not null,
  idempotency_key varchar(160) not null,
  request_hash varchar(128) not null,
  response_type varchar(160) not null default 'ScenarioResponse',
  response_json jsonb not null,
  created_at timestamptz not null default now(),
  primary key (tenant_id, idempotency_key)
);

create table if not exists scenario.scenario_outbox_event (
  tenant_id uuid not null,
  event_id uuid primary key,
  scenario_id uuid not null,
  event_type varchar(120) not null,
  event_version int not null,
  correlation_id varchar(128) not null,
  payload_json jsonb not null,
  occurred_at timestamptz not null default now(),
  published_at timestamptz
);

create table if not exists scenario.scenario_audit_record (
  tenant_id uuid not null,
  audit_package_id uuid primary key,
  scenario_id uuid not null,
  action varchar(120) not null,
  correlation_id varchar(128) not null,
  replay_hash varchar(128) not null,
  occurred_at timestamptz not null default now()
);

create index if not exists scenario_idx_tenant_status_updated on scenario.scenario (tenant_id, status, updated_at desc);
create index if not exists scenario_version_idx on scenario.scenario_version (tenant_id, scenario_id, version desc);
create index if not exists scenario_issue_idx on scenario.scenario_validation_issue (tenant_id, scenario_id, version, severity);
create index if not exists scenario_outbox_unpublished_idx on scenario.scenario_outbox_event (tenant_id, occurred_at) where published_at is null;
