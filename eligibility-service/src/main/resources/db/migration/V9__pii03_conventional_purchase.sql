create table if not exists eligibility.scenario (
  tenant_id uuid not null,
  scenario_id uuid primary key,
  scenario_version int not null default 1,
  channel varchar(32) not null,
  loan_purpose varchar(32) not null check (loan_purpose = 'PURCHASE'),
  occupancy_type varchar(32) not null,
  loan_amount numeric(18,2) not null check (loan_amount > 0),
  purchase_price numeric(18,2) not null check (purchase_price > 0),
  appraised_value numeric(18,2) check (appraised_value is null or appraised_value > 0),
  subordinate_financing_amount numeric(18,2) not null default 0,
  ltv numeric(7,5) not null,
  cltv numeric(7,5) not null,
  representative_fico int check (representative_fico >= 300 and representative_fico <= 850),
  dti numeric(7,5),
  property_state char(2) not null,
  property_county varchar(128),
  property_zip varchar(10) not null,
  property_type varchar(32) not null,
  units int not null check (units >= 1 and units <= 4),
  lock_period_days int not null,
  aus_type varchar(32),
  documentation_type varchar(32),
  fact_quality_status varchar(32) not null,
  created_by uuid not null,
  created_at_utc timestamp not null default now(),
  updated_at_utc timestamp not null default now(),
  unique (tenant_id, scenario_id, scenario_version)
);

create index if not exists scenario_channel_state_idx on eligibility.scenario (tenant_id, channel, property_state);
create index if not exists scenario_creator_idx on eligibility.scenario (tenant_id, created_by, created_at_utc desc);

create table if not exists eligibility.scenario_fact_issue (
  fact_issue_id uuid primary key,
  tenant_id uuid not null,
  scenario_id uuid not null,
  scenario_version int not null,
  field_path varchar(128) not null,
  issue_type varchar(64) not null,
  severity varchar(32) not null,
  actual_value_encrypted jsonb,
  expected_value varchar(256),
  message text not null,
  remediation_hint text
);

create index if not exists fact_issue_scenario_idx on eligibility.scenario_fact_issue (tenant_id, scenario_id, scenario_version, severity);

alter table eligibility.quote
  add column if not exists requested_by uuid,
  add column if not exists requested_at_utc timestamptz,
  add column if not exists idempotency_key_hash varchar(128),
  add column if not exists audit_package_id uuid;

alter table eligibility.quote_option
  add column if not exists product_version_id uuid,
  add column if not exists investor_id uuid,
  add column if not exists channel varchar(32),
  add column if not exists eligibility_evaluation_id uuid,
  add column if not exists display_rank int;

create index if not exists quote_option_rank_idx on eligibility.quote_option (tenant_id, quote_id, display_rank);

alter table eligibility.outbox_event
  add column if not exists event_type varchar(120),
  add column if not exists payload_json jsonb,
  add column if not exists occurred_at timestamptz,
  add column if not exists published_at timestamptz;

create table if not exists eligibility.audit_package (
  audit_package_id uuid primary key,
  tenant_id uuid not null,
  aggregate_id uuid not null,
  actor_id varchar(128),
  correlation_id varchar(128),
  causation_id varchar(128),
  request_hash varchar(128),
  result_hash varchar(128),
  scenario_version int,
  rule_versions_json jsonb,
  created_at_utc timestamp not null default now()
);

create index if not exists audit_package_tenant_idx on eligibility.audit_package (tenant_id, aggregate_id);
