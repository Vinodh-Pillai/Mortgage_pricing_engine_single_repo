# Pricing Service Implementation Analysis

## 1. Service Purpose and Capabilities

The **pricing-service** is a Spring Boot 3.3.5 (Java 17) microservice that implements the core mortgage pricing engine for the World-Class Pricing Engine (WCPE) platform. It provides deterministic, auditable, and versioned pricing calculations across multiple loan product types.

### Core Capabilities

1. **Quote Generation** (QuoteApi) - Initial eligibility screening and product matching for loan scenarios
2. **Base Rate Selection** (BaseRateSelectionApi) - Versioned pricing grid import, validation, publishing, and exact-match rate selection
3. **Final Price Calculation** (FinalPriceApi) - Deterministic ledger-based price calculation with adjustments, MI, government fees, caps/floors, and rounding
4. **Pricing Waterfall** (PricingWaterfallApi) - Assembles complete pricing evidence view with redaction for restricted values
5. **Rounding Policy** (RoundingPolicyApi) - Tenant-scoped, versioned rounding policies with validation and separation-of-duties publishing
6. **Mortgage Insurance (MI) Pricing** (MiPricingApi) - BPMI/LPMI pricing across carriers with premium type support
7. **Government Loan Pricing** (GovernmentPricingApi) - FHA, VA, USDA fee and limit calculations
8. **Home Equity Pricing** (HomeEquityPricingApi) - HELOC/Closed-End pricing with index rates, margins, adjustments, rate boundaries
9. **Non-QM Pricing** (NonQmPricingApi) - DSCR, Construction, Fix-Flip, Rental Portfolio, Bank Statement, Asset Depletion, No Ratio, Foreign National, ITIN, 1099 Only, Reverse Mortgage
10. **Non-QM Quick Pricer** (NonQmQuickPricerApi) - Fast-path preliminary quotes with caching
11. **Calculation Data Table Lookups** (CalculationDataTableLookupApi) - Governed tenant-scoped lookup tables
12. **Calculation Field Catalog** (CalculationFieldCatalogApi) - Metadata catalog for calculation-sourced fields
13. **Calculation Runner** (PricingCalculationRunnerApi) - Executes governed calculation definitions (input copy, lookup, formula)
14. **Pricing Replay** (PricingReplayApi) - Immutable replay of pricing calculations for audit/diff
15. **Version Graph Resolution** (PricingVersionResolver) - Resolves immutable version graphs across pricing artifacts
16. **Missing Price Handling** (MissingPriceHandlingApi) - Incident tracking and retry for missing grid prices
17. **Par Rate Identification** (ParRateIdentificationApi) - Identifies par rate from candidate grid slice per policy

### Design Principles

- **No Hardcoded Domain Values**: All rates, thresholds, fees, limits come from configured/versioned sources
- **Deterministic Results**: SHA-256 result hashes, version graphs, idempotency keys
- **Audit-First**: Every operation produces audit events, ledger entries, and outbox events
- **Tenant Isolation**: All operations scoped by tenant_id with strict enforcement
- **Contract-First**: OpenAPI contracts and JSON schemas define external interfaces
- **Blocked-by-Default**: Missing configuration returns explicit blockers, never invented values

## 2. Domain Entities and Data Models

### Core Domain Entities

