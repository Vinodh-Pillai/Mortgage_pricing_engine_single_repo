# PII-17-S09 Backpressure Policy Runbook

Scope: local/dev observability-service evidence for configurable tenant backpressure policies.

1. Identify the active resource trigger: pricing CPU, DB pool, Redis latency, event lag, queue depth, or maintenance mode.
2. Confirm the active policy version, tenant, priority class, queue depth, rejection count, and correlation ID.
3. Confirm protective actions are reducing load before widening limits: disable cache warm, reduce concurrency, defer batch/replay, tighten rate limits, or shed low-priority work.
4. Repair or scale the saturated resource, then monitor recovery hysteresis and flapping alerts before returning to NORMAL.
5. Preserve audit/outbox evidence and replay hash. Do not store raw borrower PII in backpressure metadata.

Rollback/recovery: disable or suspend only the affected tenant policy version, keep the last `backpressure_state` row for audit, and remove the migration-created tables only in local/dev rollback drills after indexes are dropped.
