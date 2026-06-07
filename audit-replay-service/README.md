# Audit Replay Service Foundation

This module is a contract-first foundation for future audit and replay tooling. It does not execute production replay, export audit records, persist data, enforce retention, or provide regulatory evidence guarantees.

## Scope

- Owns local validation fixtures and documentation for `contracts/audit/*.schema.json`.
- Keeps this PII-13 implementation isolated to `contracts/audit/**` and `audit-replay-service/**`.
- Avoids mortgage pricing rates, thresholds, eligibility rules, investor rules, compliance conclusions, partner integration, and protected active service wiring.

## Contracts

- `contracts/audit/audit-event-envelope.schema.json` defines a generic append-only audit event envelope with stable event identity, source service, event type, aggregate reference, timestamps, ordering/correlation metadata, and generic payload fields.
- `contracts/audit/audit-replay-manifest.schema.json` defines a replay manifest stub with replay scope, event range, source filters, non-domain status values, timestamps, and optional artifact links.

## Validation

`validation/contract-fixtures.json` provides minimal valid examples for local schema validation by future test tooling. This worker did not add root build wiring or protected service integration because the developer packet keeps the slice isolated.

`validation/validate-contract-fixtures.mjs` is a module-local Node.js validation helper that parses both audit schemas and confirms the fixtures include each schema's required foundation fields. It intentionally avoids production replay execution, persistence, export, or cross-service wiring.

## PII-13-S01 Transactional Outbox Slice

- Table `audit_outbox_events` stores tenant-scoped immutable payload/header JSON, delivery status, retry metadata, correlation/causation IDs, idempotency key, and payload integrity hash.
- `OutboxRecorder` is the service-local transactional boundary helper. Call it inside the same domain transaction as the material state change so the domain write and outbox row commit or roll back together.
- `OutboxPublisherService` claims publishable rows with the repository `FOR UPDATE SKIP LOCKED` query, delegates broker delivery to `OutboxBrokerClient`, and records deterministic retry or poison metadata from `wcpe.audit.outbox.publisher.*` configuration.
- REST endpoints under `/api/v1/tenants/{tenantId}/audit/outbox-events` provide tenant-scoped list/detail and failed-event retry behavior. Full RBAC, Operations UI, Kafka/Testcontainers, DLQ ownership, and payload encryption remain environment integration work outside this local service slice.

## PII-13-S02 Event Envelope Slice

- `EventEnvelopeV1` standardizes tenant, event type/version, producer, aggregate, actor, correlation/causation, idempotency, schema reference, payload hash, previous hash, integrity hash, and legal-hold tag fields without raw PII in headers.
- `EventEnvelopeValidator` rejects malformed, stale, unknown-schema, tenant-header-mismatched, and raw-PII-header envelopes before outbox recording or replay consumption.
- `EventEnvelopeHash` canonicalizes payload/envelope JSON before SHA-256 hashing so replay verification is deterministic across object key ordering.
- Registry endpoints expose versioned contract metadata and fixtures at `/api/v1/event-contracts/envelopes/v1` and `/api/v1/event-contracts/events/{eventType}/versions/{version}`. The MVP stores schema resources in-module and creates the service-owned `event_contract_versions` table for a later persisted registry write path.

### Runbook Notes

- Old pending rows: inspect `audit_outbox_events` by `(tenant_id,status,next_attempt_at)` and compare against `audit_outbox_pending_count`/publish latency metrics when the metrics backend is wired.
- Broker outage: rows move from `IN_FLIGHT` to `FAILED` with `last_error_code`, then to `POISON` after configured max attempts. Retry only `FAILED` rows through the tenant-scoped retry endpoint with an `Idempotency-Key`.
- Cross-tenant lookup: use the tenant-scoped API and repository methods only; missing rows return `404` instead of revealing another tenant's event.
