# PII-38-S01 Pricing Engine Master Architecture

## Scope

PII-38-S01 ties the completed PII-33 through PII-37 pricing capabilities into one quote launch path. It does not add new mortgage pricing rules. The master story validates that configured rate data, adjustment rules, margin/overlay inputs, parallel candidate pricing, tenant product authorization, waterfall evidence, best execution ranking, and audit replay remain deterministic when used together.

## Runtime Flow

1. `quote-service` receives a quote launch request and validates tenant, scenario, actor, idempotency, scenario version, lock periods, and effective date.
2. Product candidates are discovered through `QuoteDependencies.candidatesFor` and filtered through `authorizedCandidatesFor` for tenant product authorization.
3. `ParallelPricingOrchestrator` prices authorized candidates concurrently behind a Resilience4j bulkhead and circuit breaker.
4. Each candidate builds an adjustment request with scenario facts, product family, investor, channel, lock period, note rate, base price, and pricing versions.
5. The adjustment path returns versioned adjustment lines. The quote candidate carries `adjustmentResultHash`, `adjustmentVersion`, and adjustment audit refs into the option waterfall.
6. `BestExecutionRanker` applies configured ranking criteria and tie breakers. It does not hard-code investor preference or pricing rules.
7. Each `QuoteOption` records base price, adjustment lines, margin, final price, rank, reasons, warnings, upstream refs, and expiration.
8. `QuoteApplicationService` stores the quote snapshot, cache entry, audit entries, and outbox events including `quote.ready.v1`, `best_execution.ranked.v1`, and `quote.snapshot_created.v1`.
9. Audit replay reads a stored outbox event, emits a replay-marked event with the original payload, and records the replay request audit trail.

## Performance Targets Covered By Local Test Harness

`projects/quote-service/src/test/java/com/wcpe/quote/MasterPricingEngineIntegrationTest.java` covers the master-path targets with deterministic in-memory adapters:

| Target | Local assertion |
| --- | --- |
| Quote launch p99 `< 500ms` for 20 candidates | 100-launch concurrent test records p99 elapsed time. |
| Quote launch p50 `< 200ms` for 20 candidates | 100-launch concurrent test records p50 elapsed time. |
| Rate lookup p99 `< 5ms` | Synthetic L1/L2 rate probe records p99 lookup time. |
| Adjustment engine p99 `< 10ms` | Synthetic adjustment port records p99 calculation time. |
| L1 cache hit rate `> 95%` | Pre-warmed L1 probe asserts hit rate. |
| L2 cache hit rate `> 99%` | Pre-warmed L2 probe asserts hit rate. |
| Parallel pricing success rate `> 99%` | Candidate pricing invocations and failures are counted. |
| 100 concurrent quote launches stable | 100 concurrent quote launches each return `READY` with 20 options. |
| Waterfall/audit replay accuracy `100%` | Test verifies versioned waterfall lines and replay payload equality. |

## Concurrency Tuning Applied

The in-memory quote repositories, cache, audit queue, outbox queue, and selection/export maps now use concurrent collections so local concurrent quote launches do not fail due to adapter data races. The production path still uses the existing repository/cache abstractions; this change makes the built-in in-memory adapters safe for concurrent local validation and service smoke usage.

## Operational Boundaries

- No new pricing constants, thresholds, rates, investor preferences, or compliance rules are introduced.
- All pricing decisions in the master test are synthetic and versioned for test evidence only.
- Runtime deployment, Kubernetes, and shared infrastructure mutation are out of scope for this developer story.

## Validation Entry Point

Use the story-scoped wrapper from the project root:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .local-harness/evidence/PII-38-S01/run-quote-service-tests.ps1
```
