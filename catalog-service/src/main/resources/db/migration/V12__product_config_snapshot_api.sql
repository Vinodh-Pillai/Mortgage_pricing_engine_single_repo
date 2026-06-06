alter table catalog.product_config_snapshot add column if not exists request_hash varchar(128);
alter table catalog.product_config_snapshot add column if not exists expires_at timestamptz;

create index if not exists product_config_snapshot_request_hash_idx on catalog.product_config_snapshot (tenant_id, request_hash);
create index if not exists product_config_snapshot_created_idx on catalog.product_config_snapshot (tenant_id, created_at desc);
create index if not exists product_config_snapshot_catalog_asof_idx on catalog.product_config_snapshot (tenant_id, catalog_id, as_of_date);
