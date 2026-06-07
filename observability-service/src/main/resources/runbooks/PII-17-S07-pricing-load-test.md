# PII-17-S07 Pricing Load Test Runbook

Scope: observability-service-owned local load-test plan, synthetic fixtures, k6 script, SLO gate definitions, and evidence checklist for conventional MVP pricing performance.

## Preconditions

- Use only a local/dev stack. Do not target production or shared environments.
- Provide `PRICING_BASE_URL` at runtime. The script intentionally has no default URL, port, token, or credential.
- Seed tenant-scoped pricing/reference configuration from approved fixture or service data. Do not invent mortgage rates, fees, investor decisions, eligibility thresholds, or tenant policy.
- Do not use real borrower PII. The committed fixture uses synthetic tenant labels and immutable source refs only.

## Scenario Mix

- Warm cache repeat quotes: 40%.
- Cold cache unique quotes: 25%.
- Reference data version changes: 10%.
- Intentional invalid requests: 10%.
- Multi-tenant parallel: 10%.
- Redis degraded/fallback: 5%.

## SLO Gates From Story

- Warm cache quote API p95 <= 500 ms.
- Cold cache quote API p95 <= 1500 ms.
- p99 <= 2500 ms under target load.
- Error rate < 0.5%, excluding intentional validation cases.
- Repeat deterministic scenario cache hit ratio >= 80%.
- DB hot-path sequential scans = 0.
- Rate limiting returns 429 with retry metadata when threshold is exceeded.

## Execution

1. Start the local/dev Docker stack for pricing API, PostgreSQL, Redis, broker, and metrics if available.
2. Seed synthetic tenants, scenario refs, pricing config refs, and reference-data version refs.
3. Run `k6 run projects/observability-service/src/test/resources/loadtest/PII-17-S07/pricing-load-test.k6.js` with `PRICING_BASE_URL`, profile, VU, duration, and sleep environment variables.
4. Save raw summary JSON, HTML report, dashboard export, EXPLAIN plans, and environment config under the story evidence folder.
5. Classify bottlenecks as application, DB, Redis, broker, rate-limit, or backpressure before accepting SLO evidence.

## Local Slice Limits

- This lane does not start Docker, CI, live pricing-service, Redis, broker, UI dashboard, or performance infrastructure.
- Full baseline/stress/degradation evidence is blocked until those runtimes are available in a local/dev stack.
