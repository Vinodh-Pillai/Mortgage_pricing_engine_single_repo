# PII-10-S01 Lock Request Runbook

## Stale Quote Spike
Check quote snapshot freshness policy references and recent quote/pricing feed delays. Do not widen freshness windows in code; adjust tenant-governed configuration only.

## Market Or Investor Suspension
Confirm the active tenant/channel/product/investor policy version and suspension source. Requests must fail closed while suspension is active.

## Outbox Lag
Inspect `lock.requested.v1` backlog by tenant and event key. Committed locks remain valid; integrations recover through outbox retry.

## Duplicate Idempotency
Replay matching tenant/idempotency payloads. Return `IDEMPOTENCY_CONFLICT` for mismatched payloads and keep the original lock unchanged.

## Config Overlap Or Policy Resolution Failure
Treat missing, overlapping, or ambiguous tenant/channel/product/investor config as `POLICY_NOT_SATISFIED`. Resolve configuration, then replay from audit/replay evidence.
