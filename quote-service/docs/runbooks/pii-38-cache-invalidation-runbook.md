# PII-38 Cache Invalidation Runbook

## Purpose

Invalidate quote/pricing cache entries after a versioned rate sheet, adjustment rulebook, margin schedule, overlay configuration, or tenant product authorization change.

## Preconditions

- Confirm the change is for a local/dev or approved release environment.
- Identify the affected tenant, pricing version, adjustment version, margin version, product family, investor, and channel.
- Preserve the prior version artifact so quote replay can continue to resolve historical inputs.

## Procedure

1. Publish the new versioned source artifact first. Do not overwrite the prior version in place.
2. Invalidate L1 entries scoped to the affected tenant and version tuple.
3. Invalidate L2 entries scoped to the affected tenant and version tuple.
4. Emit or verify the cache invalidation event for downstream services.
5. Warm the high-volume rate/product keys for the affected tenant when a warmup tool is available.
6. Launch a scoped quote validation and confirm the quote input version set references the new version tuple.

## Validation

- Quote launch returns `READY`.
- Response options include the expected pricing, adjustment, and margin version refs.
- Cache metrics recover above the PII-38 targets after warmup: L1 hit rate `> 95%`, L2 hit rate `> 99%`.
- Audit replay for quotes created before invalidation still uses the historical version tuple.

## Rollback

Republish or reactivate the prior version tuple, invalidate the same scoped cache keys again, and rerun quote launch validation. Do not delete historical replay artifacts.