| Entity | Package | Key Attributes |
|--------|---------|----------------|
| BasePricingGridVersion | baserate | id, tenantId, productCode, investorCode, channelCode, versionNumber, status (DRAFT/PUBLISHED/SUSPENDED), effectiveFrom/to, sourceDigest, approvedBy/At |
| BasePricingGridRow | baserate | id, tenantId, gridVersionId, lockPeriodDays, noteRate(5dp), basePrice(5dp), bucketKey(JSON), rowHash |
| BaseRateSelection | baserate | selectionId, gridVersionId, selectedNoteRate, selectedBasePrice, candidateRates[], lockPeriodDays, asOf, ledger[], warnings[], resultHash, status |
| CandidateRate | baserate | noteRate, basePrice, rank, reasonCode |
| FinalPriceResponse | finalprice | finalPriceId, selectedNoteRate, lockPeriodDays, basePrice, adjustments[], subtotal, capFloorResults[], mortgageInsurance[], governmentPricing[], roundedFinalPrice, ledger[], versionGraph, resultHash, cacheKey |
| AdjustmentResult | finalprice | ruleId, versionRef, reasonCode, amount, inputValue, outputValue, outputType, conditions[] |
| CapFloorResult | finalprice | versionRef, reasonCode, inputValue, outputValue, adjusted, action (ADJUST/BLOCK/WARN) |
| MiPriceOption | mi | carrier, premiumType, coveragePercent, annualRatePercent, upfrontRatePercent, priceAdjustment, monthlyPremium, upfrontPremium, rankingCost, sourceRef, versionRef, replayHash, rank, conditionEvidence[] |
| GovernmentPriceOption | government | catalog, lineItems[], loanLimit, availableEntitlement, incomeLimit, versionRef, replayHash, conditionEvidence[] |
| HomeEquityPriceResponse | homeequity | priceId, tenantId, scenarioId, productType, status, indexCode, indexRate, baseMarginBps, totalMarginBps, cltv, fullyIndexedRate, initialRate, maxAnnualRate, maxLifetimeRate, drawPayment, repaymentPayment, adjustments[], blockers[], waterfall[], versionRefs[], resultHash |
| NonQmRateSheet | nonqm | rateSheetId, investorCode, channelCode, productType, version, effectiveDate, status, rows[], adjustmentRefs[], marginPolicyRef, source |
| NonQmPriceResult | nonqm | priceId, tenantId, scenarioId, productType, status, rateSheetId, version, investorCode, channelCode, rowId, investorProductCode, baseNoteRate, basePrice, finalNoteRate, finalPrice, waterfall, blockers[], versionRefs[], resultHash |
| RoundingPolicyVersion | rounding | id, tenantId, versionNumber, status, scope, productCode, investorCode, channelCode, effectiveFrom/to, schemaVersion, createdBy, approvedBy/At, auditRef, correlationId, validationPassed, rules[], fixtures[] |
| PricingCalculationSnapshot | replay | tenantId, sourceType, sourceId, scenarioHash, versionGraphHash, selectedRowHash, roundingPolicyRef, canonicalInput, resultValues, ledger, resultHash, ledgerHash, schemaSupported |
| VersionGraphResult | version | versionGraphId, asOf, versionRefs[], graphHash, warnings[], productCode, investorCode, channelCode, scenarioHash |
| MissingPriceIncident | missingprice | id, tenantId, scenarioHash, productCode, investorCode, channelCode, lockPeriodDays, noteRate, asOf, reasonCode, diagnostic, status, correlationId, createdAt, resolvedAt, version |

### Value Objects (Scale-Normalized)

All monetary/rate values use BigDecimal with enforced scales:
- Note Rate / Price: 5 decimal places (ROUND_HALF_UP)
- Intermediate Calculations: 8 decimal places (ROUND_HALF_UP/HALF_EVEN)
- Money (Premiums, Fees, Payments): 2 decimal places (ROUND_HALF_UP)
- BPS: 2 decimal places
- Percent: 8 decimal places

### Key Enumerations

- GridVersionStatus: DRAFT, PUBLISHED, SUSPENDED
- GridImportStatus: DRAFT, VALIDATED, VALIDATION_FAILED, PUBLISHED
- BaseRateSelectionStatus: PENDING, COMPLETED, FAILED, REJECTED
- RoundingPolicyStatus: DRAFT, PUBLISHED, SUSPENDED
- RoundingUnit: NOTE_RATE, PRICE, POINTS, BPS, MONEY, PERCENT
- MiPremiumType: BPMI_MONTHLY, BPMI_SINGLE, BPMI_SPLIT, LPMI
- GovernmentProductType: FHA, VA, USDA
- GovernmentFeeFrequency: UPFRONT, ANNUAL, MONTHLY
- HomeEquityProductType: HELOC, CLOSED_END_HOME_EQUITY
- HomeEquityIndexCode: PRIME, SOFR, COFI
- LienPosition: FIRST, SECOND
- NonQmProductType: DSCR, CONSTRUCTION, FIX_FLIP, RENTAL_PORTFOLIO, BUSINESS_PURPOSE, BANK_STATEMENT, ASSET_DEPLETION, NO_RATIO, FOREIGN_NATIONAL, ITIN, ONE099_ONLY, REVERSE_MORTGAGE
- CapFloorAction: ADJUST, BLOCK, WARN
- ParComparator: NEAREST_TO_TARGET, EXACT_TARGET
- ParTieBreaker: LOWEST_NOTE_RATE, HIGHEST_NOTE_RATE
- PriceBasis: BASE, FINAL

## 3. API Contracts (REST Endpoints)

