# PII-12-S10 Config Import/Export Runbook

## Scope

This service-local slice covers governed import dry-runs, draft creation from validated profile-driven bundles, redacted export manifest generation, package hash evidence, and audited downloads in `governance-service`.

## Failure scenarios

- Failed upload or unsafe file name: reject the command before side effects and return `VALIDATION_FAILED` or `POLICY_NOT_SATISFIED` with no outbox/audit mutation.
- Validation failures: keep the import job as `VALIDATION_FAILED`, return sorted blocking findings, and create no draft refs.
- Idempotency replay: return the existing import/export job when the tenant-scoped idempotency key and request hash match; return `IDEMPOTENCY_CONFLICT` when the same key carries a changed request.
- Stale package: reject download with `PACKAGE_EXPIRED`; regenerate through a new export command using a current export profile.
- Redaction mismatch: treat the export profile as the source of truth. Add or correct profile redacted fields rather than hard-coding artifact fields in code.

## Recovery notes

- Import dry-run is safe to retry with the same idempotency key when the request is unchanged.
- Draft creation never publishes; publication remains owned by the lifecycle approval flow.
- Export packages are represented by manifest and hash evidence in this slice. Binary object/blob persistence remains an external platform integration gap.
