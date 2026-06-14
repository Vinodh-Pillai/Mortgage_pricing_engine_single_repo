package com.wcpe.pricing.nonqm;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Non-QM pricing core for configured rate sheets and program dimensions.
 *
 * <p>The API intentionally consumes configured tier keys and rule refs. It does not invent mortgage pricing
 * thresholds, investor rules, or eligibility constants; missing configured data returns a non-priceable result.</p>
 */
public final class NonQmPricingApi {
    public static final String NON_QM_PRICE_PERMISSION = "pricing.non-qm.price";

    private static final int RATE_SCALE = 5;
    private static final int PRICE_SCALE = 5;

    private final NonQmRateSheetResolver rateSheetResolver;
    private final NonQmPricingStrategyRegistry strategyRegistry;
    private final NonQmAdjustmentClient adjustmentClient;
    private final NonQmMarginClient marginClient;

    public NonQmPricingApi(NonQmRateSheetResolver rateSheetResolver) {
        this(rateSheetResolver, new NonQmPricingStrategyRegistry(defaultStrategies()),
                new ConfiguredNonQmAdjustmentClient(), new ConfiguredNonQmMarginClient());
    }

    public NonQmPricingApi(NonQmRateSheetResolver rateSheetResolver, NonQmPricingStrategyRegistry strategyRegistry,
            NonQmAdjustmentClient adjustmentClient, NonQmMarginClient marginClient) {
        this.rateSheetResolver = Objects.requireNonNull(rateSheetResolver);
        this.strategyRegistry = Objects.requireNonNull(strategyRegistry);
        this.adjustmentClient = Objects.requireNonNull(adjustmentClient);
        this.marginClient = Objects.requireNonNull(marginClient);
    }

    public NonQmPriceResult price(String tenantId, NonQmPricingHeaders headers, NonQmPricingRequest request) {
        requireTenant(tenantId);
        requirePermission(headers, NON_QM_PRICE_PERMISSION);
        validateRequest(tenantId, request);

        EligibilityDecision eligibility = request.eligibilityDecision();
        if (eligibility == null || !eligibility.priceable()) {
            return blocked(request, "NON_QM_NOT_ELIGIBLE", eligibility == null ? "eligibility decision is required" : eligibility.reasonCode(),
                    eligibility == null ? "eligibility-service:missing" : eligibility.eligibilityRef(), headers.correlationId());
        }

        Optional<NonQmRateSheet> maybeSheet = rateSheetResolver.resolve(request);
        if (maybeSheet.isEmpty()) {
            return blocked(request, "RATE_SHEET_MISSING", "no published Non-QM rate sheet matches the request context",
                    "pricing-service.non-qm.rate-sheet-resolver", headers.correlationId());
        }

        NonQmRateSheet sheet = maybeSheet.get();
        if (sheet.productType() != request.productType()) {
            return blocked(request, "RATE_SHEET_PRODUCT_MISMATCH", "resolved rate sheet product type does not match request",
                    sheet.rateSheetId(), headers.correlationId());
        }

        NonQmPricingStrategy strategy = strategyRegistry.get(request.productType());
        BasePriceResult base = strategy.selectBasePrice(request, sheet);
        if (!base.priceable()) {
            return blocked(request, base.reasonCode(), String.join(",", base.missingFacts()), sheet.rateSheetId(), headers.correlationId());
        }

        NonQmAdjustmentCalculationResult adjustments = adjustmentClient.calculate(request, base, sheet.adjustmentRefs());
        if (adjustments.blocked()) {
            return blocked(request, "ADJUSTMENT_SERVICE_UNAVAILABLE", adjustments.resultHash(), sheet.rateSheetId(), headers.correlationId());
        }

        NonQmMarginResult margin = marginClient.calculateNonQmMargin(request, base, adjustments, sheet.marginPolicyRef(),
                request.marginPolicies());
        if (margin.blocked()) {
            return blocked(request, "MARGIN_POLICY_MISSING", margin.reasonCode(), sheet.marginPolicyRef(), headers.correlationId());
        }

        NonQmPricingWaterfall waterfall = buildWaterfall(base, adjustments, margin);
        String resultHash = stableHash("non-qm-price", tenantId, request.scenarioId(), request.productType(), sheet.rateSheetId(),
                base.row().rowId(), waterfall.lines(), waterfall.finalNoteRate(), waterfall.finalPrice(), eligibility.eligibilityRef());

        return new NonQmPriceResult(
                UUID.nameUUIDFromBytes((tenantId + ":" + resultHash).getBytes(StandardCharsets.UTF_8)),
                request.tenantId(), request.scenarioId(), request.productType(), "PRICED", sheet.rateSheetId(), sheet.version(),
                sheet.investorCode(), sheet.channelCode(), base.row().rowId(), base.row().investorProductCode(),
                base.row().noteRate(), base.row().basePrice(), waterfall.finalNoteRate(), waterfall.finalPrice(), waterfall,
                List.of(), versionRefs(sheet, adjustments, margin), resultHash, headers.correlationId());
    }