### OpenAPI Contract (contracts/pricing-contract.yml)

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| POST | /api/v1/tenants/{tenantId}/pricing/rounding-policies | Create draft rounding policy | WRITE |
| POST | /api/v1/tenants/{tenantId}/pricing/rounding-policies/{versionId}/publish | Publish validated rounding policy | APPROVE+PUBLISH |
| GET | /api/v1/tenants/{tenantId}/pricing/rounding-policies/resolve | Resolve active rounding policy | READ |
| POST | /api/v1/tenants/{tenantId}/pricing/base-rate-selections | Select base rate from grid | WRITE + Idempotency-Key |
| POST | /api/v1/tenants/{tenantId}/pricing/base-grids/imports | Import draft base pricing grid | GRID_IMPORT + Idempotency-Key |
| POST | /api/v1/tenants/{tenantId}/pricing/base-grids/{versionId}/validate | Validate draft grid | GRID_IMPORT |
| POST | /api/v1/tenants/{tenantId}/pricing/base-grids/{versionId}/publish | Publish validated grid | GRID_PUBLISH |
| GET | /api/v1/tenants/{tenantId}/pricing/base-grids/lookup | Lookup exact grid row | GRID_READ |
| GET | /api/v1/tenants/{tenantId}/pricing/missing-price-incidents/{incidentId} | Get missing-price incident | MISSING_PRICE_READ |
| POST | /api/v1/tenants/{tenantId}/pricing/missing-price-incidents/{incidentId}/retry | Retry missing-price lookup | MISSING_PRICE_RETRY + Idempotency-Key |
| POST | /api/v1/tenants/{tenantId}/pricing/final-prices | Calculate final price | FINAL_PRICE_WRITE + Idempotency-Key |
| POST | /pricing/quote | Synthetic pricing quote (fixture-based) | - |

### Headers

- X-Actor-Id / actorId - Requesting actor identity
- X-Correlation-Id / correlationId - Request correlation for tracing
- Idempotency-Key / idempotencyKey - Required for write operations
- X-Roles / permissions - Comma-separated roles/permissions
- If-Pricing-Version - Optional header for replay/pinned pricing

### Error Codes

| Code | HTTP | Description |
|------|------|-------------|
| VERSION_CONFLICT | 409 | Ambiguous/overlapping versions |
| IDEMPOTENCY_CONFLICT | 409 | Same idempotency key, different payload |
| PRICING_VERSION_STALE | 409 | Pinned version no longer current |
| BASE_RATE_SELECTION_REQUIRED | 422 | Selection prerequisite missing |
| ADJUSTMENT_CONFIG_MISSING | 422 | Adjustment configuration not found |
| ADJUSTMENT_CONFLICT | 422 | Conflicting adjustment rules |
| CAP_FLOOR_BLOCKED | 422 | Price boundary violation (BLOCK) |
| ROUNDING_POLICY_MISSING | 422 | No applicable rounding policy |
| SCENARIO_FACT_MISSING | 422 | Required scenario facts absent |
| PRICE_GRID_MISSING | 422 | No active pricing grid |
| PRICE_ROW_MISSING_LOCK_PERIOD | 422 | No grid row for lock period |
| PRICE_ROW_MISSING_NOTE_RATE | 422 | No grid row for note rate |
| PRICE_ROW_MISSING_BUCKET | 422 | No grid row for bucket |
| PRICE_GRID_SUSPENDED | 422 | Grid version suspended |
| PRICE_LOOKUP_AMBIGUOUS | 409 | Multiple matching grid rows |
| PRICE_VERSION_STALE | 409 | Pinned grid version stale |
| GRID_DUPLICATE_ROW | 400 | Duplicate grid row on import |
| GRID_SCALE_INVALID | 400 | Scale exceeds 5dp |
| GRID_NOT_APPROVED | 422 | Importer cannot publish own grid |
| GRID_EFFECTIVE_WINDOW_OVERLAP | 409 | Overlapping published grids |
| POLICY_NOT_SATISFIED | 422 | Import policy requirements unmet |
| DEPENDENCY_UNAVAILABLE | 503 | Downstream service unavailable |

### Request/Response Schemas

- contracts/pricing-request-schema.json - Synthetic grid request (fixture_id only)
- contracts/pricing-response-schema.json - Synthetic grid response (synthetic_base_rate, synthetic_adjustment, synthetic_quote_rate)

All request/response bodies defined as Java records in API classes with JSON serialization via Jackson.

## 4. Database Schema (Flyway Migrations)

### V1__rounding_policy.sql
- rounding_policy_version - Policy versions with effective windows, status, approval metadata
- rounding_rule - Rules per policy: output_context, unit, scale, rounding_mode, increment, precedence, reason_code
- rounding_sample_fixture - Validation fixtures per policy
- Indexes: effective window lookup, context+precedence unique

### V2__base_pricing_grid.sql
- base_pricing_grid_version - Grid versions per tenant/product/investor/channel with effective windows
- base_pricing_grid_row - Rows per grid version: lock_period_days, note_rate(9,5), base_price(9,5), bucket_key(JSONB), row_hash
- base_pricing_grid_import - Import tracking: source_type, source_digest, status, validation_summary
- base_pricing_grid_event - Event sourcing: event_type, actor, correlation, idempotency_key, payload
- base_rate_selection_audit - Selection audit: request/response hashes, rounding_policy_ref
- Unique indexes: grid version per tenant/scope/version_number, row key per grid/lock/note/bucket

