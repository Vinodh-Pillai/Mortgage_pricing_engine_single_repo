# PII-01 Completeness Review

## Verdict
`projects/scenario-service` now has a PostgreSQL-backed Kubernetes runtime baseline that covers the main scenario intake flow. It is no longer an in-memory-only prototype, but it still needs governance, RBAC, channel profile, replay hardening, async CSV import, and contract testing before full PII-01 completion.

## Implemented Or Partially Implemented
| Story | Status |
|---|---|
| S01 Create Draft Scenario | Baseline implemented/runtime validated |
| S02 Borrower Credit | Partial |
| S03 Loan Structure | Partial |
| S04 Property Attributes | Partial |
| S05 Income and Asset Signals | Partial |
| S06 Normalize Derived Fields | Partial |
| S07 Scenario API Idempotency | Baseline implemented/runtime validated |
| S08 Channel Submission Profile | Missing |
| S09 Batch Scenario Import | Very partial |
| S10 Scenario Replay API | Partial |

## Blocking Gaps
- Authorization, tenant status, RBAC, field-level access, and replay roles are not implemented.
- Idempotency is durable but still response-hash scoped rather than full canonical request-body conflict detection.
- Scenario versions are persisted as snapshots, but the domain still mutates the active aggregate in place between snapshots.
- Channel submission profile is missing.
- Batch import is not multipart CSV/async/row-tracked.
- Replay has no exact-version loading, hash verification, legal hold, or robust redaction.
- No UI/E2E/OpenAPI/contract tests.

## Runtime Validation 2026-05-15
- Built `scenario-service:0.1.0` locally with Gradle bootJar and Docker.
- Pushed image to local registry `192.168.4.93:5000/scenario-service:0.1.0`.
- Deployed to k3s namespace `wcpe-dev` using `projects/scenario-service/k8s/scenario-dev.yaml`.
- Pod `scenario-service-6bfff8cc46-4tmn8` reached `1/1 Running`.
- Smoke flow passed: create draft, borrower credit, loan structure, property, income/assets, normalize, submit, replay package.
- Smoke result: final status `READY_FOR_ELIGIBILITY`, version `7`, replay events `8`.
- Local Testcontainers tests remain blocked by Docker Desktop/Testcontainers named-pipe detection; runtime PostgreSQL validation passed in k3s.

## Next PII-01 Tasks
| ID | Task | Depends On | Status |
|---|---|---|---|
| P01-T01 | Add durable PostgreSQL/Flyway/JDBC persistence | none | implemented/runtime validated |
| P01-T02 | Implement canonical idempotency with request hash/conflict | P01-T01 | baseline implemented; canonical request hash hardening pending |
| P01-T03 | Make audit/outbox transactional with scenario writes | P01-T01 | implemented/runtime validated |
| P01-T04 | Add tenant/auth/RBAC facade and fail-closed checks | none | pending |
| P01-T05 | Implement immutable scenario version and section snapshots | P01-T01 | pending |
| P01-T06 | Implement channel submission profile | P01-T01, P01-T04 | pending |
| P01-T07 | Replace hard-coded catalogs with governed config lookup | P01-T06, PII-02 | pending |
| P01-T08 | Implement multipart async batch import | P01-T01, P01-T02 | pending |
| P01-T09 | Harden replay package with exact versions, redaction, hash verification | P01-T05, P01-T03 | pending |
| P01-T10 | Add OpenAPI, contract tests, and controller tests | P01-T01 | pending |
