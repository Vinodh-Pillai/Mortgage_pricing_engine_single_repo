# PII-17-S08 Rate Limiting Runbook

## Scope

This service-local slice evaluates tenant-scoped rate-limit policies, emits headers/problem responses, records audit/event metadata, and fails closed when required configuration is missing. It does not store raw tokens, API keys, borrower data, or mortgage pricing payloads.

## Operational steps

1. Identify the tenant, hashed principal, endpoint group, policy key, policy version, decision, remaining capacity, reset time, and correlation ID from `rate_limit_decision_audit` or the emitted event envelope.
2. Confirm whether the request used the primary counter store or `REDIS_FALLBACK_ACTIVE` degraded mode.
3. For authenticated business quote reads, verify that any emergency fallback cap came from a published tenant policy; do not invent or edit caps directly in runtime counters.
4. For high-risk ops/admin endpoints, treat counter-store outage as fail-closed until Redis or the configured primary counter store recovers.
5. Use the admin policy workflow to publish or roll back a policy; never edit counters manually in production.

## Rollback

Rollback by publishing an approved prior policy version. Database rollback for local/dev can drop `rate_limit_decision_audit` and `rate_limit_policy` after dropping their indexes.
