-- owner_story: PII-17-S06
-- purpose: service-local PostgreSQL index baseline for observability-owned cache audit and invalidation hot paths.
-- rollback notes: for large production tables, apply the matching DROP INDEX CONCURRENTLY note outside a transaction when the migration runner permits it.

create index if not exists idx_cache_op_audit_tenant_status_created
  on cache_operation_audit (tenant_id, status, created_at desc);

create index if not exists idx_cache_op_audit_tenant_namespace_operation_created
  on cache_operation_audit (tenant_id, namespace, operation, created_at desc);

create index if not exists idx_cache_inv_tenant_namespace_status_created
  on cache_invalidation_request (tenant_id, namespace, status, created_at desc);

create index if not exists idx_cache_inv_processing_created
  on cache_invalidation_request (status, attempt_count, created_at);

-- Existing baseline constraints used by this story:
-- uq_cache_inv_tenant_idempotency on (tenant_id, idempotency_key)
-- uq_cache_inv_tenant_source_namespace on (tenant_id, source_event_id, namespace)
-- idx_cache_inv_scope on (tenant_id, namespace, scope_type, scope_ref)
-- idx_cache_op_audit_tenant_created on (tenant_id, created_at desc)