### V3__final_price_calculation.sql
- pricing_adjustment_version - Adjustment config versions
- pricing_adjustment_rule - Rules: scope, condition_json, operation, amount, unit, precedence, reason_code
- final_price_result - Calculation results: selection_id, scenario_hash, final_price, subtotal, version_graph(JSONB), result_hash, request_hash, idempotency_key (unique per tenant)
- final_price_ledger_entry - Ledger lines per calculation: ordinal, input/output values, operation, config_ref, rounding_ref, reason_code
- Indexes: tenant/selection, tenant/scenario/result_hash, tenant/version/precedence

### V4__par_rate_identification.sql
- pricing_par_policy_version - Par policies: target_definition_json, comparator, tie_breaker, price_basis
- pricing_par_rate_identification_result - Results: grid_version_id, lock_period_days, par_policy_version_id, par_note_rate, par_price, result_hash, ledger_json
- Index: tenant/grid/lock/result_hash

### V5__missing_price_handling.sql
- missing_price_incident - Incidents: scenario_hash, product/investor/channel, lock_period, note_rate, as_of, reason_code, diagnostics_json, status, version
- missing_price_retry - Retries: incident_id, attempted_by/at, result_status, result_ref, error_code
- pricing_missing_price_outbox - Outbox events for incident detection/retry
- Indexes: tenant/status/created, tenant/reason/created, tenant/scenario_hash

### V6__pricing_version_graph.sql
- pricing_version_graph - Resolved version graphs: product/investor/channel, as_of, version_refs(JSONB), graph_hash, warnings
- artifact_version - Artifact versions per type (GRID, ROUNDING, PAR_RATE, CAP_FLOOR, REASON_CODE, ADJUSTMENT) with effective windows
- version_graph_event - Graph resolution events
- version_graph_audit - Graph audit with pinned_refs
- version_graph_cache - Cache with TTL
- Indexes: tenant/graph_hash, tenant/as_of/scope, artifact resolution, cache expiry

### V7__price_boundary_policy.sql
- price_boundary_policy_version - Cap/floor policies
- price_boundary_rule - Rules: condition_json, boundary_type (CAP/FLOOR), bound_value, action (ADJUST/BLOCK/WARN), precedence, reason_code
- price_boundary_evaluation - Evaluations: input/output price, action, result_hash, ledger
- Indexes: policy effective, rule precedence, evaluation hash

### V8__pricing_replay.sql
- pricing_calculation_snapshot - Immutable snapshots: source_type/id, scenario_hash, version_graph_hash, selected_row_hash, rounding_policy_ref, canonical_input, result_values, ledger, result_hash, ledger_hash, schema_supported
- pricing_replay_run - Replay runs: mode, status, original/replay/ledger hashes, mismatch_class, evidence_ref
- pricing_replay_diff - Diffs per run: path, original/replay values (redacted), classification, severity
- pricing_replay_event - Replay events
- pricing_replay_audit - Replay audit
- Indexes: run source, run status/mismatch, diff per run

## 5. Business Logic / Services

### QuoteApi (quote/api/QuoteApi.java)
**Purpose**: Initial eligibility screening and product matching for loan scenarios

**Flow**:
1. Validates headers (PRICING_LOAN_OFFICER role, actorId, correlationId, idempotencyKey)
2. Validates request (borrowerId, ficoBand, purchasePrice, loanAmount, propertyState/Zip/Type, units, occupancy, channel, loanType, loanPurpose=PURCHASE, lockPeriodDays)
3. Creates ScenarioReference via ScenarioAdapter
4. Retrieves active CatalogCandidates for channel/loanType (conventional vs government)
5. Evaluates eligibility per candidate via EligibilityEvaluationAdapter
6. Returns QuoteResponse with options (productCode, investorCode, channelCode, eligibilityStatus, displayOrder, reason)
7. Persists quote via QuoteRepository (InMemory or Durable file-based)

**Key Features**:
- Tenant isolation enforcement
- Idempotency via DurableQuoteRepository (base64-encoded pipe-delimited serialization)
- Supported loan types: CONVENTIONAL, FHA, VA, USDA
- Only PURCHASE loan purpose supported for REQ-001

### BaseRateSelectionApi (baserate/BaseRateSelectionApi.java)
**Purpose**: Versioned pricing grid lifecycle and exact-match rate selection

**Operations**:
- importBaseGrid() - Creates DRAFT grid version + rows, validates bucket dimensions, lock periods, scale (5dp), duplicate detection
- validateBaseGrid() - Validates persisted grid (rows required, no duplicates)
- publishBaseGrid() - Publishes validated grid: separation of duties (importer != publisher), no overlapping effective windows, cache invalidation
- lookupBaseGridRow() - Exact match by lockPeriodDays, noteRate, bucketKey hash
- selectRate() - Core selection: resolves published grid version at asOf, filters by lockPeriodDays, finds exact noteRate match, applies selection policy (exact match required), builds ledger, persists with idempotency

