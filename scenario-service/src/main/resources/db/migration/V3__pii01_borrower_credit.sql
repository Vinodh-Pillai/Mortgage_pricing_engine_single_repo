-- PII-01-S02: Borrower and Credit capture tables

create table if not exists scenario.scenario_borrower (
  tenant_id uuid not null,
  scenario_borrower_id uuid not null,
  scenario_id uuid not null,
  scenario_version_id uuid not null,
  borrower_external_id varchar(128),
  borrower_role varchar(40) not null,
  occupies_property boolean not null,
  created_at_utc timestamptz not null default now(),
  primary key (tenant_id, scenario_borrower_id),
  foreign key (tenant_id, scenario_id) references scenario.scenario(tenant_id, scenario_id)
);

create table if not exists scenario.scenario_credit_attribute (
  tenant_id uuid not null,
  credit_attribute_id uuid not null,
  scenario_borrower_id uuid not null,
  credit_status varchar(40) not null,
  credit_score integer,
  credit_score_source varchar(40),
  credit_score_date date,
  quality_status varchar(40) not null default 'COMPLETE',
  primary key (tenant_id, credit_attribute_id),
  foreign key (tenant_id, scenario_borrower_id) references scenario.scenario_borrower(tenant_id, scenario_borrower_id),
  check (credit_score is null or (credit_score >= 300 and credit_score <= 850))
);

create table if not exists scenario.scenario_representative_credit (
  tenant_id uuid not null,
  scenario_version_id uuid not null,
  scenario_id uuid not null,
  version_number int not null,
  representative_score integer,
  derivation_rule_code varchar(80) not null,
  derivation_trace_json jsonb not null default '{}'::jsonb,
  quality_status varchar(40) not null,
  primary key (tenant_id, scenario_version_id),
  foreign key (tenant_id, scenario_id) references scenario.scenario(tenant_id, scenario_id)
);

create table if not exists scenario.scenario_validation_issue_detail (
  tenant_id uuid not null,
  issue_id uuid not null,
  scenario_id uuid not null,
  scenario_version int not null,
  issue_code varchar(80) not null,
  field_path varchar(240) not null,
  severity varchar(20) not null,
  message text not null,
  created_at_utc timestamptz not null default now(),
  primary key (tenant_id, issue_id)
);

-- Partial unique index: only one PRIMARY borrower per (tenant, scenario_version)
create unique index if not exists sb_unique_primary_borrower
  on scenario.scenario_borrower (tenant_id, scenario_version_id, borrower_role)
  where borrower_role = 'PRIMARY';

-- Index for fast lookup by scenario and version
create index if not exists sb_idx_scenario_version
  on scenario.scenario_borrower (tenant_id, scenario_id, scenario_version_id);

-- Index for credit attribute by borrower
create index if not exists sca_idx_borrower
  on scenario.scenario_credit_attribute (tenant_id, scenario_borrower_id);

-- Index for validation issues
create index if not exists svd_idx_scenario_version
  on scenario.scenario_validation_issue_detail (tenant_id, scenario_id, scenario_version);
