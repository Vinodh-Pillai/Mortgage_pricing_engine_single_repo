# PII Readiness Matrix

| PII | Independent Readiness | Can Implementation Start? | Primary Service |
|---|---|---:|---|
| PII-01 Scenario Intake | PostgreSQL baseline deployed, hardening gaps remain | Yes, hardening | `scenario-service` |
| PII-02 Product and Investor Catalog | PostgreSQL lifecycle baseline deployed, hardening gaps remain | Yes, hardening | `catalog-service` |
| PII-03 Eligibility / Conventional Quote Shell | PostgreSQL quote/eligibility baseline deployed, hardening gaps remain | Yes, hardening | `eligibility-service` |
| PII-04 Rate Sheet and Feed Ingestion | Ready with fixtures | After PII-02 | `rate-feed-ingestion-service` |
| PII-05 Base Pricing | Conditional | After PII-03/04 | `pricing-service` |
| PII-06 Adjustments and LLPA | Ready | After PII-05 | `adjustment-service` |
| PII-07 Margins and Compensation | Conditional | After PII-06 | `margin-service` |
| PII-08 Quote and Best Execution | Conditional orchestrator | After PII-03/05/06/07 | `quote-service` |
| PII-09 What-If Analysis | Conditional | After PII-01, meaningful after PII-08 | `scenario-analysis-service` |
| PII-10 Lock Desk | Conditional | After PII-08 | `lock-service` |
| PII-11 Concessions and Exceptions | Conditional | After PII-07/08 | `exception-service` |
| PII-12 Administration and Governance | Conditional | Can start core lifecycle after PII-02 | `governance-service` |
| PII-13 Audit and Replay | Conditional | Minimal now, full after quote/lock/governance | `audit-replay-service` |
| PII-14 ML Advisory | Conditional | Shell can start later; needs audit/compliance gates | `ml-advisory-service` |
| PII-15 Compliance and Fair Lending | Conditional | After quote/lock/concession evidence | `compliance-service` |
| PII-16 Integrations | Conditional | Adapter shell can start with mocks | `integration-service` |
| PII-17 Observability and Performance | Conditional | Minimal now, full after real workflows | `observability-service` |
| PII-18 Security and Platform | Not independently ready as a whole | Bootstrap slice required first | `security-platform` |

## PII-04 Developer Implementation Evidence (2026-05-17)
- Developer implementation for the rate-feed unblock is complete in `projects/rate-feed-service` with JSONB PGobject binding, deterministic idempotency conflict checks, validation hardening, non-destructive V2 compatibility views, unit tests, and a wcpe-dev manifest.
- Evidence: `.agent-runtime/current/evidence/pii-04-rate-feed-unblock/developer-implementation-evidence.md` and `.agent-runtime/current/evidence/pii-04-rate-feed-unblock/synthetic-validation-matrix.md`.
- PII-05 remains blocked pending staff-engineer review, tester/integration/security/release validation, non-prod deployment/runtime smoke evidence, and product-owner closure recommendation.

## Principal Planner Continuation Refresh (2026-05-17)
- Dependency order is unchanged from `projects/implementation-tracker/implementation-dag.md`.
- Immediate critical path: PII-04 must be fixed, deployed to non-prod/dev, smoke validated, and synthetic-data validated before PII-05 starts.
- PII-05 remains blocked until PII-03 and PII-04 runtime/contract evidence exists.
- PII-01/02/03 hardening may proceed in parallel only inside scoped task packets and without changing downstream dependency gates.
- PII-13/17/18 minimal foundation slices may proceed only for audit/event, observability/correlation, and tenant/security platform prerequisites; full PII completion remains gated by the DAG.
- Every PII completion gate must include synthetic-data validation, screenshot/visual evidence, agent agreement/reviews, non-prod deployment/runtime evidence, and final detailed PDF documentation references.

## Next Route Summary
| Route | PIIs | Gate |
|---|---|---|
| Parallel hardening | PII-01, PII-02, PII-03 | Already deployed/smoke-passed; story hardening only. |
| Critical unblock | PII-04 | Must complete before PII-05. |
| Waterfall pricing | PII-05, PII-06, PII-07, PII-08 | PII-04 -> PII-05 -> PII-06 -> PII-07 -> PII-08. |
| Downstream workflows | PII-09, PII-10, PII-11, PII-12, PII-13, PII-15, PII-16 | Follow existing DAG prerequisites and contract evidence. |
| Governed advisory | PII-14 | Non-authoritative; after audit/compliance controls. |
| Readiness foundations | PII-13, PII-17, PII-18 | Minimal foundations now; full completion later per DAG. |

## Key Independence Findings
- All 180 story files contain `Independent Implementation Contract` sections.
- PII-18 still has intra-PII circular dependencies and needs a bootstrap split.
- PII-03, PII-08, PII-10, PII-13, PII-15 depend on upstream implemented contracts and should not be built as isolated full PIIs first.
- PII-02 is the best next PII because it unlocks eligibility, feeds, pricing, quote orchestration, and governance.
