# PII-05 Published Grid Contract

## Overview

This contract defines the published rate grid format produced by PII-04 (Rate Feed Service) and consumed by PII-05 (Base Pricing). It establishes the schema, resolution semantics, and API endpoints for rate sheet consumption.

---

## Published Grid Schema

```
PublishedGrid {
  // Identity
  sheetId: UUID                              // Immutable rate sheet identity
  version: int                                // Monotonically increasing version number
  gridHash: string                            // SHA-256 of canonical grid content after sorting

  // Pricing dimensions
  investorId: UUID
  channelId: UUID
  productCode: string
  status: "ACTIVE" | "SUPERSEDED" | "REJECTED"

  // Effective window
  effectiveAt: Instant                         // Inclusive start
  effectiveUntil: Instant | null              // Exclusive end (null = still active)

  // Validation summary (at activation time)
  validationSummary: {
    rowCount: int                               // Total price points in grid
    gridHash: string                          // Hash confirming grid integrity
    validatedAt: Instant                       // When validation passed
    validatedBy: string                        // Actor who validated
    validationErrors: []                        // Empty = valid
  }

  // Row-level price structure (PII-05 consumer format)
  pricePoints: [
    {
      noteRate: BigDecimal                     // e.g., 0.0550 = 5.50%
      lockPeriod: int                          // Days: 7, 15, 30, 45, 60, 90
      basePrice: BigDecimal                    // e.g., 2.750 = 2.75%
      discountPoints: BigDecimal | null      // e.g., 0.500
      yieldIndex: BigDecimal | null
    }
  ]

  // Audit and immutability
  resultHash: string                           // SHA-256(sheetId + gridHash + activationTimestamp)
  activatedAt: Instant
  activatedBy: string
  supersededBy: UUID | null                    // Reference to successor sheet, if any
}
```

---

## PII-05 Consumption Contract

### Resolution Logic
PII-05 resolves active grid by: `(tenantId, investorId, channelId, productCode, resolutionTimestamp)`

**Resolution algorithm:**
1. Find rate sheets where `tenant_id = ? AND investor_id = ? AND channel_id = ? AND product_code = ?`
2. Filter: `effective_at <= resolutionTimestamp` AND (`effective_until IS NULL` OR `effective_until > resolutionTimestamp`)
3. Filter: `status = 'ACTIVE'`
4. Return version with highest `version` number

### API Endpoints (Consumed by PII-05)

| Method | Path | RBAC | Description |
|--------|------|------|-------------|
| GET | `/api/v1/tenants/{tenantId}/rates/resolve` | `RATE_FEED_VIEW` | Resolve active grid at timestamp |
| GET | `/api/v1/tenants/{tenantId}/rates/{sheetId}/{version}/grid` | `RATE_FEED_VIEW` | Full grid for specific version |
| GET | `/api/v1/tenants/{tenantId}/rates/{sheetId}/{version}/price` | `RATE_FEED_VIEW` | Single-point lookup |
| GET | `/api/v1/tenants/{tenantId}/rate-sheets/{sheetId}` | `RATE_FEED_VIEW` | Sheet detail metadata |
| GET | `/api/v1/tenants/{tenantId}/rate-sheets` | `RATE_FEED_VIEW` | Sheet list with filters |

### Resolve Endpoint Query Parameters

| Param | Type | Required | Description |
|-------|------|----------|-------------|
| investorId | UUID | Yes | Investor for resolution |
| channelId | UUID | Yes | Channel for resolution |
| productCode | string | Yes | Product code |
| lockPeriod | int | Yes | Lock period in days |
| resolutionTimestamp | Instant | Yes | Point-in-time resolution |

### Grid Response Model

| Field | Type | Description |
|-------|------|-------------|
| sheetId | UUID | Rate sheet identity |
| version | int | Grid version |
| gridHash | string | Integrity hash |
| pricePoints | array[] | Array of price point objects |
| pointCount | int | Total count |

### Price Lookup Response

| Field | Type | Description |
|-------|------|-------------|
| noteRate | BigDecimal | Requested note rate |
| lockPeriod | int | Requested lock period |
| basePrice | BigDecimal | Resolved base price |
| discountPoints | BigDecimal | Resolved discount points |
| match | string | "EXACT" or "INTERPOLATED" |
| resultHash | string | Result integrity hash |

### Cache Contract

PII-05 cache keyed by: `(tenantId, investorId, channelId, productCode, lockPeriod, resolutionTimestampFloor)`

- **TTL:** Bound to `effective_until` end of the resolved grid's effective window
- **Invalidate on:** New activation that supersedes the current active sheet

---

## Error Responses

All endpoints return:
```json
{
  "code": "ERROR_CODE",
  "message": "Human-readable message",
  "correlationId": "UUID for tracing"
}
```

### Error Codes

| Code | HTTP Status | Condition |
|------|-------------|-----------|
| NO_ACTIVE_RATE_SHEET | 404 | No active sheet in effective window |
| SHEET_NOT_FOUND | 404 | Sheet ID not found |
| VERSION_NOT_FOUND | 404 | Version does not match sheet |
| NO_EXACT_MATCH | 404 | No exact grid match (interpolate=false) |
| RATE_OUT_OF_RANGE | 400 | Rate outside [0.005, 25.0] for interpolation |
| ACCESS_DENIED | 403 | RBAC role check failed |

---

## Immutability Guarantees

1. Once a sheet is `ACTIVE`, its `pricePoints` array is immutable
2. `gridHash` is computed at parse time and never changes
3. Supersession creates a new version; the old sheet transitions to `SUPERSEDED` but retains its data
4. `resultHash` binds sheetId, gridHash, and activation timestamp for replay verification

---

## Governance Requirements

1. **Investor/Channel Required:** Both must be non-null UUIDs on all grid records
2. **Product Code Required:** Must be present and non-blank
3. **Effective Window:** Must have `effectiveAt`; `effectiveUntil` is optional for current sheet
4. **Status Machine:** `PARSING` → `VALIDATED` → `ACTIVE` or `REJECTED`; `ACTIVE` → `SUPERSEDED`

---

## Version History

| Version | Date | Change |
|---------|------|--------|
| 1.0 | 2026-05-19 | Initial contract for PII-05 consumption |