**Validation Rules**:
- Grid import requires approvalWorkflowConfigured=true, allowedLockPeriodDays configured, allowedBucketDimensions configured
- Row validation: lockPeriodDays in allowed set, noteRate/basePrice scale <=5, bucketKeys subset of allowed dimensions
- Publish: no overlapping PUBLISHED windows for same tenant/product/investor/channel
- Selection: requires published grid, matching lockPeriodDays, exact noteRate match (throws PRICE_ROW_MISSING_NOTE_RATE)

### FinalPriceApi (finalprice/FinalPriceApi.java)
**Purpose**: Deterministic final price calculation with full ledger and version graph

**Flow**:
1. Validates request (selectionId, scenarioId, scenarioHash, pricingConfigVersionRefs[], asOf, idempotency)
2. Idempotency check via requestHash (SHA-256 of request fields)
3. Loads SelectedBaseRate (selectionId), ScenarioFacts (scenarioId+hash), PricingConfigurationSnapshot (versionRefs+asOf)
4. Base price from selection (scale 5dp), subtotal at intermediate scale (8dp)
5. Adjustments: Delegates to AdjustmentCalculationPort (configuration adapter or external), applies lines sequentially with ledger entries
6. Mortgage Insurance: If configured and facts present (loanType, LTV, FICO, loanAmount, coveragePercent), calls MiPricingApi, adds MI line to ledger
7. Government Pricing: If government loanType (FHA/VA/USDA) and config present, calls GovernmentPricingApi, adds fee lines to ledger
8. Caps/Floors: Applies CapFloorRules in precedence order (ADJUST/BLOCK/WARN), fails closed if policy required but missing
9. Rounding: Calls RoundingPolicyApi.resolve() for FINAL_PRICE context, applies rounding, adds ledger entry
10. Builds VersionGraph (gridVersionRef, roundingPolicyVersionId, MI refs, gov refs), computes resultHash
11. Persists FinalPriceResult, FinalPriceEvent, FinalPriceAudit (if not dryRun)
12. Returns FinalPriceResponse with all components

### PricingWaterfallApi (waterfall/PricingWaterfallApi.java)
**Purpose**: Assembles complete pricing evidence view with redaction

**Flow**:
1. Validates evidence (baseSelection, finalPrice required)
2. Builds ledger rows from finalPrice.ledger with redaction (RESTRICTED_VALUE_PERMISSION)
3. Extracts adjustment refs, cap/floor refs, version refs
4. Maps MI, government, home equity lines
5. Generates PricingOutcomeRecordedEvent for HMDA-style reporting
6. Returns PricingWaterfallView with status (READY/BLOCKED), redacted values, blockers

### RoundingPolicyApi (rounding/api/RoundingPolicyApi.java)
**Purpose**: Tenant-scoped, versioned rounding policies with validation

**Operations**:
- createDraft() - Creates DRAFT policy with rules (outputContext, unit, scale, roundingMode, increment, precedence, reasonCode), validates scales per unit, sorts by precedence
- validatePolicy() - Runs sample fixtures against rules, marks validationPassed
- publish() - Separation of duties (creator != approver), requires validationPassed, no overlapping effective windows for same scope
- resolve() - Finds single matching published policy for scope/product/investor/channel/asOf/outputContext, applies rule to inputValue

**Rounding Algorithm**:
incrementCount = inputValue / increment (using roundingMode)
outputValue = incrementCount * increment (setScale(scale, roundingMode))

**Supported Units/Scales**: NOTE_RATE(5), PRICE(5), POINTS(5), BPS(2), MONEY(2)

### MiPricingApi (mi/MiPricingApi.java)
**Purpose**: Mortgage insurance pricing across carriers and premium types

**Flow**:
1. Validates CONVENTIONAL loan type only
2. Matches MiPrograms (carrier + premiumType) to MiRateCards (carrier match)
3. Filters MiRateRows by: premiumType match, coveragePercent match, LTV range, FICO range, loanAmount range
4. Calculates premiums:
   BPMI_MONTHLY: monthly = loanAmount * annualRate% / 100 / 12
   BPMI_SINGLE: upfront = loanAmount * upfrontRate% / 100
   BPMI_SPLIT: both monthly and upfront
   LPMI: priceAdjustment from row
5. RankingCost = monthly + upfront + |priceAdjustment| * 100
6. Ranks by rankingCost, carrier, premiumType
7. Returns selected + ranked options with replayHash

### GovernmentPricingApi (government/GovernmentPricingApi.java)
**Purpose**: FHA, VA, USDA fee and limit calculations

**FHA**: Upfront MIP + Annual MIP + Monthly MIP (annual/12), county loan limit check
**VA**: Funding fee (exempt/first-use/down-payment tiers), county loan limit, entitlement calculation
**USDA**: Upfront/annual guarantee fee, county/state income limit, property eligibility ref check

