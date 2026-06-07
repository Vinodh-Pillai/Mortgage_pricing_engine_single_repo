create table if not exists admin_config_ui_metadata_snapshot (
  tenant_id uuid not null,
  cache_key varchar(80) primary key,
  metadata_version varchar(128) not null,
  permissions_hash varchar(128) not null,
  request_json jsonb not null,
  result_json jsonb not null,
  audit_ref uuid not null,
  replay_ref varchar(128) not null,
  correlation_id varchar(128) not null,
  generated_at timestamptz not null
);

create table if not exists admin_config_ui_inventory_snapshot (
  tenant_id uuid not null,
  cache_key varchar(80) not null references admin_config_ui_metadata_snapshot(cache_key),
  artifact_id varchar(128) not null,
  artifact_type varchar(128) not null,
  name varchar(256) not null,
  context varchar(256) not null,
  status varchar(40) not null,
  version int not null,
  effective_start timestamptz,
  effective_end timestamptz,
  validation_status varchar(40) not null,
  approval_status varchar(40) not null,
  last_changed_by varchar(128) not null,
  last_changed_at timestamptz not null,
  publish_status varchar(40) not null,
  primary key (tenant_id, cache_key, artifact_id)
);

create table if not exists admin_config_ui_approval_queue_snapshot (
  tenant_id uuid not null,
  cache_key varchar(80) not null references admin_config_ui_metadata_snapshot(cache_key),
  approval_request_id varchar(128) not null,
  artifact_id varchar(128) not null,
  artifact_type varchar(128) not null,
  name varchar(256) not null,
  status varchar(40) not null,
  submitted_by varchar(128) not null,
  submitted_at timestamptz not null,
  due_at timestamptz,
  available_actions_json jsonb not null default '[]'::jsonb,
  primary key (tenant_id, cache_key, approval_request_id)
);

create index if not exists idx_admin_config_ui_inventory_filter
  on admin_config_ui_inventory_snapshot (tenant_id, artifact_type, status, effective_start, effective_end, last_changed_at desc);

create index if not exists idx_admin_config_ui_approval_queue_status
  on admin_config_ui_approval_queue_snapshot (tenant_id, status, submitted_at);
