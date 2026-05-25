# PII-03 Implementation Status

## Current Baseline
`projects/eligibility-service` implements a PostgreSQL-backed Spring Boot baseline for the conventional purchase quote/eligibility shell.

Current status: **PostgreSQL quote/eligibility baseline plus governed loan-limit/FICO-LTV policy and explanation read model deployed to k3s with runtime smoke validation passed.**

Implemented baseline:
- `POST /api/v1/tenants/{tenantId}/quotes` with fail-closed `X-Roles: quote:create` enforcement.
- `GET /api/v1/tenants/{tenantId}/quotes/{quoteId}`.
- `POST /api/v1/tenants/{tenantId}/eligibility/evaluations/loan-limit` with `X-Roles: eligibility:evaluate`.
- `POST /api/v1/tenants/{tenantId}/eligibility/evaluations/fico-ltv-matrix` with `X-Roles: eligibility:evaluate`.
- Deterministic loan-limit and FICO/LTV decisions.
- Durable quote, quote options, eligibility evaluations, decisions, idempotency, outbox, and audit tables under schema `eligibility`.
- Dockerfile and k3s manifest.
- Governed policy migration `V2__governed_policy_and_explain.sql` for conforming loan limits, FICO/LTV matrices, and explanation read model.
- Policy-backed loan-limit and FICO/LTV decisions with trace IDs/hashes from persisted configuration rows.
- `GET /api/v1/tenants/{tenantId}/quotes/{quoteId}/options/{quoteOptionId}/eligibility-explanation`.
- Image `192.168.4.93:5000/eligibility-service:0.1.2` deployed to namespace `wcpe-dev`.

## Runtime Validation 2026-05-16
- Pod `eligibility-service-7c699965cf-7szcw` reached `1/1 Running`.
- Readiness endpoint returned `UP`.
- Missing quote role returned HTTP `403`.
- Quote smoke returned quote ID `1070cdee-7970-4c8f-a468-1b961d18cdce` with `eligibleOptionCount=2`.
- Loan-limit rule smoke returned `INELIGIBLE` with reason `LOAN_AMOUNT_EXCEEDS_CONFORMING_LIMIT` for loan amount `850000.00` over `806500.00`.

## Runtime Validation 2026-05-17
- Pod `eligibility-service-d68cb7dc6-4jjzb` reached `1/1 Running` on image `192.168.4.93:5000/eligibility-service:0.1.2`.
- Flyway migrated schema `eligibility` to v2.
- Quote/explanation smoke passed with seeded TX/Travis policy: quote `1689219c-f297-4a2b-97cb-840732cb9820`, `eligibleOptionCount=1`, explanation status `ELIGIBLE`, rules `2`.
- CA/Orange smoke intentionally failed with `LIMIT_NOT_CONFIGURED`, confirming fail-closed behavior when no published governed loan-limit row matches.

## Remaining Gaps
- Candidate products are defaulted in-service rather than resolved from PII-02 product catalog snapshots.
- Governed loan-limit and FICO/LTV configuration tables are not fully admin-lifecycle managed; current service seeds a local/dev baseline per tenant.
- Redis cache/invalidation is not implemented.
- UI workbench implementation is not implemented.
- Contract/OpenAPI/Testcontainers validation still needs hardening.
