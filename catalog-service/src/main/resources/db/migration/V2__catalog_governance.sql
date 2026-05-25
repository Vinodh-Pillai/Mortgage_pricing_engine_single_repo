alter table catalog.catalog_audit_record
  add column if not exists actor_id varchar(128),
  add column if not exists correlation_id varchar(128),
  add column if not exists idempotency_key varchar(160),
  add column if not exists before_json jsonb not null default '{}'::jsonb,
  add column if not exists after_json jsonb not null default '{}'::jsonb;

alter table catalog.catalog_outbox_event
  add column if not exists actor_id varchar(128),
  add column if not exists correlation_id varchar(128),
  add column if not exists idempotency_key varchar(160);

alter table catalog.product_config_snapshot
  add column if not exists catalog_id uuid,
  add column if not exists as_of_date date;

create index if not exists catalog_audit_tenant_time_idx on catalog.catalog_audit_record (tenant_id, occurred_at desc);
create index if not exists product_config_snapshot_id_tenant_idx on catalog.product_config_snapshot (tenant_id, snapshot_id);
