create table if not exists what_if_replay (
  tenant_id varchar(64) not null,
  replay_id uuid primary key,
  source_type varchar(40) not null,
  source_id varchar(128) not null,
  mode varchar(40) not null,
  status varchar(40) not null,
  original_hash varchar(128) not null,
  replay_hash varchar(128) not null,
  mismatch_category varchar(80) not null,
  pricing_config_versions_json text not null,
  event_sequence_json text not null,
  evidence_json text not null,
  audit_ref varchar(256) not null,
  created_by varchar(128) not null,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  correlation_id varchar(128) not null,
  idempotency_key_hash varchar(128),
  constraint uq_what_if_replay_tenant_replay unique (tenant_id, replay_id),
  constraint uq_what_if_replay_tenant_idempotency unique (tenant_id, idempotency_key_hash)
);

create index if not exists idx_what_if_replay_tenant_status_updated
  on what_if_replay (tenant_id, status, updated_at desc);

create table if not exists what_if_replay_diff (
  tenant_id varchar(64) not null,
  replay_id uuid not null,
  diff_id bigserial primary key,
  path varchar(256) not null,
  original_value varchar(256),
  replay_value varchar(256),
  tolerance varchar(64),
  category varchar(80) not null,
  created_at timestamptz not null,
  constraint fk_what_if_replay_diff_replay foreign key (replay_id) references what_if_replay (replay_id)
);

create index if not exists idx_what_if_replay_diff_tenant_replay
  on what_if_replay_diff (tenant_id, replay_id);
