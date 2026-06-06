create table if not exists what_if_export (
  tenant_id varchar(80) not null,
  export_id uuid primary key,
  source_type varchar(80) not null,
  source_id varchar(160) not null,
  format varchar(20) not null,
  recipient_type varchar(80) not null,
  status varchar(40) not null,
  storage_uri varchar(500) not null,
  content_sha256 varchar(80) not null,
  row_count int not null,
  expires_at timestamptz not null,
  revoked_at timestamptz,
  created_by varchar(128) not null,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  correlation_id varchar(128) not null,
  idempotency_key_hash varchar(80),
  request_hash varchar(80) not null,
  unique (tenant_id, export_id),
  unique (tenant_id, idempotency_key_hash)
);

create index if not exists idx_what_if_export_tenant_status_updated
  on what_if_export (tenant_id, status, updated_at desc);
