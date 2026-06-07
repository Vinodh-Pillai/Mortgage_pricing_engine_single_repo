create table if not exists cache_operation_audit (
  id uuid primary key,
  tenant_id uuid not null,
  namespace varchar(80) not null,
  operation varchar(40) not null,
  key_pattern varchar(300),
  requested_by varchar(120) not null,
  correlation_id varchar(80) not null,
  status varchar(30) not null,
  failure_code varchar(80),
  created_at timestamptz not null,
  constraint chk_cache_op_audit_operation check (
    operation in ('HEALTH_CHECK','READ','WRITE','DELETE','FLUSH_NAMESPACE','FALLBACK')
  )
);

create index if not exists idx_cache_op_audit_tenant_created
  on cache_operation_audit (tenant_id, created_at desc);

create index if not exists idx_cache_op_audit_corr
  on cache_operation_audit (correlation_id);