All require matching GovernmentProductConfiguration (active, displayOrder sorted).

### HomeEquityPricingApi (homeequity/HomeEquityPricingApi.java)
**Purpose**: HELOC/Closed-End pricing with index rates and adjustments

**Flow**:
1. Validates product family enabled, CLTV <= maxCombinedLoanToValue
2. Finds IndexRateConfig for requested indexCode (PRIME/SOFR/COFI)
3. Applies adjustments (CLTV_GREATER_THAN, CREDIT_SCORE_LESS_THAN, PROPERTY_TYPE_EQUALS, LIEN_POSITION_EQUALS, PRODUCT_TYPE_EQUALS) by precedence
4. Total margin = baseMarginBps + sum(adjustment BPS)
5. Fully indexed rate = indexRate + margin
6. Applies rate boundaries (floor/ceiling, annual/lifetime caps)
7. Calculates draw payment (interest-only) and repayment payment (amortizing)
8. Builds waterfall with all steps

### NonQmPricingApi (nonqm/NonQmPricingApi.java)
**Purpose**: Non-QM product pricing with strategy pattern

**Strategies**:
- DSCR: ConfiguredDimensionPricingStrategy (dscrTier, ficoBand, ltvBand)
- CONSTRUCTION: ProjectPricingStrategy (projectType, ltcBand, reserveBand, builderStatus, drawScheduleStatus)
- FIX_FLIP: ProjectPricingStrategy (ltarvBand, rehabBudgetBand, drawScheduleStatus, term, exitStrategy)
- RENTAL_PORTFOLIO: ProjectPricingStrategy (entityType, portfolioDscrBand, propertyCountBand, crossCollateral)
- BUSINESS_PURPOSE, BANK_STATEMENT, ASSET_DEPLETION, NO_RATIO, FOREIGN_NATIONAL, ITIN, ONE099_ONLY: ConfiguredDimensionPricingStrategy
- REVERSE_MORTGAGE: ReverseMortgagePricingStrategy (PLF, MIP, servicing set-aside, LOC growth)

**Flow**:
1. Validates EligibilityDecision (ELIGIBLE/REFER)
2. Resolves NonQmRateSheet (published, matching product/investor/channel/effectiveDate)
3. Strategy.selectBasePrice() - finds matching rate row by tier keys, derives pricing facts
4. AdjustmentClient calculates adjustments from sheet.adjustmentRefs
5. MarginClient calculates margin from sheet.marginPolicyRef + request.marginPolicies
6. Builds waterfall: BASE_RATE_SHEET_ROW -> NON_QM_SPECIALTY_PREMIUM -> PII_33_ADJUSTMENT -> PII_34_MARGIN
7. Returns NonQmPriceResult with versionRefs and resultHash

### CalculationRunnerApi (calculationrunner/PricingCalculationRunnerApi.java)
**Purpose**: Executes governed calculation definitions (no formula invention)

**Step Sources**:
- INPUT_VALUE - Copies from request.inputs
- LOOKUP_VALUE - Calls CalculationDataTableLookupApi with key bindings
- FORMULA_EXPRESSION - Evaluates expression (parser supports literals, booleans, numbers, field refs, basic ops)

**Features**: Activation validation (field/enum/lookup dependencies), audit capture, replay with divergence detection, runtime evaluation for Pipeline/Adjustments/Margins/Pricing modules.

### PricingReplayApi (replay/PricingReplayApi.java)
**Purpose**: Immutable replay of pricing calculations

**Flow**:
1. Loads PricingCalculationSnapshot by sourceType/sourceId
2. Reconstructs calculation from snapshot (ledgerHash, resultHash, versionGraphHash, etc.)
3. Classifies diffs: RESULT_HASH_MISMATCH, LEDGER_HASH_MISMATCH, VERSION_GRAPH_MISMATCH, SELECTED_ROW_MISMATCH, ROUNDING_POLICY_MISMATCH, SCALE_ONLY_DRIFT, RESULT_VALUE_MISMATCH
4. Mismatch classification hierarchy: VERSION_GRAPH > material non-SCALE > SCALE_ONLY_DRIFT > RESULT_HASH
5. Persists replay run, diffs, events, audit

### PricingVersionResolver (version/PricingVersionResolver.java)
**Purpose**: Resolves immutable version graphs across pricing artifacts

**Artifact Types**: GRID, ROUNDING, PAR_RATE, CAP_FLOOR, REASON_CODE, ADJUSTMENT

**Flow**:
1. Parses pinnedVersionRefs (artifactType:versionId:immutableHash)
2. For each required artifact: resolves effective published version at asOf (or pinned)
3. Detects ambiguity (overlapping effective windows)
4. Computes graphHash (SHA-256 of sorted artifactType:versionId:versionHash)
5. Persists VersionGraphResult, VersionGraphEvent, VersionGraphAudit

