# PII-38 Grid Reload Runbook

## Purpose

Reload versioned base-rate or GSE grid inputs without breaking quote launch, waterfall evidence, or audit replay.

## Preconditions

- New grid file has passed schema and version validation.
- The source version, effective date, investor, product family, and grid checksum are known.
- The previous active grid version remains available for replay and rollback.

## Procedure

1. Load the new grid as a new immutable version.
2. Validate row counts, effective dates, product family coverage, and checksum.
3. Run a dry quote launch for a representative scenario using the new grid version.
4. Activate the grid version for the intended tenant/product/investor scope.
5. Invalidate scoped rate lookup cache entries.
6. Warm common lookup keys when supported.
7. Run the PII-38 quote-service validation wrapper and inspect evidence logs.

## Validation

- Rate lookup p99 remains `< 5ms` after warmup.
- Quote launch p99 remains `< 500ms` for 20 candidates.
- Waterfall output includes the expected rate/grid source refs.
- Audit replay for pre-reload quotes remains byte-for-byte stable for the replayed event payload.

## Rollback

Reactivate the previous grid version, invalidate scoped caches, and rerun the quote launch validation. Keep the failed grid version retained with status metadata for diagnosis; do not reuse its version identifier.
