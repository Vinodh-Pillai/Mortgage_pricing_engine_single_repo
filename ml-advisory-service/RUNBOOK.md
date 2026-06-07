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

## PII-14-S03 Advisory Interface

- Advisory responses are non-authoritative display artifacts. They must never update deterministic pricing, eligibility, approval, concession, compensation, or lock state.
- The API contract for display is represented by `GET /api/v1/tenants/{tenantId}/ml-advisory/advisories?scenarioId=&pricingResultId=` and `GET /api/v1/tenants/{tenantId}/ml-advisory/advisories/{advisoryId}`.
- Every advisory card carries the exact disclaimer `Advisory only — does not change final pricing or eligibility.`, `authoritative=false`, and allowed actions limited to view, dismiss, and feedback.
- Low-confidence collapse behavior is driven by tenant/model-risk display policy supplied to the command. Missing display policy fails closed with `POLICY_NOT_SATISFIED`.
- `MlAdvisoryGenerated.v1` and `MlAdvisoryViewed.v1` outbox records carry identifiers, confidence band, model version, snapshot reference, and view surface only; sensitive feature values and reason descriptions are not included in event payloads.
- UI implementation remains outside this service-local lock. The service exposes panel state, disclaimer, allowed actions, and accessibility label fields for a future pricing-workbench panel.

## PII-14-S04 Local Model Adapter

- `LocalModelAdapter` invokes only local model artifacts represented by registry metadata; there is no external model service call in this slice.
- `ModelArtifactResolver` requires `APPROVED_FOR_ADVISORY`, a non-blank local artifact URI, schema metadata, and matching registry/runtime checksums before a model can be used.
- Schema mismatch, checksum mismatch, unapproved model versions, runtime failure, timeout, and rejected output all degrade to a non-blocking `NO_ADVISORY` response while recording invocation/audit evidence.
- `MlModelInferenceCompleted.v1` and `MlModelRuntimeHealthChanged.v1` carry tenant, invocation/runtime, model version, snapshot, status, latency, and reason metadata only; raw borrower feature values are not written to events, audit records, or runtime health.
- The health contract is `GET /api/v1/tenants/{tenantId}/ml-advisory/model-runtime/health`; it exposes safe operational state, checksum, load time, last error, and kill-switch state without artifact URIs or feature payloads.

## PII-14-S05 Pricing Advisory Service

- Pricing advisory evaluation is exposed as `POST /api/v1/tenants/{tenantId}/ml-advisory/pricing-advisories:evaluate` and returns non-authoritative evidence with `deterministicPricingUnchanged=true`.
- The service consumes an approved feature snapshot and local model artifact, then suppresses advisory output when tenant controls disable advisory display, configured confidence policy hides the result, fair-lending governance excludes prohibited proxy features, or the model runtime returns no safe advisory.
- `MlPricingAdvisoryGenerated.v1` and `MlPricingAdvisorySuppressed.v1` include advisory, pricing-result, model-version, snapshot, confidence/status, and reason metadata only. Raw borrower feature values are never emitted.
- Advisory IDs include tenant, scenario, pricing result, snapshot, and model version so cache/read-model callers can invalidate when the pricing result, model version, or snapshot changes.
- Live Redis caching, cross-service pricing-result reads, feedback submission, explainability content, and pricing-workbench UI cards remain integration points; this service-local slice provides stable links, events, and audit evidence without mutating deterministic pricing.
