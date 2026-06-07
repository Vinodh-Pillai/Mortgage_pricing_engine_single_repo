# PII-17-S06 Database Index Baseline Runbook

Scope: observability-service-owned cache audit and cache invalidation tables only. Pricing, reference-data, rate-limit, and backpressure tables remain service-owned outside this lane.

1. Open the DB performance panel or internal health snapshot for the query class.
2. Confirm the query includes `tenant_id` and the expected status/namespace predicates before comparing plans.
3. Verify migration `V3__pii17_s06_database_index_baseline.sql` is applied.
4. If stats are stale, run the approved analyze action in dev/stage before changing indexes.
5. If rollout blocks writes, stop the rollout and apply only the rollback note for the newly created index.

Telemetry names emitted by the service-local snapshot: `db.query.latency`, `db.index.scan.count`, `db.seq_scan.count`, `db.connection.pool.active`, and `db.lock.wait`.
