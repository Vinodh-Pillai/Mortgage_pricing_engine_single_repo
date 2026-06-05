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

## PII-14-S02 Shadow Input Capture

- Feature snapshots are side-effect-only advisory evidence and must not influence deterministic pricing, eligibility, lock, or compensation outcomes.
- Capture requires tenant ID, idempotency key, actor, deterministic output references, feature schema version, legal basis, retention class, source references, and feature inventory metadata.
- Protected and prohibited proxy features are excluded; non-public included values are represented only by deterministic hashes and redaction tokens in service responses, events, and tests.
- `MlFeatureSnapshotCaptured.v1` outbox records contain metadata and source reference keys, never raw sensitive feature values.
- Live encrypted PostgreSQL storage, raw-value key management, operations UI, and enterprise audit delivery remain external integrations; this slice provides schema artifacts and local evidence models.
