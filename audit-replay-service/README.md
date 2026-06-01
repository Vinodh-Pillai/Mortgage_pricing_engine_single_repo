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
