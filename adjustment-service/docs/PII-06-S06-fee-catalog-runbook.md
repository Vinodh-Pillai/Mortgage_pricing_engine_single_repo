# PII-06-S06 Fee Catalog Notes

## Implemented slice

- `FeeCatalogVersion` models tenant-scoped catalog lifecycle, publish separation of duties, suspend and rollback transitions, immutable published catalogs, event/audit metadata, applicability-based fee resolution, formula-parameter validation, and precision policy.
- `V6__fee_catalog.sql` adds service-owned `fee_catalog_versions`, `fee_definitions`, and `fee_catalog_audit` tables with tenant keys, effective windows, lifecycle checks, JSONB formula/applicability fields, and indexes.
- Golden fixtures under `golden/PII-06-S06-fee-catalog/` document create, publish event, and invalid formula error shapes.

## Operational behavior

- No fee amounts, LLPA rates, investor policies, or jurisdictional values are encoded in Java source. Fee formulas use configuration references such as `amountConfigRef`, `percentConfigRef`, and `approvedExpressionRef`.
- Missing formula/configuration references fail closed through domain validation.
- Published fee catalogs require an approver different from the requester and cannot overlap another published tenant catalog effective window.
- Local domain events cover `FeeCatalogPublished` and `FeeCatalogRolledBack` with event id, event/schema version, source service, occurred-at timestamp, effective window, snapshot URI/hash, and fee-code list hash fields. Runtime outbox infrastructure remains responsible for transport and persistence.

## Validation

Run from `projects/adjustment-service`:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ..\..\.local-harness\evidence\PII-06-S06\run-adjustment-service-tests.ps1
```

The wrapper runs Gradle from `projects/adjustment-service` and writes `.exit`, `.done`, stdout, stderr, and combined logs under `.local-harness/evidence/PII-06-S06/`.

## Deferred integration notes

- REST controllers, admin UI screens, runtime outbox publisher wiring, cache invalidation listeners, and service-to-service S07 fee calculation wiring are not present in the current adjustment-service skeleton. The domain model, schema, event shape, and fixtures define the contract for those future framework-backed slices.
