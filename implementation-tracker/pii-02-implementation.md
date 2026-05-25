# PII-02 Implementation Status

## Current Baseline
`projects/catalog-service` has been advanced beyond the initial in-memory baseline. Runtime persistence is now designed around PostgreSQL with Flyway/JDBC, Kubernetes manifests, and Testcontainers integration tests.

Current status: **PostgreSQL baseline plus lifecycle/idempotency/audit/snapshot retrieval and version-control foundation implemented and deployed to k3s. Runtime smoke validation passed. PII-02 still requires remaining story-specific governance/RBAC/contract hardening before full completion.**

Implemented baseline:
- Gradle Spring Boot service scaffold.
- PostgreSQL/Flyway/JDBC dependencies.
- Product definition command/API.
- Investor program command/API.
- Reference catalog APIs for taxonomy, channels, term/amortization, property type, occupancy type, and loan purpose.
- Market import API.
- Publish catalog command/API.
- Active catalog lookup API.
- Product config snapshot resolve API.
- PostgreSQL-backed repository replacing runtime in-memory storage.
- Durable outbox table for catalog events.
- SQL migration artifact for catalog, references, markets, snapshots, idempotency, outbox, and audit tables.
- Kubernetes manifests for dev PostgreSQL and service deployment.
- Golden fixture and PostgreSQL Testcontainers integration tests.
- Dockerfile for `catalog-service:0.1.0` image build.
- Actuator readiness/liveness probe configuration.
- Kubernetes namespace manifest and dev deployment manifest.
- Lifecycle action endpoints for validate, submit approval, approve, reject, publish, suspend, and retire.
- Request idempotency records with same-key replay and different-payload conflict behavior.
- Audit table writes for accepted catalog commands.
- Snapshot retrieval by `snapshotId`.
- Governance hardening migration `V3__catalog_governance_hardening.sql` with `catalog_version_control`, validation issue storage, row versions, and effective-window constraints.
- Per-artifact version-control rows for products, investors, references, and markets.
- `GET /api/v1/tenants/{tenantId}/product-catalog/versions` endpoint.

## Story Completion Matrix
| Story | Status | Notes |
|---|---|---|
| PII-02-S01 Product Taxonomy | Partial | Implemented as generic `reference_entry` API; missing dedicated taxonomy aggregate/version table, hierarchy rules, lifecycle, cache, and contract tests. |
| PII-02-S02 Conventional Product Definition | Partial | Product create/list/resolve baseline exists; missing full conventional product attributes, ARM/fixed invariants, loan limits, allowed value tables, and six seeded MVP products. |
| PII-02-S03 Channel Taxonomy | Partial | Implemented as generic reference API; missing source mappings, no-default-to-retail rule, branch/channel assignment checks, and dedicated resolve contract. |
| PII-02-S04 Investor Catalog | Partial | Investor create baseline exists; missing seller/servicer encryption/masking, delivery types, agency metadata, suspension, and secret-view RBAC. |
| PII-02-S05 Program Versioning | Partial | Lifecycle endpoints now exist for validate, submit approval, approve, reject, publish, suspend, retire, rollback, SoD enforcement, optimistic version checks, and per-artifact version rows. Still missing full contract coverage and deeper story-specific governance rules. |
| PII-02-S06 Term and Amortization Model | Partial | Implemented as generic reference API; missing fixed/ARM constraints and dedicated profile resolve. |
| PII-02-S07 Property and Occupancy Types | Partial | Implemented as generic reference APIs; missing dedicated constraints for condo, manufactured home, 2-4 unit, and occupancy rules. |
| PII-02-S08 Loan Purpose Catalog | Partial | Implemented as generic reference API; missing refinance/cash-out/purchase invariants and alias mapping. |
| PII-02-S09 State County Market Catalog | Partial | Single market import API exists; missing batch import, FIPS source validation, state/county override workflow, restricted market handling. |
| PII-02-S10 Product Config API | Partial | Snapshot resolve/materialization and retrieval by ID exist. Still missing exact component version hashes, includeInactive permission, and full fail-closed component checks. |

## Remaining Tasks
| ID | Task | Status |
|---|---|---|
| P02-T02 | Replace in-memory repository with PostgreSQL/Flyway/JDBC persistence | implemented, needs runtime validation |
| P02-T03 | Add effective-dated active lookup by date/channel/state/product family | implemented baseline, needs story-specific expansion |
| P02-T04 | Add tenant/auth/RBAC, idempotency, audit/outbox, and correlation headers | partial: outbox implemented; auth/RBAC/idempotency pending |
| P02-T05 | Expand stories for product lifecycle, investor overlays, channel/state availability, historical replay, and contract tests | pending |
| P02-T06 | Build and publish/load `catalog-service:0.1.0` image for Kubernetes | implemented/runtime validated |
| P02-T07 | Run Testcontainers and controller contract tests | local Testcontainers blocked by Docker Desktop named-pipe detection; runtime PostgreSQL smoke passed |
| P02-T08 | Complete Kubernetes smoke test HTTP flow | implemented/runtime validated |
| P02-T09 | Add rollback, SoD, optimistic locking, and dedicated per-artifact version rows | implemented baseline/runtime validated |
| P02-T10 | Replace generic references with story-specific schemas/constraints where required | pending |

