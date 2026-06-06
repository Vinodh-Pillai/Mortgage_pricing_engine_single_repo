create table if not exists margin_version_manifest_cache (
  tenant_id varchar(64) not null,
  scope_hash varchar(128) not null,
  active_at_utc timestamp not null,
  manifest_json clob not null,
  config_hash varchar(128) not null,
  created_at timestamp not null,
  primary key (tenant_id, scope_hash, active_at_utc, config_hash)
);

create index if not exists idx_margin_version_manifest_cache_tenant_active
  on margin_version_manifest_cache (tenant_id, active_at_utc);
