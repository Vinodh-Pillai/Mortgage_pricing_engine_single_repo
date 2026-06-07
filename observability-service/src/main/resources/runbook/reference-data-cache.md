# Reference data cache runbook

The reference data cache uses deterministic keys of the form
`wcp:{env}:tenant:{tenantId}:reference:{dataset}:v{schemaVersion}:{referenceDataVersion}:{asOfDate}`.

Source read models remain authoritative. The cache service resolves exactly one `PUBLISHED` tenant/dataset/version/effective-window row before reading a cached snapshot, so `DRAFT`, `SUSPENDED`, and `ROLLED_BACK` versions are not served from cache. If the cache store is unavailable, the service records fallback diagnostics and returns source metadata rather than inventing reference data.

Stale-data response:

1. Inspect the active published version graph for the tenant and dataset.
2. Verify `CacheInvalidationRequested.v1` exists for the source `ReferenceDataPublished.v1`, `ReferenceDataSuspended.v1`, or `ReferenceDataRolledBack.v1` event.
3. Warm the affected tenant/dataset/version after the source read model is corrected.
4. Treat ambiguous active versions as fail-closed policy issues until the source read model is corrected.
