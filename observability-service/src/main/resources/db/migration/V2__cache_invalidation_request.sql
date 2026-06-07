create table if not exists cache_invalidation_request (
  id uuid primary key,
  tenant_id uuid not null,
  namespace varchar(80) not null,
  scope_type varchar(40) not null,
  scope_ref varchar(160) not null,
  source_event_id varchar(120) not null,
  source_event_type varchar(120) not null,
  version_graph_jsonb jsonb not null default '{}'::jsonb,
  idempotency_key varchar(160) not null,
  status varchar(40) not null,
  attempt_count int not null default 0,
  last_error_code varchar(80),
  requested_by varchar(120) not null,
  operator_reason varchar(160),
  correlation_id varchar(80) not null,
  created_at timestamptz not null,
  completed_at timestamptz,
  constraint chk_cache_inv_status check (
    status in ('REQUESTED','PROCESSING','SUCCEEDED','PARTIAL','FAILED','DEAD_LETTERED','REPLAYED')
  ),
  constraint uq_cache_inv_tenant_idempotency unique (tenant_id, idempotency_key),
  constraint uq_cache_inv_tenant_source_namespace unique (tenant_id, source_event_id, namespace)
);

create index if not exists idx_cache_inv_status_created
  on cache_invalidation_request (status, created_at);

create index if not exists idx_cache_inv_scope
  on cache_invalidation_request (tenant_id, namespace, scope_type, scope_ref);
