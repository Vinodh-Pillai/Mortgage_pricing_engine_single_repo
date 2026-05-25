create schema if not exists eligibility;

create table if not exists eligibility.quote (
  tenant_id uuid not null,
  quote_id uuid not null,
  scenario_id uuid not null,
  scenario_version int not null,
  quote_status varchar(32) not null,
  request_hash varchar(128) not null,
  result_hash varchar(128) not null,
  audit_package_id uuid not null,
  request_json jsonb not null,
  response_json jsonb not null,
  requested_at timestamptz not null default now(),
  primary key (tenant_id, quote_id)
);

create table if not exists eligibility.quote_option (
  tenant_id uuid not null,
  quote_option_id uuid primary key,
  quote_id uuid not null,
  product_code varchar(64) not null,
  investor_code varchar(64) not null,
  eligibility_status varchar(32) not null,
  pricing_status varchar(32) not null,
  display_rank int not null,
  summary_reason text,
  decisions_json jsonb not null
);

create table if not exists eligibility.eligibility_evaluation (
  tenant_id uuid not null,
  evaluation_id uuid primary key,
  scenario_id uuid not null,
  scenario_version int not null,
  rule_set_version int not null,
  evaluation_status varchar(32) not null,
  input_hash varchar(128) not null,
  result_hash varchar(128) not null,
  evaluated_at timestamptz not null default now()
);

create table if not exists eligibility.eligibility_decision (
  tenant_id uuid not null,
  decision_id uuid primary key,
  evaluation_id uuid not null,
  product_code varchar(64) not null,
  investor_code varchar(64) not null,
  rule_code varchar(80) not null,
  severity varchar(32) not null,
  decision varchar(32) not null,
  reason_code varchar(80) not null,
  message text not null,
  actual_value varchar(256),
  required_value varchar(256),
  trace_json jsonb not null
);

create table if not exists eligibility.idempotency_record (
  tenant_id uuid not null,
  idempotency_key varchar(160) not null,
  request_hash varchar(128) not null,
  response_type varchar(80) not null,
  response_json jsonb not null,
  created_at timestamptz not null default now(),
  primary key (tenant_id, idempotency_key)
);

create table if not exists eligibility.outbox_event (
  tenant_id uuid not null,
  event_id uuid primary key,
  aggregate_id uuid not null,
  event_type varchar(120) not null,
  payload_json jsonb not null,
  occurred_at timestamptz not null default now(),
  published_at timestamptz
);

create table if not exists eligibility.audit_record (
  tenant_id uuid not null,
  audit_id uuid primary key,
  aggregate_id uuid not null,
  action varchar(120) not null,
  replay_hash varchar(128) not null,
  payload_json jsonb not null,
  occurred_at timestamptz not null default now()
);

create index if not exists quote_scenario_idx on eligibility.quote (tenant_id, scenario_id, scenario_version);
create index if not exists evaluation_status_idx on eligibility.eligibility_evaluation (tenant_id, evaluation_status, evaluated_at desc);
