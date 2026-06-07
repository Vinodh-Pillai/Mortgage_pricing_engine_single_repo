create table if not exists integration_dead_letter (
  tenant_id uuid not null,
  dead_letter_id uuid not null,
  source_type varchar(60) not null,
  source_id varchar(160) not null,
  event_type varchar(160) not null,
  schema_version int not null,
  original_config_ref varchar(160),
  payload_ref varchar(512) not null,
  payload_hash varchar(128) not null,
  failure_class varchar(80) not null,
  status varchar(40) not null,
  replay_count int not null default 0,
  legal_hold boolean not null default false,
  correlation_id varchar(128) not null,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  last_replay_at timestamptz,
  constraint integration_dead_letter_pk primary key (tenant_id, dead_letter_id)
);

create index if not exists integration_dead_letter_tenant_status_idx
  on integration_dead_letter (tenant_id, status, created_at desc);

create index if not exists integration_dead_letter_tenant_source_event_idx
  on integration_dead_letter (tenant_id, source_type, event_type);

create table if not exists integration_dead_letter_replay_attempt (
  tenant_id uuid not null,
  attempt_id uuid not null,
  dead_letter_id uuid not null,
  mode varchar(60) not null,
  status varchar(40) not null,
  requested_by varchar(128) not null,
  reason varchar(512) not null,
  result_hash varchar(128) not null,
  result_json jsonb not null default '{}'::jsonb,
  requested_at timestamptz not null,
  correlation_id varchar(128) not null,
  constraint integration_dead_letter_replay_attempt_pk primary key (tenant_id, attempt_id),
  constraint integration_dead_letter_replay_attempt_item_fk foreign key (tenant_id, dead_letter_id)
    references integration_dead_letter (tenant_id, dead_letter_id)
);

create index if not exists integration_dead_letter_replay_attempt_item_idx
  on integration_dead_letter_replay_attempt (tenant_id, dead_letter_id, requested_at desc);