### MissingPriceHandlingApi (missingprice/MissingPriceHandlingApi.java)
**Purpose**: Incident tracking for missing grid prices

**Flow**:
1. Classifies MissingPriceLookupStatus -> MissingPriceReason (PRICE_GRID_MISSING, PRICE_ROW_MISSING_LOCK_PERIOD, PRICE_ROW_MISSING_NOTE_RATE, PRICE_ROW_MISSING_BUCKET, PRICE_GRID_SUSPENDED, PRICE_LOOKUP_AMBIGUOUS, PRICE_VERSION_STALE)
2. Creates MissingPriceIncident with diagnostics, persists, publishes outbox event
3. Negative caching by (tenant, gridVersionRef, scenarioHash, lockPeriod, noteRate, bucketKeyHash)
4. Retry: updates incident status (RESOLVED/RETRY_FAILED), publishes outbox event
5. Grid publish invalidates negative cache

### ParRateIdentificationApi (parrate/ParRateIdentificationApi.java)
**Purpose**: Identifies par rate from grid slice per policy

**Flow**:
1. Loads published ParPolicyVersion (targetPrice, comparator, priceBasis, tieBreaker)
2. Filters candidateRates by lockPeriodDays, sorts by noteRate
3. Evaluates each candidate: distance = |evaluatedPrice - targetPrice| (priceBasis: BASE or FINAL)
4. Finds bestDistance, handles ties with tieBreaker (LOWEST/HIGHEST_NOTE_RATE)
5. Builds ledger: POLICY_RESOLUTION, GRID_SLICE, PAR_COMPARISON
6. Returns ParRateIdentificationResponse with candidateEvaluations (parCandidate flag)

## 6. Key Algorithms

### 1. Base Rate Selection (Exact Match)
1. Resolve published grid version at asOf (single, no overlap)
2. Filter rows by lockPeriodDays
3. Find exact noteRate match
4. If not found -> PRICE_ROW_MISSING_NOTE_RATE
5. If multiple -> PRICE_LOOKUP_AMBIGUOUS
6. Return selected noteRate, basePrice, all candidates ranked by noteRate

### 2. Adjustment Application (Precedence-Ordered)
runningSubtotal = basePrice
For each adjustmentRule sorted by precedence:
  amount = rule.amount (8dp)
  runningSubtotal += amount (8dp)
  ledgerEntry(input=prev, operation=ADD/SUB, output=runningSubtotal)
Validate unique precedence per applicable rule set

### 3. Cap/Floor Evaluation
For each capFloorRule sorted by precedence:
  belowFloor = minPrice != null && running < minPrice
  aboveCap = maxPrice != null && running > maxPrice
  If belowFloor or aboveCap:
    ADJUST -> running = minPrice or maxPrice
    BLOCK -> throw CAP_FLOOR_BLOCKED
    WARN -> throw PRICE_BOUNDARY_POLICY_NOT_SATISFIED (fails closed)
  ledgerEntry(CHECK/ADJUST)

### 4. Rounding Policy Resolution
1. Find published policy matching scope/product/investor/channel/asOf/outputContext
2. Must have exactly 1 matching rule for outputContext
3. Apply: outputValue = round(inputValue / increment) * increment (to scale)

### 5. MI Pricing (BPMI/LPMI)
BPMI_MONTHLY: monthly = loanAmount * annualRate% / 100 / 12
BPMI_SINGLE: upfront = loanAmount * upfrontRate% / 100
BPMI_SPLIT: both monthly + upfront
LPMI: priceAdjustment from rate row (added to price)
RankingCost = monthly + upfront + |priceAdjustment| * 100
Select lowest RankingCost

### 6. Government Fee Calculation
feeAmount = loanAmount * ratePercent / 100 (10dp) -> 2dp
FHA: upfrontMIP + annualMIP + monthlyMIP (annual/12)
VA: fundingFee (exempt? 0 : rate by downPayment/firstUse)
USDA: upfrontGuaranteeFee + annualGuaranteeFee

### 7. Home Equity Rate Calculation
CLTV = (existingLienBalance + creditLimit) * 100 / propertyValue
totalMarginBps = baseMarginBps + sum(adjustment.amountBps)
fullyIndexedRate = indexRate + bpsToRate(totalMarginBps)
boundedRate = applyFloorCeiling(fullyIndexedRate)
annualMax = boundedRate + annualCap (capped by ceiling)
lifetimeMax = boundedRate + lifetimeCap (capped by ceiling)
drawPayment = initialDraw * (initialRate/100/12)
repaymentPayment = amortized(initialDraw, monthlyRate, repaymentMonths)

