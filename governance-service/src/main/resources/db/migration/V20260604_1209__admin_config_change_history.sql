create table if not exists admin_config_history_entry (
  tenant_id uuid not null,
  history_id uuid not null,
  artifact_id varchar(128) not null,
  artifact_type varchar(128) not null,
  version_id varchar(128) not null,
  event_type varchar(128) not null,
  event_version int not null,
  actor_id varchar(128) not null,
  actor_group varchar(128) not null,
  status_from varchar(64),
  status_to varchar(64) not null,
  reason_code varchar(128),
  summary varchar(1024) not null,
  payload_hash_before varchar(128),
  payload_hash_after varchar(128) not null,
  audit_record_id varchar(128) not null,
  outbox_event_id varchar(128),
  replay_event_id varchar(128) not null,
  replay_hash varchar(128) not null,
  occurred_at timestamptz not null,
  sequence bigint not null,
  primary key (tenant_id, history_id)
);

create index if not exists idx_admin_config_history_artifact_time
  on admin_config_history_entry (tenant_id, artifact_id, occurred_at desc, sequence desc);

create index if not exists idx_admin_config_history_actor_time
  on admin_config_history_entry (tenant_id, actor_id, occurred_at desc);

create index if not exists idx_admin_config_history_event_type
  on admin_config_history_entry (tenant_id, event_type, occurred_at desc);

create index if not exists idx_admin_config_history_reason_code
  on admin_config_history_entry (tenant_id, reason_code, occurred_at desc);

create table if not exists admin_config_diff_cache (
  tenant_id uuid not null,
  diff_id uuid not null,
  from_version_id varchar(128) not null,
  to_version_id varchar(128) not null,
  diff_json_redacted jsonb not null,
  redaction_policy_id varchar(128) not null,
  diff_hash varchar(128) not null,
  created_at timestamptz not null,
  expiry timestamptz not null,
  primary key (tenant_id, diff_id)
);

create index if not exists idx_admin_config_diff_cache_versions
  on admin_config_diff_cache (tenant_id, from_version_id, to_version_id, created_at desc);
