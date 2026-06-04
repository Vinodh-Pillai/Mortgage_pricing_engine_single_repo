# ML Advisory Feature Flag Runbook

## PII-14-S01 Scope

This service owns tenant-scoped ML advisory control behavior. The first vertical slice includes domain validation, in-memory application behavior, event/audit evidence models, SQL migration artifacts, golden fixtures, and JUnit validation.

## Operational Rules

- Missing controls resolve to `DISABLED`.
- Active kill switch resolves all advisory types to `DISABLED` before tenant flag state is considered.
- `ADVISORY_VISIBLE` requires model registry status `APPROVED_FOR_ADVISORY` and approval metadata.
- State changes require `ML_ADVISORY_ADMIN`, a change reason, model-risk ticket, correlation ID, and idempotency key.
- Advisory state must not drive deterministic pricing, eligibility, lock, or compensation outcomes.

## Blocked External Integrations

- Live PostgreSQL repository wiring is not included in this slice; the migration is provided at `src/main/resources/db/migration/V1__ml_advisory_controls.sql`.
- Redis cache and Kafka publication are represented by deterministic cache keys and outbox records until shared runtime infrastructure is available.
- Enterprise RBAC and approval workflow source-of-truth are represented by explicit role and approval fields without cross-service calls.
