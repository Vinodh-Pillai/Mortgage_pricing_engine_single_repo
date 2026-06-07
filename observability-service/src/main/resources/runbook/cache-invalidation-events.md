# Cache Invalidation Events Runbook

Scope: PII-17-S05 local observability-service cache invalidation events.

1. Check `cache.invalidation.lag`, `cache.invalidation.failed.count`, and `cache.invalidation.dlq.count`.
2. Inspect `cache_invalidation_request` by `tenant_id`, `namespace`, `status`, and `correlation_id`.
3. Replay only with the original idempotency key after the schema/config issue is fixed.
4. If stale pricing risk exists and runtime Redis/Kafka controls are available, use the approved tenant namespace flush workflow; this local slice does not directly mutate cross-service Redis/Kafka infrastructure.
5. Schema-version mismatch is fail-closed into dead-letter evidence instead of broad best-effort deletion.
6. PostgreSQL persistence is available through `JdbcCacheInvalidationRepository` when a runtime supplies a `DataSource`; this java-only module has no application bootstrap or live database fixture in the story lane.
7. POST/GET REST endpoints remain blocked for this lane because `observability-service` has no Spring/Web controller pattern or web runtime dependency; adding one would broaden the service architecture beyond the acquired story locks.