## Runtime Validation 2026-05-15
`kubectl` is available by absolute path and the Kubernetes manifests are applied. PostgreSQL and `catalog-service` are running in namespace `wcpe-dev`.

Build/deploy record:
- Installed local Gradle 8.10.2 under `C:\Users\vinog\AppData\Local\Temp\opencode\gradle\gradle-8.10.2`.
- Installed/started Docker Desktop and local registry container `localhost:5000`, exposed to k3s as `192.168.4.93:5000`.
- Configured k3s `/etc/rancher/k3s/registries.yaml` for HTTP mirror `192.168.4.93:5000` and restarted k3s.
- Built/pushed `192.168.4.93:5000/catalog-service:0.1.0`.
- Built/pushed hardened image `192.168.4.93:5000/catalog-service:0.1.4`.
- Runtime smoke flow passed: channel, loan purpose, product, investor, validate, submit approval, approve, publish, active retrieval, snapshot resolve.
- Latest smoke result: catalog status `PUBLISHED`, version `9`, snapshot hash `sha256:0cd1d764df4f2b4f70a23b329b9af163bc4fd3d4cb5fcdb16562a1a8c3910152`.
- 2026-05-17 hardening smoke result: `catalog-service:0.1.4` rolled out, Flyway migrated schema `catalog` to v3, channel draft created with catalog version `2`, `/versions` returned one `DRAFT` version-control record.

Remaining validation blocker:
- Local Testcontainers tests fail because Java/Testcontainers does not detect Docker Desktop's Linux engine named pipe. Docker CLI and k3s runtime validation work.

Cluster status:
- Namespace `wcpe-dev`: created.
- PostgreSQL StatefulSet: rollout complete.
- Catalog service Deployment: rollout complete on image `192.168.4.93:5000/catalog-service:0.1.4`.

## Kubernetes Deployment Record
Applied with absolute kubectl path:
`C:\Users\vinog\AppData\Local\Programs\kubectl\kubectl.exe`

Applied resources:
- `projects/catalog-service/k8s/namespace.yaml`
- `projects/catalog-service/k8s/catalog-dev.yaml`

Observed resources:
- `pod/catalog-postgres-0`: PostgreSQL rollout completed.
- `pod/catalog-service-*`: `1/1 Running`.
- `pvc/catalog-postgres-data`: bound to `local-path` storage class.
- `svc/catalog-postgres`: headless service on port `5432`.
- `svc/catalog-service`: ClusterIP service on port `8082`.

Image pull issue resolved by registry-qualified image `192.168.4.93:5000/catalog-service:0.1.0` and k3s registry mirror configuration.

## Required Commands To Unblock Runtime Validation
Run from a terminal with Gradle and Docker available:

```powershell
cd "C:\Users\vinog\ Documents\Requirements\Mortgage Pricing\projects\catalog-service"
gradle test
gradle bootJar
docker build -t catalog-service:0.1.0 .
```

Then load/push the image for the cluster runtime and restart:

```powershell
kubectl rollout restart deployment/catalog-service -n wcpe-dev
kubectl rollout status deployment/catalog-service -n wcpe-dev
kubectl port-forward -n wcpe-dev svc/catalog-service 8082:8082
```

Smoke test target:
`http://localhost:8082/api/v1/tenants/018fa4f0-1a4f-7e99-a02d-1b0100010001/product-catalog`

## Review Findings Closed
- Added missing `CatalogException`.
- Fixed investor `channels` JSONB cast.
- Replaced short `hashCode()` hash with SHA-256 format.
- Changed `/active` to require a published catalog.
- Added `/current` for editable draft/current state.
- Added reference matching for loan purpose, property type, occupancy type, term, and amortization in snapshot resolution.
- Added `PGDATA` subdirectory and namespace manifest for Kubernetes.
- Added lifecycle transition endpoints and strict publish-from-approved rule.
- Added idempotency replay/conflict support backed by PostgreSQL.
- Added audit persistence for accepted commands.
- Added snapshot retrieval endpoint.
- Fixed advisory lock runtime issue found in final review.
- Fixed null reason `Map.of` runtime issue found in final review.
- Fixed snapshot audit/event catalog association to use the active published catalog.

## Remaining High-Risk Gaps
- PII-02 is still not story-complete because deeper lifecycle governance, contract tests, and dedicated catalog schemas remain incomplete.
- Lifecycle governance remains incomplete for production despite baseline rollback, SoD, optimistic locking, and version rows; story-specific approvals and validation rules still need expansion.
- Auth/RBAC and service-account enforcement are not implemented.
- Current implementation uses generic `reference_entry` for several story-specific catalogs. This is acceptable as a baseline but not final PII-02 completeness.
- Full runtime validation has not run because Gradle/Docker are unavailable and app image is not deployed.