### 8. Non-QM Strategy Selection
For each product type, registered strategy:
  selectBasePrice(request, rateSheet):
    Match row by tierKeys (exact match on required dimensions)
    Derive pricing facts (DSCR, LTC, LTARV, portfolioDSCR, etc.)
    Return BasePriceResult with row, pricingFacts, missingFacts
If missingFacts not empty -> BLOCKED

### 9. Par Rate Identification
For each candidate in lockPeriod slice:
  evaluatedPrice = (priceBasis == FINAL) ? candidate.finalPrice : candidate.basePrice
  distance = |evaluatedPrice - targetPrice|
Find min distance
If EXACT_TARGET and no distance==0 -> PAR_CANDIDATES_EMPTY
If tie -> apply tieBreaker (LOWEST/HIGHEST_NOTE_RATE)

### 10. Version Graph Hash
sortedRefs = versionRefs sorted by artifactType then versionId
hash = SHA-256(concat(artifactType:versionId:versionHash + NUL))

### 11. Result Hash (Deterministic Replay)
SHA-256 of all calculation inputs + outputs:
  tenantId, selectionId, scenarioHash, versionGraphHash,
  selectedRowHash, roundingPolicyRef, canonicalInput,
  resultValues, ledgerHash

### 12. Replay Diff Classification
Hierarchy:
1. VERSION_GRAPH_MISMATCH (highest)
2. Any material diff (non-SCALE_ONLY_DRIFT)
3. All diffs SCALE_ONLY_DRIFT -> SCALE_ONLY_DRIFT
4. RESULT_HASH_MISMATCH only -> RESULT_HASH_MISMATCH

### 13. Idempotency
requestFingerprint = SHA-256(commandType, tenantId, allRequestFields)
If idempotencyKey exists with different fingerprint -> IDEMPOTENCY_CONFLICT
If same fingerprint -> return cached response

### 14. Negative Caching (Missing Price)
cacheKey = pricing:missing-price:{tenant}:{gridVersionRef}:{scenarioHash}:{lockPeriod}:{noteRate}:{bucketKeyHash}
On grid publish -> invalidate all keys with gridVersionRef

## 7. Test Coverage

### Test Structure
All tests in src/test/java/com/wcpe/pricing/ organized by domain:
- contract/ - Contract compatibility and golden file tests
- baserate/ - Base rate selection service and contract tests
- finalprice/ - Final price calculation tests
- rounding/ - Rounding policy tests
- mi/ - Mortgage insurance tests
- government/ - Government loan pricing tests
- homeequity/ - Home equity pricing tests
- nonqm/ - Non-QM pricing and quick pricer tests
- waterfall/ - Waterfall assembly tests
- quote/ - Quote eligibility tests
- missingprice/ - Missing price handling tests
- parrate/ - Par rate identification tests
- replay/ - Pricing replay tests
- version/ - Version graph resolver tests
- calculationrunner/ - Calculation runner tests
- calculationtables/ - Calculation data table lookup tests
- calculationfields/ - Calculation field catalog tests

### Key Test Patterns

**In-Memory Repositories**: All APIs provide InMemory*Repository implementations for fast unit testing without database.

**Synthetic Fixtures Only**: All test data uses synthetic values (e.g., SYNTH_CONVENTIONAL, tenant-pii05-s10, rates like 6.12500). No production mortgage rates, thresholds, or investor rules.

**Golden File Tests**: PII05S10BasePricingEventSchemaCompatibilityTest validates against src/test/resources/golden/base-pricing/pii05-s10-contract-golden.json

**Contract Tests**: BaseRateSelectionContractTest, PricingContractTest validate JSON schemas and error surfaces against OpenAPI contract.

**Idempotency Tests**: Verify same idempotency key replays response; different payload fails with IDEMPOTENCY_CONFLICT.

**Tenant Isolation Tests**: Verify tenant-a cannot access tenant-b's grids/pricing.

**Deterministic Hash Tests**: Verify resultHash and selectionId are deterministic across separate API instances.

**BigDecimal Scale Tests**: Verify all monetary values maintain correct scale (5dp for rates/prices, 8dp intermediate, 2dp money).

**Error Code Tests**: Verify specific error codes thrown for each failure mode (400/409/422/503).

**Validation Tests**: Rounding policy scale/increment validation, grid duplicate/scale/bucket validation, cap/floor conflict detection.

**Replay Tests**: PricingReplayApiTest validates diff classification hierarchy and scale-only drift detection.

### Test Count: ~20 test classes covering all 17 API domains with multiple test methods each.

### Missing Coverage Areas
- Integration tests with actual PostgreSQL (Testcontainers)
- Load/performance tests
- Chaos/failure injection tests
- Cross-service integration tests (adjustment-service, scenario-service)
- UI/E2E tests (no UI in this service)
## 10. Architecture Summary
