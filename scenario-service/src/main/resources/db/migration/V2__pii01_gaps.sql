-- S08: Channel Submission Profile tables
create table if not exists scenario.submission_profile (
  tenant_id uuid not null,
  submission_profile_id uuid not null,
  channel varchar(40) not null,
  quote_intent varchar(40) not null,
  profile_name varchar(160) not null,
  created_by varchar(128) not null,
  created_at timestamptz not null default now(),
  primary key (tenant_id, submission_profile_id)
);

create table if not exists scenario.submission_profile_version (
  tenant_id uuid not null,
  profile_version_id uuid not null,
  submission_profile_id uuid not null,
  version_number int not null,
  status varchar(30) not null default 'DRAFT',
  effective_from_utc timestamptz not null,
  effective_to_utc timestamptz null,
  checksum char(64) not null,
  created_at timestamptz not null default now(),
  primary key (tenant_id, profile_version_id),
  foreign key (tenant_id, submission_profile_id) references scenario.submission_profile(tenant_id, submission_profile_id)
);

create table if not exists scenario.submission_profile_field_rule (
  tenant_id uuid not null,
  field_rule_id uuid not null,
  profile_version_id uuid not null,
  section varchar(80) not null,
  field_path varchar(160) not null,
  required_when_expression text not null,
  severity varchar(20) not null,
  message varchar(1000) not null,
  remediation_hint varchar(1000),
  primary key (tenant_id, field_rule_id),
  foreign key (tenant_id, profile_version_id) references scenario.submission_profile_version(tenant_id, profile_version_id)
);

create index if not exists spv_idx_channel_intent on scenario.submission_profile_version (tenant_id, status, effective_from_utc, effective_to_utc);
create index if not exists sp_idx_channel on scenario.submission_profile (tenant_id, channel, quote_intent);

-- S09: Batch Scenario Import tables
create table if not exists scenario.scenario_import_job (
  tenant_id uuid not null,
  import_job_id uuid not null,
  status varchar(40) not null default 'QUEUED',
  file_name varchar(256),
  file_hash varchar(128),
  template_version varchar(40) not null,
  channel varchar(40) not null,
  quote_intent varchar(40) not null,
  partial_success_policy varchar(40) not null default 'ALLOW_VALID_ROWS',
  submitted_by varchar(128) not null,
  submitted_at_utc timestamptz not null default now(),
  started_at_utc timestamptz null,
  completed_at_utc timestamptz null,
  total_rows int not null default 0,
  created_rows int not null default 0,
  failed_rows int not null default 0,
  primary key (tenant_id, import_job_id)
);

create table if not exists scenario.scenario_import_row (
  tenant_id uuid not null,
  import_row_id uuid not null,
  import_job_id uuid not null,
  row_number int not null,
  row_hash varchar(128),
  status varchar(40) not null default 'PENDING',
  scenario_id uuid null,
  scenario_version_id uuid null,
  idempotency_key varchar(160),
  created_at_utc timestamptz not null default now(),
  primary key (tenant_id, import_row_id),
  foreign key (tenant_id, import_job_id) references scenario.scenario_import_job(tenant_id, import_job_id)
);

create table if not exists scenario.scenario_import_error (
  tenant_id uuid not null,
  import_error_id uuid not null,
  import_row_id uuid not null,
  field_name varchar(160),
  error_code varchar(80) not null,
  message varchar(1000) not null,
  raw_value_redacted varchar(500),
  primary key (tenant_id, import_error_id),
  foreign key (tenant_id, import_row_id) references scenario.scenario_import_row(tenant_id, import_row_id)
);

create unique index if not exists sir_unique_row on scenario.scenario_import_row (tenant_id, import_job_id, row_number);
create index if not exists sij_idx_status on scenario.scenario_import_job (tenant_id, status, submitted_at_utc);

-- S10: Scenario Replay Access Log table
create table if not exists scenario.scenario_replay_access_log (
  tenant_id uuid not null,
  access_id uuid not null,
  scenario_id uuid not null,
  scenario_version int not null,
  actor_id varchar(128) not null,
  redaction_mode varchar(20) not null,
  export_flag boolean not null default false,
  access_reason_code varchar(80),
  correlation_id varchar(128),
  accessed_at_utc timestamptz not null default now(),
  primary key (tenant_id, access_id)
);

create index if not exists sral_idx_scenario on scenario.scenario_replay_access_log (tenant_id, scenario_id, accessed_at_utc desc);
