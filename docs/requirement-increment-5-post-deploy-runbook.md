# Requirement Increment 5 Post-Deploy Contracts, Performance, and Gap-Review Runbook

Scope: project-local instructions and evidence checklist for `NextInput/5.md` contract compatibility, pricing performance, synthetic/captured catalog data, post-deployment API/UI validation, and modularization regression guardrails. This runbook does not authorize Docker, Kubernetes, external network, or secret access by itself.

## Contract Compatibility

- Local contract artifact: `projects/quote-service/src/main/resources/openapi/loanpass-quote-api.yml`.
- Approved public endpoint semantics for QuickQuote mapping: LoanPass `execute-summary` (`https://api.loanpass.io/v1/swagger/#tag/pricing/post/execute-summary`) is the product-candidate listing boundary; LoanPass `execute-product` (`https://api.loanpass.io/v1/swagger/#tag/pricing/post/execute-product`) is the single-product detail boundary after a product id/selected program is known.
- Remaining external blocker: no approved public LoanPass swagger/schema artifact is committed at `projects/quote-service/src/test/resources/loanpass/public-swagger.json` or another repo-local path for byte-for-byte field-level diffing. Do not fetch `https://api.loanpass.io/v1/swagger/schema.json` or public docs over the network from this lane.
- Current evidence allowed in-repo: structural/local compatibility only for `/api/v1/loanpass/execute-summary`, `/api/v1/loanpass/executeSummary`, `/api/v1/loanpass/execute-product`, and `/api/v1/loanpass/executeProduct`, with endpoint roles fixed as summary=list and product=single-product detail.
- Next unblocked step: commit an approved public LoanPass swagger/schema artifact with provenance, then add an offline structural diff test comparing required request/response fields, operation ids, status codes, and header semantics.

## Synthetic and Captured Catalog Data

- Synthetic loader: `projects/quote-service/src/main/java/com/wcpe/quote/LoanPassSyntheticCatalogLoader.java` uses `quote.loanpass.synthetic-loader.*` properties and records `generatorVersion`, `seed`, `schemaVersion`, payload hash, and `devOnly=true` metadata.
- Captured loader: `projects/quote-service/src/main/java/com/wcpe/quote/LoanHouseCapturedCatalogLoader.java` loads `classpath:loanhouse/product-records.json`, records source artifact/hash metadata, and preserves captured product/rate/lock/source refs without inferring production pricing rules.
- Do not add production rates, rule thresholds, investor decisions, tenant policy, or eligibility constants to fixtures. New datasets need a seed, generator/capture version, source artifact path, payload hash, and privacy note.

## Pricing Performance / k6

1. Start only an approved local/dev quote-service stack with its normal PostgreSQL/Redis/broker dependencies.
2. Seed approved tenant snapshots and reference-data versions. The committed k6 fixture has UUID tenant ids and source refs only.
3. From `projects/observability-service`, run:

   ```bash
   PRICING_BASE_URL=http://<local-dev-quote-host> \
   PRICING_LOAD_VUS=1 \
   PRICING_LOAD_DURATION=1m \
   k6 run src/test/resources/loadtest/PII-17-S07/pricing-load-test.k6.js
   ```

4. The script targets `POST /api/v1/tenants/{tenantId}/quotes` using default `PRICING_API_PREFIX=/api/v1` and `PRICING_QUOTE_PATH=/quotes`; the previous `/pricing/quotes` path is obsolete.
5. Save k6 summary JSON, logs, quote-service build/version, active snapshot/version refs, cache mode, and any dashboard exports under `.local-harness/evidence/requirement-increment-5/`.
6. Record p50/p95/p99 by scenario type plus cache hit/miss, product count, rule count, DB time, serialization time, upstream timeout/error budget, and backpressure activations when the local/dev stack exposes those metrics.

## Post-Deployment API Smoke Checklist

Run these only against a local/dev deployment with approved credentials/config already present in that environment. Do not print secrets.

- Quote create smoke: `POST /api/v1/tenants/{tenantId}/quotes` with approved seeded scenario refs.
- LoanPass summary smoke: `POST /api/v1/loanpass/execute-summary` or `/executeSummary` with approved tenant/body ids.
- LoanPass product smoke: `POST /api/v1/loanpass/execute-product` or `/executeProduct` with a product id present in the active snapshot.
- Lock workflow smoke: exercise lock-service request/confirm/extend/cancel APIs only when local/dev lock-service and audit evidence are available.
- Capture response status, correlation id, tenant id, run/quote/job id, audit refs, snapshot refs, and any blocked dependency codes. Do not copy borrower PII or secret-bearing headers into evidence.

## Post-Deployment UI / Playwright Checklist

1. Build or deploy backend/BFF services through the approved release lane.
2. Port-forward the BFF from the local/dev Kubernetes namespace and record command, PID/log, namespace, service, local port, and health response.
3. Run the UI locally against the port-forwarded BFF.
4. Execute live Playwright flows, not mock-only evidence, for: login/persona navigation, Quick Quote launch, pricing analysis, pricing waterfall, quote journey map, lock request/confirm/extend/cancel, rate sheet intake upload/validate/publish, tenant fields draft/publish/audit, partner lifecycle, user settings/profile, and error/blocker states.
5. Save screenshots, traces, console/network evidence, API smoke outputs, and a gap review under `.local-harness/evidence/requirement-increment-5/` and the project-native Playwright output folders.
6. Mock-based specs such as `projects/pricing-workbench-ui/tests/e2e/live/live-functional-workflows.spec.ts` remain useful for local UI regression but are not sufficient to close live post-deployment gaps.

## Modularization Regression Guardrails

- Before extracting route packages, BFF adapters, or backend service layers, add or update behavior-preserving tests for the affected route/API/service boundary.
- First extraction pass must keep public routes, request/response shapes, audit refs, fallback/blocker labels, and screenshot-visible behavior unchanged.
- Use existing quote-service regression tests and live/mock Playwright specs as baselines. Add new tests before moving code; do not combine extraction with business-rule changes.

## Gap Review Outcome Format

For each scenario, record: route/API, tenant/run/quote ids, command or Playwright spec, screenshot/trace path, expected behavior, observed behavior, pass/fail, blocker class, repo-supported fix, and non-repo environment blocker if any.
