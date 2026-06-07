create table if not exists apr_advisory_evaluation (
  id uuid primary key,
  tenant_id varchar(64) not null,
  scenario_id varchar(128) not null,
  quote_id varchar(128),
  as_of_date date not null,
  status varchar(40) not null,
  note_rate numeric(12,8),
  apr numeric(12,8),
  spread numeric(12,8),
  formula_ref varchar(160) not null,
  config_version_ids jsonb not null,
  result_hash varchar(160) not null,
  created_at timestamp not null,
  updated_at timestamp not null
);

create index if not exists idx_apr_advisory_tenant_scenario
  on apr_advisory_evaluation (tenant_id, scenario_id);

create index if not exists idx_apr_advisory_tenant_quote
  on apr_advisory_evaluation (tenant_id, quote_id);

create index if not exists idx_apr_advisory_tenant_as_of
  on apr_advisory_evaluation (tenant_id, as_of_date);

create index if not exists idx_apr_advisory_result_hash
  on apr_advisory_evaluation (result_hash);

create table if not exists apr_finance_charge_component (
  id uuid primary key,
  tenant_id varchar(64) not null,
  evaluation_id uuid not null,
  component_code varchar(80) not null,
  amount numeric(19,4) not null,
  included boolean not null,
  inclusion_rule_ref varchar(160) not null,
  source_ref varchar(160),
  sensitivity_classification varchar(80) not null,
  foreign key (evaluation_id) references apr_advisory_evaluation(id)
);

create index if not exists idx_apr_finance_charge_tenant_eval
  on apr_finance_charge_component (tenant_id, evaluation_id);

create table if not exists apr_advisory_ledger (
  id uuid primary key,
  tenant_id varchar(64) not null,
  evaluation_id uuid not null,
  sequence_number int not null,
  entry_type varchar(80) not null,
  entry_json text not null,
  created_at timestamp not null,
  foreign key (evaluation_id) references apr_advisory_evaluation(id)
);

create unique index if not exists idx_apr_advisory_ledger_sequence
  on apr_advisory_ledger (tenant_id, evaluation_id, sequence_number);