    public NonQmBatchPriceResult priceBatch(String tenantId, NonQmPricingHeaders headers, List<NonQmPricingRequest> requests) {
        List<NonQmPriceResult> results = (requests == null ? List.<NonQmPricingRequest>of() : requests).stream()
                .map(request -> price(tenantId, headers, request))
                .sorted(Comparator
                        .comparing((NonQmPriceResult result) -> !"PRICED".equals(result.status()))
                        .thenComparing(NonQmPriceResult::finalNoteRate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(NonQmPriceResult::finalPrice, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(NonQmPriceResult::productType)
                        .thenComparing(NonQmPriceResult::rowId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        NonQmPriceResult best = results.stream().filter(result -> "PRICED".equals(result.status())).findFirst().orElse(null);
        return new NonQmBatchPriceResult(results, best == null ? null : best.priceId(), best == null ? null : best.resultHash());
    }

    public ReversePricingBreakdown priceReverse(ReverseProgramConfig config, ReversePricingInputs inputs) {
        requireNonNull(config, "reverse program config is required");
        requireNonNull(inputs, "reverse pricing inputs are required");
        requireText(config.programCode(), "reverse program_code is required");
        requireText(config.investorCode(), "reverse investor_code is required");
        if (!config.paymentOptions().contains(inputs.paymentOption())) {
            return ReversePricingBreakdown.blocked("REVERSE_PAYMENT_OPTION_UNSUPPORTED",
                    "payment option is not configured for reverse program", config.programCode());
        }

        PrincipalLimitFactor plf = config.principalLimitTable().lookup(inputs.youngestBorrowerAge(), inputs.expectedRate());
        if (plf == null) {
            return ReversePricingBreakdown.blocked("REVERSE_PLF_MISSING",
                    "no configured PLF row matches youngest borrower age and expected rate", config.principalLimitTable().tableId());
        }

        BigDecimal maxClaimAmount = money(config.maxClaimAmount(inputs.propertyValue()));
        BigDecimal principalLimit = money(maxClaimAmount.multiply(plf.factor()));
        MipResult mip = config.mipPolicy().calculate(maxClaimAmount, principalLimit);
        ServicingFeeSetAsideResult servicingSetAside = config.servicingFeeSetAsidePolicy().calculate(inputs);
        BigDecimal closingCosts = money(config.configuredClosingCostEstimate());
        BigDecimal netAvailable = money(principalLimit.subtract(money(inputs.existingMortgageBalance()))
                .subtract(mip.initialMip()).subtract(servicingSetAside.amount()).subtract(closingCosts));
        ReversePaymentOptionEstimate optionEstimate = config.locGrowthPolicy().estimate(inputs.paymentOption(), netAvailable, inputs);
        List<String> auditRefs = List.of(config.programCode(), config.principalLimitTable().tableVersionRef(),
                plf.auditRef(), config.mipPolicy().policyRef(), config.locGrowthPolicy().policyRef(),
                config.servicingFeeSetAsidePolicy().policyRef());
        return new ReversePricingBreakdown(config.programCode(), config.programType(), config.investorCode(),
                inputs.expectedRate(), maxClaimAmount, plf, principalLimit, mip, servicingSetAside, closingCosts,
                netAvailable, optionEstimate, auditRefs, List.of(), stableHash("reverse-price", config.programCode(),
                        inputs, plf, principalLimit, mip, servicingSetAside, netAvailable));
    }

    public NonQmImportResult importRateSheet(String rateSheetId, String investorCode, String channelCode,
            NonQmProductType productType, int version, LocalDate effectiveDate, RateSheetSource source,
            List<Map<String, String>> vendorRows, String marginPolicyRef) {
        requireText(rateSheetId, "rate_sheet_id is required");
        List<NonQmRateRow> rows = new ArrayList<>();
        int ordinal = 0;
        for (Map<String, String> vendorRow : vendorRows == null ? List.<Map<String, String>>of() : vendorRows) {
            Map<String, String> fields = vendorRow == null ? Map.of() : vendorRow;
            String rowId = firstNonBlank(fields.get("rowId"), fields.get("row_id"), "row-" + ++ordinal);
            BigDecimal noteRate = decimalField(fields, "noteRate", "note_rate", "rate");
            BigDecimal basePrice = decimalField(fields, "basePrice", "base_price", "price");
            if (noteRate == null || basePrice == null) {
                return new NonQmImportResult(null, List.of(new NonQmBlocker("RATE_ROW_INVALID",
                        "imported row requires noteRate/rate and basePrice/price", rowId, "fix vendor row mapping")));
            }
            Map<String, String> tierKeys = fields.entrySet().stream()
                    .filter(entry -> entry.getKey().startsWith("tier.") || entry.getKey().startsWith("nonQm."))
                    .collect(Collectors.toMap(entry -> entry.getKey().replaceFirst("^tier\\.", ""), Map.Entry::getValue,
                            (left, right) -> right, LinkedHashMap::new));
            rows.add(new NonQmRateRow(rowId, noteRate, basePrice, tierKeys, Map.of(),
                    firstNonBlank(fields.get("investorProductCode"), fields.get("investor_product_code"), productType.name()),
                    firstNonBlank(fields.get("reasonCode"), fields.get("reason_code"), "IMPORTED_" + source.name())));
        }
        NonQmRateSheet sheet = new NonQmRateSheet(rateSheetId, investorCode, channelCode, productType, version,
                effectiveDate, RateSheetStatus.PUBLISHED, rows, List.of(), marginPolicyRef, source);
        return new NonQmImportResult(sheet, List.of());
    }

    public List<Map<String, String>> exportRateSheet(NonQmRateSheet sheet, RateSheetSource format) {
        requireNonNull(sheet, "rate sheet is required");
        requireNonNull(format, "export format is required");
        return sheet.rows().stream().map(row -> {
            Map<String, String> exported = new LinkedHashMap<>();
            exported.put("format", format.name());
            exported.put("rateSheetId", sheet.rateSheetId());
            exported.put("productType", sheet.productType().name());
            exported.put("rowId", row.rowId());
            exported.put("noteRate", row.noteRate().toPlainString());
            exported.put("basePrice", row.basePrice().toPlainString());
            row.tierKeys().forEach((key, value) -> exported.put("tier." + key, value));
            exported.put("investorProductCode", row.investorProductCode());
            exported.put("reasonCode", row.reasonCode());
            return exported;
        }).toList();
    }

    private static NonQmPricingWaterfall buildWaterfall(BasePriceResult base, NonQmAdjustmentCalculationResult adjustments,
            NonQmMarginResult margin) {
        List<NonQmWaterfallLine> lines = new ArrayList<>();
        BigDecimal runningRate = rate(base.row().noteRate());
        BigDecimal runningPrice = price(base.row().basePrice());
        lines.add(new NonQmWaterfallLine(1, "BASE_RATE_SHEET_ROW", null, runningRate, null, runningPrice,
                base.configRef(), base.reasonCode()));

        for (PricingFact fact : base.pricingFacts()) {
            BigDecimal inputRate = runningRate;
            BigDecimal inputPrice = runningPrice;
            runningRate = rate(runningRate.add(fact.rateDelta()));
            runningPrice = price(runningPrice.add(fact.priceDelta()));
            lines.add(new NonQmWaterfallLine(lines.size() + 1, "NON_QM_SPECIALTY_PREMIUM", inputRate, runningRate,
                    inputPrice, runningPrice, fact.sourceRef(), fact.reasonCode()));
        }

        for (NonQmAdjustmentLine adjustment : adjustments.adjustments()) {
            BigDecimal inputRate = runningRate;
            BigDecimal inputPrice = runningPrice;
            runningRate = rate(runningRate.add(adjustment.rateDelta()));
            runningPrice = price(runningPrice.add(adjustment.priceDelta()));
            lines.add(new NonQmWaterfallLine(lines.size() + 1, "PII_33_ADJUSTMENT", inputRate, runningRate,
                    inputPrice, runningPrice, adjustment.versionRef() + ":" + adjustment.ruleId(), adjustment.reasonCode()));
        }

        BigDecimal inputRate = runningRate;
        BigDecimal inputPrice = runningPrice;
        runningRate = rate(runningRate.add(margin.rateMargin()));
        runningPrice = price(runningPrice.add(margin.priceMargin()));
        lines.add(new NonQmWaterfallLine(lines.size() + 1, "PII_34_MARGIN", inputRate, runningRate,
                inputPrice, runningPrice, margin.policyRef(), margin.reasonCode()));
        return new NonQmPricingWaterfall(List.copyOf(lines), runningRate, runningPrice,
                stableHash("non-qm-waterfall", lines, runningRate, runningPrice));
    }

    private static List<String> versionRefs(NonQmRateSheet sheet, NonQmAdjustmentCalculationResult adjustments,
            NonQmMarginResult margin) {
        List<String> refs = new ArrayList<>();
        refs.add(sheet.rateSheetId() + ":v" + sheet.version());
        refs.addAll(sheet.adjustmentRefs().stream().map(NonQmPricingAdjustmentRef::versionRef).toList());
        refs.addAll(adjustments.auditRefs());
        refs.add(margin.policyRef());
        return refs.stream().filter(ref -> ref != null && !ref.isBlank()).distinct().sorted().toList();
    }

    private static NonQmPriceResult blocked(NonQmPricingRequest request, String code, String message, String sourceRef,
            String correlationId) {
        NonQmBlocker blocker = new NonQmBlocker(code, firstNonBlank(message, code), sourceRef, "publish required configuration or correct request facts");
        String hash = stableHash("non-qm-blocked", request == null ? null : request.scenarioId(), code, message, sourceRef);
        return new NonQmPriceResult(UUID.nameUUIDFromBytes(hash.getBytes(StandardCharsets.UTF_8)),
                request == null ? null : request.tenantId(), request == null ? null : request.scenarioId(),
                request == null ? null : request.productType(), "BLOCKED", null, 0, null, null, null, null,
                null, null, null, null, new NonQmPricingWaterfall(List.of(), null, null, hash), List.of(blocker),
                List.of(sourceRef), hash, correlationId);
    }

    private static void validateRequest(String tenantId, NonQmPricingRequest request) {
        requireNonNull(request, "request is required");
        requireText(request.tenantId(), "request tenant_id is required");
        if (!tenantId.equals(request.tenantId())) {
            throw new NonQmPricingException("request tenant does not match path tenant");
        }
        requireText(request.scenarioId(), "scenario_id is required");
        requireNonNull(request.productType(), "product_type is required");
        requireText(request.investorCode(), "investor_code is required");
        requireText(request.channelCode(), "channel_code is required");
        requireNonNull(request.asOf(), "as_of is required");
    }

    private static void requirePermission(NonQmPricingHeaders headers, String permission) {
        if (headers == null) {
            throw new NonQmPricingException("headers are required");
        }
        requireText(headers.actorId(), "actor_id is required");
        requireText(headers.correlationId(), "correlation_id is required");
        if (!headers.permissions().contains(permission)) {
            throw new NonQmPricingException(permission + " permission is required");
        }
    }

    private static void requireTenant(String tenantId) {
        requireText(tenantId, "tenant_id is required");
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new NonQmPricingException(message);
        }
    }

    private static void requireNonNull(Object value, String message) {
        if (value == null) {
            throw new NonQmPricingException(message);
        }
    }

    private static BigDecimal decimalField(Map<String, String> fields, String... keys) {
        String value = firstNonBlank(keys == null ? null : java.util.Arrays.stream(keys).map(fields::get).toArray(String[]::new));
        return value == null ? null : new BigDecimal(value);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static BigDecimal rate(BigDecimal value) {
        return value == null ? null : value.setScale(RATE_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal price(BigDecimal value) {
        return value == null ? null : value.setScale(PRICE_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : value.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(PRICE_SCALE, RoundingMode.HALF_UP) : value;
    }

    private static String stableHash(Object... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Object value : values) {
                digest.update(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static List<NonQmPricingStrategy> defaultStrategies() {
        return List.of(
                new DscrRentalPricingStrategy(),
                new ProjectPricingStrategy(NonQmProductType.CONSTRUCTION,
                        List.of("projectType", "ltcBand", "reserveBand", "builderStatus", "drawScheduleStatus"),
                        List.of("term", "prepayPenalty", "interestReserveBand", "completionReserveBand"),
                        NonQmPricingApi::constructionFacts),
                new ProjectPricingStrategy(NonQmProductType.FIX_FLIP,
                        List.of("ltarvBand", "rehabBudgetBand", "drawScheduleStatus", "term", "exitStrategy"),
                        List.of("ltcBand", "borrowerExperienceBand", "prepayPenalty"),
                        NonQmPricingApi::fixFlipFacts),
                new ProjectPricingStrategy(NonQmProductType.RENTAL_PORTFOLIO,
                        List.of("entityType", "portfolioDscrBand", "propertyCountBand", "crossCollateral"),
                        List.of("guarantorType", "blanketLoan", "propertyScheduleStatus", "prepayPenalty", "term"),
                        NonQmPricingApi::rentalPortfolioFacts),
                new ConfiguredDimensionPricingStrategy(NonQmProductType.BUSINESS_PURPOSE,
                        List.of("businessPurposeType", "entityType", "ltvBand"),
                        List.of("portfolioDscrBand", "propertyCountBand", "crossCollateral", "guarantorType", "prepayPenalty", "term")),
                new ConfiguredDimensionPricingStrategy(NonQmProductType.BANK_STATEMENT,
                        List.of("statementType", "statementMonths", "ficoBand", "ltvBand"), List.of("incomeTrend", "expenseMethod", "term")),
                new ConfiguredDimensionPricingStrategy(NonQmProductType.ASSET_DEPLETION,
                        List.of("assetType", "assetIncomeMethod", "seasoningBand", "ficoBand", "ltvBand"), List.of("term")),
                new ConfiguredDimensionPricingStrategy(NonQmProductType.NO_RATIO,
                        List.of("ficoBand", "ltvBand", "occupancy"), List.of("propertyType", "term")),
                new ConfiguredDimensionPricingStrategy(NonQmProductType.FOREIGN_NATIONAL,
                        List.of("countryTier", "ltvBand", "creditProfile"), List.of("reservesMonths", "visaStatus")),
                new ConfiguredDimensionPricingStrategy(NonQmProductType.ITIN,
                        List.of("itinStatus", "ficoBand", "ltvBand"), List.of("residencyStatus", "term")),
                new ConfiguredDimensionPricingStrategy(NonQmProductType.ONE099_ONLY,
                        List.of("documentType", "businessHistoryBand", "ficoBand", "ltvBand"), List.of("incomeStability", "term")),
                new ReverseMortgagePricingStrategy());
    }

    public enum NonQmProductType {
        DSCR, CONSTRUCTION, FIX_FLIP, RENTAL_PORTFOLIO, BUSINESS_PURPOSE,
        BANK_STATEMENT, ASSET_DEPLETION, NO_RATIO, FOREIGN_NATIONAL, ITIN, ONE099_ONLY, REVERSE_MORTGAGE
    }

    public enum ReverseProgramType { HECM, PROPRIETARY }

    public enum PaymentOption { LUMP_SUM, LINE_OF_CREDIT, TENURE, TERM, MODIFIED_TENURE, MODIFIED_TERM }

    public enum MipBasis { MAX_CLAIM_AMOUNT, PRINCIPAL_LIMIT }

    public enum RateSheetSource { OPTIMAL_BLUE, POLLY, LOANPASS, INTERNAL }

    public enum RateSheetStatus { PUBLISHED, DRAFT, RETIRED }

    public enum EligibilityStatus { ELIGIBLE, REFER, INELIGIBLE, BLOCKED }

    public record NonQmPricingHeaders(Set<String> permissions, String actorId, String correlationId) {
        public NonQmPricingHeaders {
            permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
        }
    }

    public record NonQmPricingRequest(String tenantId, String scenarioId, NonQmProductType productType,
            String investorCode, String channelCode, Instant asOf, Map<String, String> tierFacts,
            Map<String, BigDecimal> numericFacts, EligibilityDecision eligibilityDecision,
            Map<String, NonQmMarginPolicy> marginPolicies) {
        public NonQmPricingRequest {
            tierFacts = tierFacts == null ? Map.of() : Map.copyOf(tierFacts);
            numericFacts = numericFacts == null ? Map.of() : Map.copyOf(numericFacts);
            marginPolicies = marginPolicies == null ? Map.of() : Map.copyOf(marginPolicies);
        }

        String fact(String key) {
            return tierFacts.get(key);
        }
    }

    public record EligibilityDecision(EligibilityStatus status, String eligibilityRef, String reasonCode) {
        boolean priceable() {
            return status == EligibilityStatus.ELIGIBLE || status == EligibilityStatus.REFER;
        }
    }

    public record NonQmRateSheet(String rateSheetId, String investorCode, String channelCode,
            NonQmProductType productType, int version, LocalDate effectiveDate, RateSheetStatus status,
            List<NonQmRateRow> rows, List<NonQmPricingAdjustmentRef> adjustmentRefs, String marginPolicyRef,
            RateSheetSource source) {
        public NonQmRateSheet {
            requireText(rateSheetId, "rate_sheet_id is required");
            requireText(investorCode, "investor_code is required");
            requireText(channelCode, "channel_code is required");
            requireNonNull(productType, "product_type is required");
            requireNonNull(effectiveDate, "effective_date is required");
            status = status == null ? RateSheetStatus.PUBLISHED : status;
            rows = rows == null ? List.of() : List.copyOf(rows);
            adjustmentRefs = adjustmentRefs == null ? List.of() : List.copyOf(adjustmentRefs);
            source = source == null ? RateSheetSource.INTERNAL : source;
        }
    }

    public record NonQmRateRow(String rowId, BigDecimal noteRate, BigDecimal basePrice,
            Map<String, String> tierKeys, Map<String, BigDecimal> metrics, String investorProductCode,
            String reasonCode) {
        public NonQmRateRow {
            requireText(rowId, "row_id is required");
            noteRate = rate(noteRate);
            basePrice = price(basePrice);
            tierKeys = tierKeys == null ? Map.of() : Map.copyOf(tierKeys);
            metrics = metrics == null ? Map.of() : Map.copyOf(metrics);
            investorProductCode = firstNonBlank(investorProductCode, "UNKNOWN_PRODUCT");
            reasonCode = firstNonBlank(reasonCode, "RATE_SHEET_MATCH");
        }
    }

    public record NonQmPricingAdjustmentRef(String ruleId, String versionRef, String requiredFactKey,
            String requiredFactValue, BigDecimal rateDelta, BigDecimal priceDelta, int precedence, String reasonCode) {
        public NonQmPricingAdjustmentRef {
            requireText(ruleId, "adjustment rule_id is required");
            requireText(versionRef, "adjustment version_ref is required");
            rateDelta = rate(zeroIfNull(rateDelta));
            priceDelta = price(zeroIfNull(priceDelta));
            reasonCode = firstNonBlank(reasonCode, ruleId);
        }

        boolean appliesTo(NonQmPricingRequest request) {
            if (requiredFactKey == null || requiredFactKey.isBlank()) {
                return true;
            }
            return Objects.equals(requiredFactValue, request.fact(requiredFactKey));
        }
    }

    public record NonQmMarginPolicy(String policyRef, BigDecimal rateMargin, BigDecimal priceMargin, String reasonCode) {
        public NonQmMarginPolicy {
            requireText(policyRef, "margin policy_ref is required");
            rateMargin = rate(zeroIfNull(rateMargin));
            priceMargin = price(zeroIfNull(priceMargin));
            reasonCode = firstNonBlank(reasonCode, "NON_QM_MARGIN");
        }
    }

    public record BasePriceResult(NonQmRateRow row, String configRef, String reasonCode,
            List<String> missingFacts, List<PricingFact> pricingFacts, boolean multipleMatches) {
        public BasePriceResult {
            missingFacts = missingFacts == null ? List.of() : List.copyOf(missingFacts);
            pricingFacts = pricingFacts == null ? List.of() : List.copyOf(pricingFacts);
        }

        boolean priceable() {
            return row != null && missingFacts.isEmpty();
        }
    }

    public record PricingFact(String factKey, String factValue, BigDecimal rateDelta, BigDecimal priceDelta,
            String sourceRef, String reasonCode) {
        public PricingFact {
            rateDelta = rate(zeroIfNull(rateDelta));
            priceDelta = price(zeroIfNull(priceDelta));
            reasonCode = firstNonBlank(reasonCode, factKey);
        }
    }

    public record NonQmAdjustmentLine(String ruleId, String versionRef, BigDecimal rateDelta, BigDecimal priceDelta,
            String reasonCode, List<String> auditRefs) {
        public NonQmAdjustmentLine {
            rateDelta = rate(zeroIfNull(rateDelta));
            priceDelta = price(zeroIfNull(priceDelta));
            auditRefs = auditRefs == null ? List.of() : List.copyOf(auditRefs);
        }
    }

    public record NonQmAdjustmentCalculationResult(List<NonQmAdjustmentLine> adjustments, List<String> auditRefs,
            String resultHash, boolean blocked) {
        public NonQmAdjustmentCalculationResult {
            adjustments = adjustments == null ? List.of() : List.copyOf(adjustments);
            auditRefs = auditRefs == null ? List.of() : List.copyOf(auditRefs);
            resultHash = firstNonBlank(resultHash, stableHash(adjustments, auditRefs, blocked));
        }
    }

    public record NonQmMarginResult(String policyRef, BigDecimal rateMargin, BigDecimal priceMargin,
            String reasonCode, boolean blocked) {
        public NonQmMarginResult {
            rateMargin = rate(zeroIfNull(rateMargin));
            priceMargin = price(zeroIfNull(priceMargin));
            reasonCode = firstNonBlank(reasonCode, "NON_QM_MARGIN");
        }
    }

    public record NonQmPricingWaterfall(List<NonQmWaterfallLine> lines, BigDecimal finalNoteRate,
            BigDecimal finalPrice, String waterfallHash) {
        public NonQmPricingWaterfall {
            lines = lines == null ? List.of() : List.copyOf(lines);
            finalNoteRate = rate(finalNoteRate);
            finalPrice = price(finalPrice);
        }
    }

    public record NonQmWaterfallLine(int ordinal, String step, BigDecimal inputRate, BigDecimal outputRate,
            BigDecimal inputPrice, BigDecimal outputPrice, String configRef, String reasonCode) {
        public NonQmWaterfallLine {
            inputRate = rate(inputRate);
            outputRate = rate(outputRate);
            inputPrice = price(inputPrice);
            outputPrice = price(outputPrice);
        }
    }

    public record NonQmBlocker(String code, String message, String sourceRef, String remediationHint) {}

    public record NonQmPriceResult(UUID priceId, String tenantId, String scenarioId, NonQmProductType productType,
            String status, String rateSheetId, int rateSheetVersion, String investorCode, String channelCode,
            String rowId, String investorProductCode, BigDecimal baseNoteRate, BigDecimal basePrice,
            BigDecimal finalNoteRate, BigDecimal finalPrice, NonQmPricingWaterfall waterfall,
            List<NonQmBlocker> blockers, List<String> versionRefs, String resultHash, String correlationId) {
        public NonQmPriceResult {
            blockers = blockers == null ? List.of() : List.copyOf(blockers);
            versionRefs = versionRefs == null ? List.of() : List.copyOf(versionRefs);
        }
    }

    public record NonQmBatchPriceResult(List<NonQmPriceResult> results, UUID bestExecutionPriceId,
            String bestExecutionHash) {
        public NonQmBatchPriceResult {
            results = results == null ? List.of() : List.copyOf(results);
        }
    }

    public record NonQmImportResult(NonQmRateSheet rateSheet, List<NonQmBlocker> blockers) {
        public NonQmImportResult {
            blockers = blockers == null ? List.of() : List.copyOf(blockers);
        }
    }

    public record ReverseProgramConfig(String programCode, ReverseProgramType programType, String investorCode,
            PrincipalLimitTable principalLimitTable, MipPolicy mipPolicy, LocGrowthPolicy locGrowthPolicy,
            ServicingFeeSetAsidePolicy servicingFeeSetAsidePolicy, List<PaymentOption> paymentOptions,
            BigDecimal maxClaimAmountCap, BigDecimal configuredClosingCostEstimate,
            Map<String, String> proprietaryInvestorRules) {
        public ReverseProgramConfig {
            requireText(programCode, "reverse program_code is required");
            requireNonNull(programType, "reverse program_type is required");
            requireText(investorCode, "reverse investor_code is required");
            requireNonNull(principalLimitTable, "principal_limit_table is required");
            requireNonNull(mipPolicy, "mip_policy is required");
            requireNonNull(locGrowthPolicy, "loc_growth_policy is required");
            requireNonNull(servicingFeeSetAsidePolicy, "servicing_fee_set_aside_policy is required");
            paymentOptions = paymentOptions == null ? List.of() : List.copyOf(paymentOptions);
            proprietaryInvestorRules = proprietaryInvestorRules == null ? Map.of() : Map.copyOf(proprietaryInvestorRules);
        }

        BigDecimal maxClaimAmount(BigDecimal propertyValue) {
            BigDecimal value = money(propertyValue);
            if (maxClaimAmountCap == null || maxClaimAmountCap.compareTo(BigDecimal.ZERO) <= 0) {
                return value;
            }
            return value.min(maxClaimAmountCap);
        }
    }

    public record ReversePricingInputs(int youngestBorrowerAge, BigDecimal propertyValue,
            BigDecimal existingMortgageBalance, BigDecimal indexRate, BigDecimal margin, PaymentOption paymentOption,
            String state, String propertyType, int termMonths) {
        public ReversePricingInputs {
            requireNonNull(propertyValue, "reverse property_value is required");
            existingMortgageBalance = money(existingMortgageBalance);
            indexRate = rate(zeroIfNull(indexRate));
            margin = rate(zeroIfNull(margin));
            requireNonNull(paymentOption, "reverse payment_option is required");
        }

        public BigDecimal expectedRate() {
            return rate(indexRate.add(margin));
        }
    }

    public record PrincipalLimitTable(String tableId, int version, List<PrincipalLimitFactor> factors,
            String sourceRef) {
        public PrincipalLimitTable {
            requireText(tableId, "reverse plf table_id is required");
            factors = factors == null ? List.of() : List.copyOf(factors);
            sourceRef = firstNonBlank(sourceRef, tableId);
        }

        PrincipalLimitFactor lookup(int age, BigDecimal expectedRate) {
            return factors.stream()
                    .filter(row -> row.matches(age, expectedRate))
                    .sorted(Comparator.comparing(PrincipalLimitFactor::minAge).reversed()
                            .thenComparing(PrincipalLimitFactor::maxExpectedRate))
                    .findFirst()
                    .orElse(null);
        }

        String tableVersionRef() {
            return tableId + ":v" + version;
        }
    }

    public record PrincipalLimitFactor(int minAge, int maxAge, BigDecimal minExpectedRate,
            BigDecimal maxExpectedRate, BigDecimal factor, String auditRef) {
        public PrincipalLimitFactor {
            minExpectedRate = rate(minExpectedRate);
            maxExpectedRate = rate(maxExpectedRate);
            requireNonNull(minExpectedRate, "principal_limit_factor min_expected_rate is required");
            requireNonNull(maxExpectedRate, "principal_limit_factor max_expected_rate is required");
            factor = factor == null ? null : factor.setScale(6, RoundingMode.HALF_UP);
            requireNonNull(factor, "principal_limit_factor is required");
            auditRef = firstNonBlank(auditRef, "plf:" + minAge + ":" + maxAge + ":" + maxExpectedRate);
        }

        boolean matches(int age, BigDecimal expectedRate) {
            BigDecimal comparableRate = rate(expectedRate);
            return age >= minAge && age <= maxAge
                    && comparableRate.compareTo(minExpectedRate) >= 0
                    && comparableRate.compareTo(maxExpectedRate) <= 0;
        }
    }

    public record MipPolicy(String policyRef, MipBasis initialMipBasis, BigDecimal initialMipRate,
            BigDecimal annualMipRate) {
        public MipPolicy {
            requireText(policyRef, "mip policy_ref is required");
            initialMipBasis = initialMipBasis == null ? MipBasis.PRINCIPAL_LIMIT : initialMipBasis;
            initialMipRate = rate(zeroIfNull(initialMipRate));
            annualMipRate = rate(zeroIfNull(annualMipRate));
        }

        MipResult calculate(BigDecimal maxClaimAmount, BigDecimal principalLimit) {
            BigDecimal basis = initialMipBasis == MipBasis.MAX_CLAIM_AMOUNT ? maxClaimAmount : principalLimit;
            BigDecimal initialMip = money(basis.multiply(initialMipRate));
            BigDecimal annualMip = money(principalLimit.multiply(annualMipRate));
            return new MipResult(policyRef, initialMipBasis, initialMipRate, initialMip, annualMipRate, annualMip);
        }
    }

    public record MipResult(String policyRef, MipBasis initialMipBasis, BigDecimal initialMipRate,
            BigDecimal initialMip, BigDecimal annualMipRate, BigDecimal annualMipEstimate) {}

    public record ServicingFeeSetAsidePolicy(String policyRef, BigDecimal monthlyServicingFee,
            int setAsideMonths) {
        public ServicingFeeSetAsidePolicy {
            requireText(policyRef, "servicing fee set-aside policy_ref is required");
            monthlyServicingFee = money(monthlyServicingFee);
        }

        ServicingFeeSetAsideResult calculate(ReversePricingInputs inputs) {
            int months = setAsideMonths > 0 ? setAsideMonths : Math.max(inputs.termMonths(), 0);
            return new ServicingFeeSetAsideResult(policyRef, monthlyServicingFee, months,
                    money(monthlyServicingFee.multiply(BigDecimal.valueOf(months))));
        }
    }

    public record ServicingFeeSetAsideResult(String policyRef, BigDecimal monthlyServicingFee,
            int setAsideMonths, BigDecimal amount) {}

    public record LocGrowthPolicy(String policyRef, BigDecimal growthRate, int tenureDisbursementMonths) {
        public LocGrowthPolicy {
            requireText(policyRef, "loc growth policy_ref is required");
            growthRate = rate(zeroIfNull(growthRate));
        }

        ReversePaymentOptionEstimate estimate(PaymentOption option, BigDecimal netAvailable, ReversePricingInputs inputs) {
            BigDecimal available = money(netAvailable.max(BigDecimal.ZERO));
            if (option == PaymentOption.LINE_OF_CREDIT) {
                return new ReversePaymentOptionEstimate(option, available, null, growthRate,
                        "configured LOC growth policy " + policyRef);
            }
            if (option == PaymentOption.TERM || option == PaymentOption.MODIFIED_TERM) {
                int months = Math.max(inputs.termMonths(), 0);
                BigDecimal monthly = months == 0 ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                        : available.divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);
                return new ReversePaymentOptionEstimate(option, available, monthly, growthRate,
                        "configured term months " + months);
            }
            if (option == PaymentOption.TENURE || option == PaymentOption.MODIFIED_TENURE) {
                int months = Math.max(tenureDisbursementMonths, 0);
                BigDecimal monthly = months == 0 ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                        : available.divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);
                return new ReversePaymentOptionEstimate(option, available, monthly, growthRate,
                        "configured tenure months " + months);
            }
            return new ReversePaymentOptionEstimate(option, available, available, growthRate, "lump sum net proceeds");
        }
    }

    public record ReversePaymentOptionEstimate(PaymentOption option, BigDecimal netProceeds,
            BigDecimal estimatedPeriodicPayment, BigDecimal locGrowthRate, String assumption) {}

    public record ReversePricingBreakdown(String programCode, ReverseProgramType programType, String investorCode,
            BigDecimal expectedRate, BigDecimal maxClaimAmount, PrincipalLimitFactor principalLimitFactor,
            BigDecimal principalLimit, MipResult mip, ServicingFeeSetAsideResult servicingFeeSetAside,
            BigDecimal closingCostEstimate, BigDecimal netAvailable, ReversePaymentOptionEstimate paymentOptionEstimate,
            List<String> auditRefs, List<NonQmBlocker> blockers, String resultHash) {
        public ReversePricingBreakdown {
            auditRefs = auditRefs == null ? List.of() : List.copyOf(auditRefs);
            blockers = blockers == null ? List.of() : List.copyOf(blockers);
        }

        static ReversePricingBreakdown blocked(String code, String message, String sourceRef) {
            NonQmBlocker blocker = new NonQmBlocker(code, message, sourceRef,
                    "publish required reverse mortgage configuration or correct request facts");
            return new ReversePricingBreakdown(null, null, null, null, null, null, null, null, null,
                    null, null, null, List.of(sourceRef), List.of(blocker), stableHash("reverse-blocked", code, message, sourceRef));
        }
    }

    public interface NonQmRateSheetResolver {
        Optional<NonQmRateSheet> resolve(NonQmPricingRequest request);
    }

    public static final class StaticNonQmRateSheetResolver implements NonQmRateSheetResolver {
        private final List<NonQmRateSheet> sheets;

        public StaticNonQmRateSheetResolver(List<NonQmRateSheet> sheets) {
            this.sheets = sheets == null ? List.of() : List.copyOf(sheets);
        }

        @Override
        public Optional<NonQmRateSheet> resolve(NonQmPricingRequest request) {
            LocalDate asOfDate = LocalDate.ofInstant(request.asOf(), ZoneOffset.UTC);
            return sheets.stream()
                    .filter(sheet -> sheet.status() == RateSheetStatus.PUBLISHED)
                    .filter(sheet -> sheet.productType() == request.productType())
                    .filter(sheet -> sheet.investorCode().equalsIgnoreCase(request.investorCode()))
                    .filter(sheet -> sheet.channelCode().equalsIgnoreCase(request.channelCode()))
                    .filter(sheet -> !sheet.effectiveDate().isAfter(asOfDate))
                    .sorted(Comparator.comparing(NonQmRateSheet::effectiveDate).reversed()
                            .thenComparing(Comparator.comparing(NonQmRateSheet::version).reversed()))
                    .findFirst();
        }
    }

    public interface NonQmPricingStrategy {
        NonQmProductType supportsType();
        BasePriceResult selectBasePrice(NonQmPricingRequest request, NonQmRateSheet rateSheet);
        List<String> requiredFacts();
    }

    public static final class NonQmPricingStrategyRegistry {
        private final Map<NonQmProductType, NonQmPricingStrategy> strategies;

        public NonQmPricingStrategyRegistry(List<NonQmPricingStrategy> strategies) {
            EnumMap<NonQmProductType, NonQmPricingStrategy> registered = new EnumMap<>(NonQmProductType.class);
            for (NonQmPricingStrategy strategy : strategies == null ? List.<NonQmPricingStrategy>of() : strategies) {
                if (registered.putIfAbsent(strategy.supportsType(), strategy) != null) {
                    throw new NonQmPricingException("duplicate Non-QM strategy for " + strategy.supportsType());
                }
            }
            this.strategies = Map.copyOf(registered);
        }

        NonQmPricingStrategy get(NonQmProductType type) {
            NonQmPricingStrategy strategy = strategies.get(type);
            if (strategy == null) {
                throw new NonQmPricingException("Non-QM strategy is not registered for " + type);
            }
            return strategy;
        }
    }

    public record DrawMilestone(String milestoneId, String description, BigDecimal amount, int ordinal) {
        public DrawMilestone {
            requireText(milestoneId, "draw milestone_id is required");
            amount = price(zeroIfNull(amount));
        }
    }

    public record ConstructionPricingFacts(BigDecimal landOrPurchaseCost, BigDecimal hardCostBudget,
            BigDecimal softCostBudget, BigDecimal loanAmount, BigDecimal completionReserve,
            BigDecimal interestReserve, List<DrawMilestone> drawSchedule, String builderApprovalStatus) {
        public ConstructionPricingFacts {
            landOrPurchaseCost = price(zeroIfNull(landOrPurchaseCost));
            hardCostBudget = price(zeroIfNull(hardCostBudget));
            softCostBudget = price(zeroIfNull(softCostBudget));
            loanAmount = price(zeroIfNull(loanAmount));
            completionReserve = price(zeroIfNull(completionReserve));
            interestReserve = price(zeroIfNull(interestReserve));
            drawSchedule = drawSchedule == null ? List.of() : List.copyOf(drawSchedule);
            builderApprovalStatus = firstNonBlank(builderApprovalStatus, "UNKNOWN");
        }

        public BigDecimal totalCost() {
            return price(landOrPurchaseCost.add(hardCostBudget).add(softCostBudget));
        }

        public BigDecimal drawBudget() {
            return price(hardCostBudget.add(softCostBudget));
        }

        public BigDecimal drawScheduleTotal() {
            return price(drawSchedule.stream().map(DrawMilestone::amount).reduce(BigDecimal.ZERO, BigDecimal::add));
        }

        public BigDecimal ltc() {
            return ratio(loanAmount, totalCost());
        }
    }

    public record FixFlipPricingFacts(BigDecimal purchasePrice, BigDecimal rehabBudget, BigDecimal afterRepairValue,
            BigDecimal loanAmount, Integer termMonths, String exitStrategy, List<DrawMilestone> drawSchedule) {
        public FixFlipPricingFacts {
            purchasePrice = price(zeroIfNull(purchasePrice));
            rehabBudget = price(zeroIfNull(rehabBudget));
            afterRepairValue = price(zeroIfNull(afterRepairValue));
            loanAmount = price(zeroIfNull(loanAmount));
            exitStrategy = firstNonBlank(exitStrategy, "UNKNOWN");
            drawSchedule = drawSchedule == null ? List.of() : List.copyOf(drawSchedule);
        }

        public BigDecimal totalProjectCost() {
            return price(purchasePrice.add(rehabBudget));
        }

        public BigDecimal drawScheduleTotal() {
            return price(drawSchedule.stream().map(DrawMilestone::amount).reduce(BigDecimal.ZERO, BigDecimal::add));
        }

        public BigDecimal ltc() {
            return ratio(loanAmount, totalProjectCost());
        }

        public BigDecimal ltArv() {
            return ratio(loanAmount, afterRepairValue);
        }
    }

    public record RentalPortfolioPricingFacts(BigDecimal portfolioNoi, BigDecimal portfolioDebtService,
            BigDecimal loanAmount, BigDecimal totalCollateralValue, Integer propertyCount, boolean crossCollateral,
            String entityType, String guarantorType, boolean blanketLoan) {
        public RentalPortfolioPricingFacts {
            portfolioNoi = price(zeroIfNull(portfolioNoi));
            portfolioDebtService = price(zeroIfNull(portfolioDebtService));
            loanAmount = price(zeroIfNull(loanAmount));
            totalCollateralValue = price(zeroIfNull(totalCollateralValue));
            propertyCount = propertyCount == null ? 0 : propertyCount;
            entityType = firstNonBlank(entityType, "UNKNOWN");
            guarantorType = firstNonBlank(guarantorType, "UNKNOWN");
        }

        public BigDecimal portfolioDscr() {
            return ratio(portfolioNoi, portfolioDebtService);
        }

        public BigDecimal blanketLtv() {
            return ratio(loanAmount, totalCollateralValue);
        }
    }

    private static final class DscrRentalPricingStrategy implements NonQmPricingStrategy {
        private final ConfiguredDimensionPricingStrategy delegate = new ConfiguredDimensionPricingStrategy(NonQmProductType.DSCR,
                List.of("dscrTier", "ficoBand", "ltvBand"), List.of("term", "occupancy", "propertyType", "prepayPenalty", "interestOnly"));

        @Override
        public NonQmProductType supportsType() {
            return NonQmProductType.DSCR;
        }

        @Override
        public List<String> requiredFacts() {
            return delegate.requiredFacts();
        }

        @Override
        public BasePriceResult selectBasePrice(NonQmPricingRequest request, NonQmRateSheet rateSheet) {
            List<PricingFact> rentalFacts = new ArrayList<>();
            BigDecimal noi = request.numericFacts().get("rental.noi");
            BigDecimal debtService = request.numericFacts().get("rental.debtService");
            if (noi != null || debtService != null) {
                if (positive(noi) && positive(debtService)) {
                    rentalFacts.add(pricingFact("rental.portfolioDscr", ratio(noi, debtService), rateSheet.rateSheetId(), "RENTAL_PORTFOLIO_DSCR"));
                } else {
                    return new BasePriceResult(null, rateSheet.rateSheetId(), "RENTAL_DSCR_FACT_INVALID",
                            List.of("rental.noi", "rental.debtService"), List.of(), false);
                }
            }
            BasePriceResult base = delegate.selectBasePrice(request, rateSheet);
            if (!base.priceable()) {
                return base;
            }
            List<PricingFact> facts = new ArrayList<>(base.pricingFacts());
            facts.addAll(rentalFacts);
            return new BasePriceResult(base.row(), base.configRef(), base.reasonCode(), List.of(), facts, base.multipleMatches());
        }
    }

    private static final class ProjectPricingStrategy implements NonQmPricingStrategy {
        private final NonQmProductType type;
        private final List<String> requiredFacts;
        private final List<String> optionalFacts;
        private final Function<NonQmPricingRequest, List<PricingFact>> factFactory;

        private ProjectPricingStrategy(NonQmProductType type, List<String> requiredFacts, List<String> optionalFacts,
                Function<NonQmPricingRequest, List<PricingFact>> factFactory) {
            this.type = type;
            this.requiredFacts = List.copyOf(requiredFacts);
            this.optionalFacts = List.copyOf(optionalFacts);
            this.factFactory = factFactory;
        }

        @Override
        public NonQmProductType supportsType() {
            return type;
        }

        @Override
        public List<String> requiredFacts() {
            return requiredFacts;
        }

        @Override
        public BasePriceResult selectBasePrice(NonQmPricingRequest request, NonQmRateSheet rateSheet) {
            List<PricingFact> derivedFacts;
            try {
                derivedFacts = factFactory.apply(request);
            } catch (NonQmPricingException ex) {
                return new BasePriceResult(null, rateSheet.rateSheetId(), type.name() + "_FACT_INVALID",
                        List.of(ex.getMessage()), List.of(), false);
            }
            return selectConfiguredRow(type, request, rateSheet, requiredFacts, optionalFacts, derivedFacts);
        }
    }

    private static final class ReverseMortgagePricingStrategy implements NonQmPricingStrategy {
        private static final List<String> REQUIRED = List.of("reverse.programType", "reverse.ageBand",
                "reverse.equityBand", "reverse.loanAmountBand", "reverse.paymentOption");
        private static final List<String> OPTIONAL = List.of("reverse.state", "reverse.propertyType", "reverse.plfTableId",
                "reverse.investorAvailability", "reverse.locGrowthPolicyRef");

        @Override
        public NonQmProductType supportsType() {
            return NonQmProductType.REVERSE_MORTGAGE;
        }

        @Override
        public List<String> requiredFacts() {
            return REQUIRED;
        }

        @Override
        public BasePriceResult selectBasePrice(NonQmPricingRequest request, NonQmRateSheet rateSheet) {
            BigDecimal indexRate = request.numericFacts().get("reverse.indexRate");
            BigDecimal margin = request.numericFacts().get("reverse.margin");
            if (indexRate == null || margin == null) {
                return new BasePriceResult(null, rateSheet.rateSheetId(), "REVERSE_EXPECTED_RATE_FACTS_MISSING",
                        List.of("reverse.indexRate", "reverse.margin"), List.of(), false);
            }
            BigDecimal expectedRate = rate(indexRate.add(margin));
            List<PricingFact> reverseFacts = new ArrayList<>();
            reverseFacts.add(pricingFact("reverse.expectedRate", expectedRate,
                    rateSheet.rateSheetId() + ":expectedRate=" + expectedRate.toPlainString(),
                    "REVERSE_EXPECTED_RATE_INDEX_PLUS_MARGIN"));
            for (String key : List.of("reverse.principalLimit", "reverse.initialMip", "reverse.annualMipEstimate",
                    "reverse.servicingFeeSetAside", "reverse.netProceeds")) {
                BigDecimal value = request.numericFacts().get(key);
                if (value != null) {
                    reverseFacts.add(pricingFact(key, value, rateSheet.rateSheetId(), key.toUpperCase().replace('.', '_')));
                }
            }
            return selectConfiguredRow(NonQmProductType.REVERSE_MORTGAGE, request, rateSheet, REQUIRED, OPTIONAL, reverseFacts);
        }
    }

    private static final class ConfiguredDimensionPricingStrategy implements NonQmPricingStrategy {
        private final NonQmProductType type;
        private final List<String> requiredFacts;
        private final List<String> optionalFacts;

        private ConfiguredDimensionPricingStrategy(NonQmProductType type, List<String> requiredFacts, List<String> optionalFacts) {
            this.type = type;
            this.requiredFacts = List.copyOf(requiredFacts);
            this.optionalFacts = List.copyOf(optionalFacts);
        }

        @Override
        public NonQmProductType supportsType() {
            return type;
        }

        @Override
        public List<String> requiredFacts() {
            return requiredFacts;
        }

        @Override
        public BasePriceResult selectBasePrice(NonQmPricingRequest request, NonQmRateSheet rateSheet) {
            return selectConfiguredRow(type, request, rateSheet, requiredFacts, optionalFacts, List.of());
        }
    }

    private static BasePriceResult selectConfiguredRow(NonQmProductType type, NonQmPricingRequest request,
            NonQmRateSheet rateSheet, List<String> requiredFacts, List<String> optionalFacts,
            List<PricingFact> additionalFacts) {
        List<String> missing = requiredFacts.stream()
                .filter(fact -> request.fact(fact) == null || request.fact(fact).isBlank())
                .toList();
        if (!missing.isEmpty()) {
            return new BasePriceResult(null, rateSheet.rateSheetId(), "SPECIALTY_DIMENSION_MISSING", missing, List.of(), false);
        }

        Set<String> matchKeys = new HashSet<>();
        matchKeys.addAll(requiredFacts);
        matchKeys.addAll(optionalFacts.stream().filter(fact -> request.fact(fact) != null).toList());
        List<NonQmRateRow> matches = rateSheet.rows().stream()
                .filter(row -> matchKeys.stream().allMatch(key -> !row.tierKeys().containsKey(key)
                        || Objects.equals(row.tierKeys().get(key), request.fact(key))))
                .filter(row -> requiredFacts.stream().allMatch(key -> Objects.equals(row.tierKeys().get(key), request.fact(key))))
                .sorted(Comparator.comparingInt((NonQmRateRow row) -> row.tierKeys().size()).reversed()
                        .thenComparing(NonQmRateRow::noteRate)
                        .thenComparing(NonQmRateRow::rowId))
                .toList();
        if (matches.isEmpty()) {
            return new BasePriceResult(null, rateSheet.rateSheetId(), "RATE_ROW_MISSING", requiredFacts, List.of(), false);
        }
        NonQmRateRow selected = matches.get(0);
        List<PricingFact> specialtyFacts = new ArrayList<>();
        requiredFacts.stream()
                .map(fact -> new PricingFact(fact, request.fact(fact), BigDecimal.ZERO, BigDecimal.ZERO,
                        rateSheet.rateSheetId() + ":" + selected.rowId(), type.name() + "_" + fact.toUpperCase()))
                .forEach(specialtyFacts::add);
        if (additionalFacts != null) {
            specialtyFacts.addAll(additionalFacts);
        }
        return new BasePriceResult(selected, rateSheet.rateSheetId() + ":" + selected.rowId(), selected.reasonCode(),
                List.of(), specialtyFacts, matches.size() > 1);
    }

    private static List<PricingFact> constructionFacts(NonQmPricingRequest request) {
        ConstructionPricingFacts facts = new ConstructionPricingFacts(
                requiredDecimal(request, "construction.landOrPurchaseCost"),
                requiredDecimal(request, "construction.hardCostBudget"),
                requiredDecimal(request, "construction.softCostBudget"),
                requiredDecimal(request, "construction.loanAmount"),
                requiredDecimal(request, "construction.completionReserve"),
                requiredDecimal(request, "construction.interestReserve"),
                drawSchedule(request, "construction"),
                request.fact("builderStatus"));
        requirePositive(facts.totalCost(), "construction.totalCost");
        requirePositive(facts.loanAmount(), "construction.loanAmount");
        validateDrawScheduleTotal("construction.drawScheduleTotal", facts.drawScheduleTotal(), facts.drawBudget());
        return List.of(
                pricingFact("construction.totalCost", facts.totalCost(), "pricing-service.nonqm.business-purpose", "CONSTRUCTION_TOTAL_COST"),
                pricingFact("construction.ltc", facts.ltc(), "pricing-service.nonqm.business-purpose", "CONSTRUCTION_LTC"),
                pricingFact("construction.interestReserve", facts.interestReserve(), "pricing-service.nonqm.business-purpose", "CONSTRUCTION_INTEREST_RESERVE"),
                pricingFact("construction.completionReserve", facts.completionReserve(), "pricing-service.nonqm.business-purpose", "CONSTRUCTION_COMPLETION_RESERVE"),
                pricingFact("construction.drawScheduleTotal", facts.drawScheduleTotal(), "pricing-service.nonqm.business-purpose", "CONSTRUCTION_DRAW_SCHEDULE"),
                new PricingFact("construction.builderStatus", facts.builderApprovalStatus(), BigDecimal.ZERO, BigDecimal.ZERO,
                        "pricing-service.nonqm.business-purpose", "CONSTRUCTION_BUILDER_STATUS"));
    }

    private static List<PricingFact> fixFlipFacts(NonQmPricingRequest request) {
        FixFlipPricingFacts facts = new FixFlipPricingFacts(
                requiredDecimal(request, "fixFlip.purchasePrice"),
                requiredDecimal(request, "fixFlip.rehabBudget"),
                requiredDecimal(request, "fixFlip.afterRepairValue"),
                requiredDecimal(request, "fixFlip.loanAmount"),
                integerFact(request, "fixFlip.termMonths"),
                request.fact("exitStrategy"),
                drawSchedule(request, "fixFlip"));
        requirePositive(facts.afterRepairValue(), "fixFlip.afterRepairValue");
        requirePositive(facts.totalProjectCost(), "fixFlip.totalProjectCost");
        requirePositive(facts.loanAmount(), "fixFlip.loanAmount");
        validateDrawScheduleTotal("fixFlip.drawScheduleTotal", facts.drawScheduleTotal(), facts.rehabBudget());
        return List.of(
                pricingFact("fixFlip.totalProjectCost", facts.totalProjectCost(), "pricing-service.nonqm.business-purpose", "FIX_FLIP_TOTAL_PROJECT_COST"),
                pricingFact("fixFlip.ltc", facts.ltc(), "pricing-service.nonqm.business-purpose", "FIX_FLIP_LTC"),
                pricingFact("fixFlip.ltArv", facts.ltArv(), "pricing-service.nonqm.business-purpose", "FIX_FLIP_LTARV"),
                pricingFact("fixFlip.rehabBudget", facts.rehabBudget(), "pricing-service.nonqm.business-purpose", "FIX_FLIP_REHAB_BUDGET"),
                pricingFact("fixFlip.drawScheduleTotal", facts.drawScheduleTotal(), "pricing-service.nonqm.business-purpose", "FIX_FLIP_DRAW_SCHEDULE"),
                new PricingFact("fixFlip.exitStrategy", facts.exitStrategy(), BigDecimal.ZERO, BigDecimal.ZERO,
                        "pricing-service.nonqm.business-purpose", "FIX_FLIP_EXIT_STRATEGY"));
    }

    private static List<PricingFact> rentalPortfolioFacts(NonQmPricingRequest request) {
        boolean crossCollateral = Boolean.parseBoolean(firstNonBlank(request.fact("crossCollateral"), "false"));
        RentalPortfolioPricingFacts facts = new RentalPortfolioPricingFacts(
                requiredDecimal(request, "rentalPortfolio.noi"),
                requiredDecimal(request, "rentalPortfolio.debtService"),
                requiredDecimal(request, "rentalPortfolio.loanAmount"),
                requiredDecimal(request, "rentalPortfolio.totalCollateralValue"),
                integerFact(request, "rentalPortfolio.propertyCount"),
                crossCollateral,
                request.fact("entityType"),
                request.fact("guarantorType"),
                Boolean.parseBoolean(firstNonBlank(request.fact("blanketLoan"), "false")));
        requirePositive(facts.portfolioDebtService(), "rentalPortfolio.debtService");
        requirePositive(facts.portfolioNoi(), "rentalPortfolio.noi");
        requirePositive(facts.loanAmount(), "rentalPortfolio.loanAmount");
        requirePositive(facts.totalCollateralValue(), "rentalPortfolio.totalCollateralValue");
        if (facts.propertyCount() <= 0) {
            throw new NonQmPricingException("rentalPortfolio.propertyCount");
        }
        if (facts.crossCollateral() && request.fact("propertyScheduleStatus") == null) {
            throw new NonQmPricingException("propertyScheduleStatus");
        }
        return List.of(
                pricingFact("rentalPortfolio.portfolioDscr", facts.portfolioDscr(), "pricing-service.nonqm.business-purpose", "RENTAL_PORTFOLIO_DSCR"),
                pricingFact("rentalPortfolio.blanketLtv", facts.blanketLtv(), "pricing-service.nonqm.business-purpose", "RENTAL_PORTFOLIO_BLANKET_LTV"),
                new PricingFact("rentalPortfolio.propertyCount", String.valueOf(facts.propertyCount()), BigDecimal.ZERO, BigDecimal.ZERO,
                        "pricing-service.nonqm.business-purpose", "RENTAL_PORTFOLIO_PROPERTY_COUNT"),
                new PricingFact("rentalPortfolio.crossCollateral", String.valueOf(facts.crossCollateral()), BigDecimal.ZERO, BigDecimal.ZERO,
                        "pricing-service.nonqm.business-purpose", "RENTAL_PORTFOLIO_CROSS_COLLATERAL"),
                new PricingFact("rentalPortfolio.blanketLoan", String.valueOf(facts.blanketLoan()), BigDecimal.ZERO, BigDecimal.ZERO,
                        "pricing-service.nonqm.business-purpose", "RENTAL_PORTFOLIO_BLANKET_LOAN"));
    }

    private static List<DrawMilestone> drawSchedule(NonQmPricingRequest request, String prefix) {
        BigDecimal total = request.numericFacts().get(prefix + ".drawScheduleTotal");
        if (total == null) {
            throw new NonQmPricingException(prefix + ".drawScheduleTotal");
        }
        Integer count = integerFact(request, prefix + ".drawCount");
        if (count == null || count <= 0) {
            throw new NonQmPricingException(prefix + ".drawCount");
        }
        return List.of(new DrawMilestone(prefix + "-draws", "configured draw schedule total", total, 1));
    }

    private static BigDecimal requiredDecimal(NonQmPricingRequest request, String key) {
        BigDecimal value = request.numericFacts().get(key);
        if (value == null) {
            throw new NonQmPricingException(key);
        }
        return value;
    }

    private static Integer integerFact(NonQmPricingRequest request, String key) {
        BigDecimal value = request.numericFacts().get(key);
        return value == null ? null : value.setScale(0, RoundingMode.UNNECESSARY).intValueExact();
    }

    private static void requirePositive(BigDecimal value, String key) {
        if (!positive(value)) {
            throw new NonQmPricingException(key);
        }
    }

    private static boolean positive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    private static void validateDrawScheduleTotal(String key, BigDecimal actual, BigDecimal expected) {
        if (actual == null || expected == null || actual.compareTo(expected) != 0) {
            throw new NonQmPricingException(key);
        }
    }

    private static BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
        requirePositive(denominator, "ratio.denominator");
        return numerator.divide(denominator, 4, RoundingMode.HALF_UP);
    }

    private static PricingFact pricingFact(String key, BigDecimal value, String sourceRef, String reasonCode) {
        return new PricingFact(key, value.toPlainString(), BigDecimal.ZERO, BigDecimal.ZERO, sourceRef, reasonCode);
    }

    public interface NonQmAdjustmentClient {
        NonQmAdjustmentCalculationResult calculate(NonQmPricingRequest request, BasePriceResult base,
                List<NonQmPricingAdjustmentRef> adjustmentRefs);
    }

    private static final class ConfiguredNonQmAdjustmentClient implements NonQmAdjustmentClient {
        @Override
        public NonQmAdjustmentCalculationResult calculate(NonQmPricingRequest request, BasePriceResult base,
                List<NonQmPricingAdjustmentRef> adjustmentRefs) {
            List<NonQmAdjustmentLine> lines = adjustmentRefs.stream()
                    .filter(ref -> ref.appliesTo(request))
                    .sorted(Comparator.comparing(NonQmPricingAdjustmentRef::precedence).thenComparing(NonQmPricingAdjustmentRef::ruleId))
                    .map(ref -> new NonQmAdjustmentLine(ref.ruleId(), ref.versionRef(), ref.rateDelta(), ref.priceDelta(),
                            ref.reasonCode(), List.of(ref.versionRef() + ":" + ref.ruleId())))
                    .toList();
            return new NonQmAdjustmentCalculationResult(lines,
                    lines.stream().flatMap(line -> line.auditRefs().stream()).toList(),
                    stableHash("non-qm-adjustments", request.scenarioId(), base.row().rowId(), lines), false);
        }
    }

    public interface NonQmMarginClient {
        NonQmMarginResult calculateNonQmMargin(NonQmPricingRequest request, BasePriceResult base,
                NonQmAdjustmentCalculationResult adjustments, String marginPolicyRef,
                Map<String, NonQmMarginPolicy> marginPolicies);
    }

    private static final class ConfiguredNonQmMarginClient implements NonQmMarginClient {
        @Override
        public NonQmMarginResult calculateNonQmMargin(NonQmPricingRequest request, BasePriceResult base,
                NonQmAdjustmentCalculationResult adjustments, String marginPolicyRef,
                Map<String, NonQmMarginPolicy> marginPolicies) {
            if (marginPolicyRef == null || marginPolicyRef.isBlank()) {
                return new NonQmMarginResult("missing-margin-policy", BigDecimal.ZERO, BigDecimal.ZERO,
                        "rate sheet margin_policy_ref is required", true);
            }
            NonQmMarginPolicy policy = marginPolicies.get(marginPolicyRef);
            if (policy == null) {
                return new NonQmMarginResult(marginPolicyRef, BigDecimal.ZERO, BigDecimal.ZERO,
                        "margin policy was not provided", true);
            }
            return new NonQmMarginResult(policy.policyRef(), policy.rateMargin(), policy.priceMargin(), policy.reasonCode(), false);
        }
    }

    public static class NonQmPricingException extends RuntimeException {
        public NonQmPricingException(String message) {
            super(message);
        }
    }
}
