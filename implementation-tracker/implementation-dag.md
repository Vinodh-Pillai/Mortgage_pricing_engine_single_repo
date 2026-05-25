# Implementation DAG

```mermaid
flowchart TD
  P18M[PII-18 minimal platform slice]
  P13M[PII-13 minimal audit/event slice]
  P17M[PII-17 minimal observability slice]

  P01[PII-01 Scenario Intake]
  P02[PII-02 Product and Investor Catalog]
  P03[PII-03 Eligibility]
  P04[PII-04 Rate Feed Ingestion]
  P05[PII-05 Base Pricing]
  P06[PII-06 Adjustments and LLPA]
  P07[PII-07 Margins and Compensation]
  P08[PII-08 Quote and Best Execution]
  P09[PII-09 What-If Analysis]
  P10[PII-10 Lock Desk]
  P11[PII-11 Concessions and Exceptions]
  P12[PII-12 Administration and Governance]
  P13[PII-13 Audit and Replay]
  P14[PII-14 ML Advisory]
  P15[PII-15 Compliance and Fair Lending]
  P16[PII-16 Integrations]
  P17[PII-17 Observability and Performance]
  P18[PII-18 Security and Platform]

  P18M --> P01
  P13M --> P01
  P17M --> P01
  P01 --> P02
  P01 --> P09
  P02 --> P03
  P02 --> P04
  P02 --> P12
  P03 --> P05
  P04 --> P05
  P05 --> P06
  P06 --> P07
  P07 --> P08
  P08 --> P09
  P08 --> P10
  P08 --> P11
  P08 --> P13
  P08 --> P14
  P08 --> P15
  P08 --> P16
  P08 --> P17
  P11 --> P10
  P11 --> P15
  P10 --> P13
  P10 --> P15
  P10 --> P16
  P04 --> P12
  P06 --> P12
  P07 --> P12
  P12 --> P13
  P12 --> P15
  P13 --> P15
  P13 --> P16
  P13 --> P14
  P15 --> P14
  P16 --> P17
  P17 --> P18
```

## Milestone Gates
| Gate | Scope | Exit Criteria |
|---|---|---|
| M0 Foundation | Minimal tenant/auth, audit/event, observability | Tenant context, idempotency, event envelope, audit refs, correlation IDs. |
| M1 Scenario Foundation | PII-01 | Durable scenario lifecycle, immutable snapshots, validation, replay. |
| M2 Product Catalog | PII-02 | Versioned products/investors/channels with active lookup. |
| M3 Eligibility Shell | PII-03 | Eligibility decisions and reason codes over scenario/catalog. |
| M4 Active Rate Pricing | PII-04 + PII-05 | Published feeds resolve deterministic base prices. |
| M5 Waterfall Pricing | PII-06 + PII-07 | LLPAs, overlays, fees, margins, comp, explanation ledger. |
| M6 Quote Orchestration | PII-08 | Ranked quote options with replay hash and audit. |
| M7 Downstream Workflow | PII-10 + PII-11 | Locks and concessions with approvals and freshness checks. |
| M8 Governed Operations | PII-12 + PII-13 | Config lifecycle and replayable evidence. |
| M9 Enterprise Controls | PII-15 + PII-16 | Compliance monitoring and integrations. |
| M10 Production Readiness | PII-17 + PII-18 | SLOs, security, tenant isolation proof, runbooks. |
| M11 Advisory Intelligence | PII-14 | Non-authoritative governed ML advisory. |

## Runtime Validation Receipts
- 2026-05-15: PII-01 `scenario-service` deployed to k3s, RBAC fail-closed and replay role validation passed.
- 2026-05-15: PII-02 `catalog-service` deployed to k3s, lifecycle/RBAC smoke passed; publish returned `PUBLISHED` version `9`.
- 2026-05-16: PII-03 `eligibility-service` deployed to k3s, quote endpoint and loan-limit rule endpoint smoke passed; RBAC denied missing role with HTTP `403`.
- 2026-05-17: PII-02 `catalog-service:0.1.4` deployed to k3s, Flyway migrated `catalog` to v3, `/versions` returned a per-artifact version-control row.
- 2026-05-17: PII-03 `eligibility-service:0.1.2` deployed to k3s, Flyway migrated `eligibility` to v2, quote explanation endpoint returned `ELIGIBLE` with two governed rule decisions.
