# PII-38 Circuit Breaker Reset Runbook

## Purpose

Recover parallel candidate pricing after an upstream adjustment, rate, margin, or product dependency causes the quote-service pricing circuit breaker to open.

## Preconditions

- Confirm the dependency incident is resolved or isolated.
- Capture the current circuit breaker state, failure rate, affected tenant/product scope, and recent quote launch errors.
- Confirm the reset target is local/dev or has release approval.

## Procedure

1. Stop new broad load against the affected dependency while preserving existing audit/outbox evidence.
2. Verify dependency health with the service-specific health or smoke endpoint.
3. Run a single quote launch for a small candidate set.
4. If the small launch succeeds, reset the pricing circuit breaker through the approved operational endpoint or service restart procedure for the target environment.
5. Run the PII-38 quote launch validation with 20 candidates.
6. Resume normal traffic gradually and watch failure rate, bulkhead saturation, p50, and p99 latency.

## Validation

- Circuit breaker state returns to closed or half-open then closed after successful probes.
- Parallel pricing success rate is `> 99%`.
- Quote launch p99 remains `< 500ms` for 20 candidates.
- No audit replay payload changes are introduced by the reset.

## Rollback

If failures recur, stop the reset attempt, keep the circuit open, route traffic through the configured degraded path, and preserve the failure window evidence for staff-engineer/tester review.
