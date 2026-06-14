package com.wcpe.pricing.homeequity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class HomeEquityPricingApi {
    public static final String HOME_EQUITY_PRICE_PERMISSION = "pricing.home-equity.price";

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal MONTHS_PER_YEAR = new BigDecimal("12");
    private static final int RATE_SCALE = 5;
    private static final int MONEY_SCALE = 2;

    public HomeEquityPriceResponse price(String tenantId, HomeEquityPricingHeaders headers,
            HomeEquityPriceRequest request) {
        requireTenant(tenantId);
        requirePermission(headers, HOME_EQUITY_PRICE_PERMISSION);
        validateRequest(tenantId, request);

        List<HomeEquityBlocker> blockers = new ArrayList<>();
        HomeEquityPricingConfiguration config = request.configuration();
        if (!config.productFamilyEnabled()) {
            blockers.add(new HomeEquityBlocker("HOME_EQUITY_DISABLED",
                    "tenant product authorization does not enable home-equity pricing",
                    "tenant-product-authorization", "enable home-equity product family for this tenant"));
        }

        BigDecimal cltv = calculateCltv(request);
        if (config.maxCombinedLoanToValue() != null && cltv.compareTo(config.maxCombinedLoanToValue()) > 0) {
            blockers.add(new HomeEquityBlocker("CLTV_LIMIT_EXCEEDED",
                    "combined loan-to-value exceeds configured second-lien limit",
                    config.versionRef() + ":maxCombinedLoanToValue",
                    "requested_cltv=" + percent(cltv) + "; configured_max_cltv=" + percent(config.maxCombinedLoanToValue())));
        }

        IndexRateConfig indexConfig = config.indexRates().stream()
                .filter(index -> index.indexCode() == request.indexCode())
                .findFirst()
                .orElse(null);
        if (indexConfig == null) {
            blockers.add(new HomeEquityBlocker("INDEX_CONFIGURATION_MISSING",
                    "home-equity index rate configuration is required for " + request.indexCode(),
                    config.versionRef() + ":indexRates", "publish Prime, SOFR, or COFI index configuration for the requested product"));
        }

        List<HomeEquityAdjustmentResult> adjustments = applyAdjustments(config, request, cltv);
        int totalMarginBps = config.baseMarginBps()
                + adjustments.stream().mapToInt(HomeEquityAdjustmentResult::amountBps).sum();

        if (!blockers.isEmpty()) {
            String resultHash = stableHash("home-equity-blocked", tenantId, request.scenarioId(), request.productType(),
                    request.indexCode(), cltv, config.versionRef(), blockers, adjustments);
            return new HomeEquityPriceResponse(UUID.nameUUIDFromBytes((tenantId + ":" + resultHash)
                    .getBytes(StandardCharsets.UTF_8)), tenantId, request.scenarioId(), request.productType(), "BLOCKED",
                    request.indexCode(), null, config.baseMarginBps(), totalMarginBps, cltv, null,
                    null, null, null, null, null, List.copyOf(adjustments), List.copyOf(blockers),
                    List.of(), List.of(config.versionRef()), resultHash, headers.correlationId());
        }

        BigDecimal marginRate = bpsToRate(totalMarginBps);
        BigDecimal fullyIndexedRate = rate(indexConfig.indexRate().add(marginRate));
        RateBoundaryResult boundary = applyRateBoundaries(config, fullyIndexedRate);
        BigDecimal monthlyRate = boundary.initialRate().divide(ONE_HUNDRED, 12, RoundingMode.HALF_UP)
                .divide(MONTHS_PER_YEAR, 12, RoundingMode.HALF_UP);
        BigDecimal drawPayment = request.initialDrawAmount().multiply(monthlyRate).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal repaymentPayment = amortizedPayment(request.initialDrawAmount(), monthlyRate, request.repaymentPeriodMonths());

        List<HomeEquityWaterfallLine> waterfall = new ArrayList<>();
        waterfall.add(new HomeEquityWaterfallLine("INDEX_RATE", BigDecimal.ZERO, indexConfig.indexRate(),
                indexConfig.versionRef() + ":" + indexConfig.sourceRef(), request.indexCode().name()));
        waterfall.add(new HomeEquityWaterfallLine("MARGIN", indexConfig.indexRate(), fullyIndexedRate,
                config.versionRef() + ":baseMarginBps=" + config.baseMarginBps(), "BASE_MARGIN_PLUS_ADJUSTMENTS"));
        for (HomeEquityAdjustmentResult adjustment : adjustments) {
            waterfall.add(new HomeEquityWaterfallLine("HELOC_ADJUSTMENT", null, bpsToRate(adjustment.amountBps()),
                    adjustment.versionRef() + ":" + adjustment.ruleId(), adjustment.reasonCode()));
        }
        waterfall.add(new HomeEquityWaterfallLine("RATE_BOUNDARY", fullyIndexedRate, boundary.initialRate(),
                config.versionRef() + ":rate-boundaries", boundary.reasonCode()));
        waterfall.add(new HomeEquityWaterfallLine("DRAW_PAYMENT", request.initialDrawAmount(), drawPayment,
                config.versionRef() + ":drawPeriodMonths=" + request.drawPeriodMonths(), "INTEREST_ONLY_DRAW_PERIOD"));
        waterfall.add(new HomeEquityWaterfallLine("REPAYMENT_PAYMENT", request.initialDrawAmount(), repaymentPayment,
                config.versionRef() + ":repaymentPeriodMonths=" + request.repaymentPeriodMonths(), "AMORTIZING_REPAYMENT_PERIOD"));

        List<String> versionRefs = new ArrayList<>();
        versionRefs.add(config.versionRef());
        versionRefs.add(indexConfig.versionRef());
        adjustments.stream().map(HomeEquityAdjustmentResult::versionRef).forEach(versionRefs::add);
        String resultHash = stableHash("home-equity-priced", tenantId, request.scenarioId(), request.productType(),
                request.indexCode(), indexConfig.indexRate(), config.baseMarginBps(), totalMarginBps, cltv,
                fullyIndexedRate, boundary, drawPayment, repaymentPayment, adjustments, versionRefs);

        return new HomeEquityPriceResponse(UUID.nameUUIDFromBytes((tenantId + ":" + resultHash)
                .getBytes(StandardCharsets.UTF_8)), tenantId, request.scenarioId(), request.productType(), "PRICED",
                request.indexCode(), indexConfig.indexRate(), config.baseMarginBps(), totalMarginBps, cltv,
                fullyIndexedRate, boundary.initialRate(), boundary.maximumAnnualAdjustmentRate(),
                boundary.maximumLifetimeRate(), drawPayment, repaymentPayment, List.copyOf(adjustments),
                List.of(), List.copyOf(waterfall), List.copyOf(versionRefs.stream().distinct().sorted().toList()),
                resultHash, headers.correlationId());
    }

    public PpeHomeEquityMappingResult validatePpeExport(Map<String, String> exportedFields) {
        Map<String, String> fields = exportedFields == null ? Map.of() : Map.copyOf(exportedFields);
        Map<String, String> canonical = new LinkedHashMap<>();
        mapIfPresent(fields, canonical, "productType", "homeEquity.productType");
        mapIfPresent(fields, canonical, "indexCode", "homeEquity.indexCode");
        mapIfPresent(fields, canonical, "marginBps", "homeEquity.baseMarginBps");
        mapIfPresent(fields, canonical, "drawPeriodMonths", "homeEquity.drawPeriodMonths");
        mapIfPresent(fields, canonical, "repaymentPeriodMonths", "homeEquity.repaymentPeriodMonths");
        mapIfPresent(fields, canonical, "lienPosition", "homeEquity.lienPosition");
        mapIfPresent(fields, canonical, "maxCltv", "homeEquity.maxCombinedLoanToValue");
        List<String> unmapped = fields.keySet().stream()
                .filter(key -> !List.of("productType", "indexCode", "marginBps", "drawPeriodMonths",
                        "repaymentPeriodMonths", "lienPosition", "maxCltv").contains(key))
                .sorted()
                .toList();
        return new PpeHomeEquityMappingResult(canonical, unmapped);
    }

    private static void mapIfPresent(Map<String, String> source, Map<String, String> target, String from, String to) {
        if (source.containsKey(from)) {
            target.put(to, source.get(from));
        }
    }

    private static List<HomeEquityAdjustmentResult> applyAdjustments(HomeEquityPricingConfiguration config,
            HomeEquityPriceRequest request, BigDecimal cltv) {
        return config.adjustments().stream()
                .filter(rule -> rule.condition().appliesTo(request, cltv))
                .sorted(Comparator.comparing(HomeEquityAdjustmentRule::precedence).thenComparing(HomeEquityAdjustmentRule::ruleId))
                .map(rule -> new HomeEquityAdjustmentResult(rule.ruleId(), rule.versionRef(), rule.amountBps(),
                        rule.reasonCode(), rule.condition().evidence(request, cltv)))
                .toList();
    }

    private static BigDecimal calculateCltv(HomeEquityPriceRequest request) {
        return request.existingLienBalance().add(request.creditLimit())
                .multiply(ONE_HUNDRED)
                .divide(request.propertyValue(), 5, RoundingMode.HALF_UP);
    }

    private static RateBoundaryResult applyRateBoundaries(HomeEquityPricingConfiguration config,
            BigDecimal fullyIndexedRate) {
        BigDecimal bounded = fullyIndexedRate;
        String reason = "WITHIN_CONFIGURED_RATE_BOUNDS";
        if (config.floorRate() != null && bounded.compareTo(config.floorRate()) < 0) {
            bounded = config.floorRate();
            reason = "FLOOR_RATE_APPLIED";
        }
        if (config.ceilingRate() != null && bounded.compareTo(config.ceilingRate()) > 0) {
            bounded = config.ceilingRate();
            reason = "CEILING_RATE_APPLIED";
        }
        BigDecimal annualMax = config.annualRateCap() == null ? null : bounded.add(config.annualRateCap());
        BigDecimal lifetimeMax = config.lifetimeRateCap() == null ? null : bounded.add(config.lifetimeRateCap());
        if (config.ceilingRate() != null) {
            annualMax = annualMax == null ? null : annualMax.min(config.ceilingRate());
            lifetimeMax = lifetimeMax == null ? null : lifetimeMax.min(config.ceilingRate());
        }
        return new RateBoundaryResult(rate(bounded), rateOrNull(annualMax), rateOrNull(lifetimeMax), reason);
    }

    private static BigDecimal amortizedPayment(BigDecimal principal, BigDecimal monthlyRate, int months) {
        if (monthlyRate.signum() == 0) {
            return principal.divide(BigDecimal.valueOf(months), MONEY_SCALE, RoundingMode.HALF_UP);
        }
        double rate = monthlyRate.doubleValue();
        double payment = principal.doubleValue() * rate / (1.0 - Math.pow(1.0 + rate, -months));
        return BigDecimal.valueOf(payment).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal bpsToRate(int bps) {
        return BigDecimal.valueOf(bps).divide(BigDecimal.valueOf(100), RATE_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal rate(BigDecimal value) {
        return value.setScale(RATE_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal rateOrNull(BigDecimal value) {
        return value == null ? null : rate(value);
    }

    private static String percent(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static void validateRequest(String tenantId, HomeEquityPriceRequest request) {
        if (request == null) {
            throw new HomeEquityPricingException("request is required");
        }
        requireText(request.scenarioId(), "scenario_id is required");
        if (!tenantId.equals(request.tenantId())) {
            throw new HomeEquityPricingException("request tenant does not match path tenant");
        }
        requireNonNull(request.productType(), "product_type is required");
        requireNonNull(request.indexCode(), "index_code is required");
        requireNonNull(request.lienPosition(), "lien_position is required");
        requirePositive(request.creditLimit(), "credit_limit must be positive");
        requirePositive(request.initialDrawAmount(), "initial_draw_amount must be positive");
        if (request.initialDrawAmount().compareTo(request.creditLimit()) > 0) {
            throw new HomeEquityPricingException("initial_draw_amount cannot exceed credit_limit");
        }
        requirePositive(request.propertyValue(), "property_value must be positive");
        requireNonNegative(request.existingLienBalance(), "existing_lien_balance cannot be negative");
        if (request.creditScore() < 300 || request.creditScore() > 850) {
            throw new HomeEquityPricingException("credit_score must be between 300 and 850");
        }
        requireText(request.propertyType(), "property_type is required");
        if (request.drawPeriodMonths() <= 0) {
            throw new HomeEquityPricingException("draw_period_months must be positive");
        }
        if (request.repaymentPeriodMonths() <= 0) {
            throw new HomeEquityPricingException("repayment_period_months must be positive");
        }
        requireNonNull(request.asOf(), "as_of is required");
        requireNonNull(request.configuration(), "configuration is required");
    }

    private static void requirePermission(HomeEquityPricingHeaders headers, String permission) {
        if (headers == null) {
            throw new HomeEquityPricingException("headers are required");
        }
        requireText(headers.actorId(), "actor_id is required");
        requireText(headers.correlationId(), "correlation_id is required");
        if (!headers.permissions().contains(permission)) {
            throw new HomeEquityPricingException(permission + " permission is required");
        }
    }

    private static void requireTenant(String tenantId) {
        requireText(tenantId, "tenant_id is required");
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new HomeEquityPricingException(message);
        }
    }

    private static void requireNonNull(Object value, String message) {
        if (value == null) {
            throw new HomeEquityPricingException(message);
        }
    }

    private static void requirePositive(BigDecimal value, String message) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new HomeEquityPricingException(message);
        }
    }

    private static void requireNonNegative(BigDecimal value, String message) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
            throw new HomeEquityPricingException(message);
        }
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

    public enum HomeEquityProductType { HELOC, CLOSED_END_HOME_EQUITY }

    public enum HomeEquityIndexCode { PRIME, SOFR, COFI }

    public enum LienPosition { FIRST, SECOND }

    public enum HomeEquityConditionType {
        CLTV_GREATER_THAN,
        CREDIT_SCORE_LESS_THAN,
        PROPERTY_TYPE_EQUALS,
        LIEN_POSITION_EQUALS,
        PRODUCT_TYPE_EQUALS
    }

    public record HomeEquityPricingHeaders(Set<String> permissions, String actorId, String correlationId) {
        public HomeEquityPricingHeaders {
            permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
        }
    }

    public record HomeEquityPriceRequest(String tenantId, String scenarioId, HomeEquityProductType productType,
            HomeEquityIndexCode indexCode, LienPosition lienPosition, BigDecimal creditLimit,
            BigDecimal initialDrawAmount, BigDecimal propertyValue, BigDecimal existingLienBalance, int creditScore,
            String propertyType, int drawPeriodMonths, int repaymentPeriodMonths, Instant asOf,
            HomeEquityPricingConfiguration configuration) {
    }

    public record HomeEquityPricingConfiguration(String versionRef, boolean productFamilyEnabled,
            int baseMarginBps, List<IndexRateConfig> indexRates, BigDecimal floorRate, BigDecimal ceilingRate,
            BigDecimal annualRateCap, BigDecimal lifetimeRateCap, BigDecimal maxCombinedLoanToValue,
            List<HomeEquityAdjustmentRule> adjustments) {
        public HomeEquityPricingConfiguration {
            requireText(versionRef, "home_equity version_ref is required");
            indexRates = indexRates == null ? List.of() : List.copyOf(indexRates);
            floorRate = rateOrNull(floorRate);
            ceilingRate = rateOrNull(ceilingRate);
            annualRateCap = rateOrNull(annualRateCap);
            lifetimeRateCap = rateOrNull(lifetimeRateCap);
            maxCombinedLoanToValue = maxCombinedLoanToValue == null ? null : maxCombinedLoanToValue.setScale(5, RoundingMode.HALF_UP);
            adjustments = adjustments == null ? List.of() : List.copyOf(adjustments);
            if (floorRate != null && ceilingRate != null && floorRate.compareTo(ceilingRate) > 0) {
                throw new HomeEquityPricingException("floor_rate cannot exceed ceiling_rate");
            }
        }
    }

    public record IndexRateConfig(HomeEquityIndexCode indexCode, BigDecimal indexRate, String sourceRef,
            String versionRef) {
        public IndexRateConfig {
            requireNonNull(indexCode, "index_code is required");
            indexRate = rate(indexRate);
            requireText(sourceRef, "index source_ref is required");
            requireText(versionRef, "index version_ref is required");
        }
    }

    public record HomeEquityAdjustmentRule(String ruleId, String versionRef, HomeEquityAdjustmentCondition condition,
            int amountBps, int precedence, String reasonCode) {
        public HomeEquityAdjustmentRule {
            requireText(ruleId, "home_equity adjustment rule_id is required");
            requireText(versionRef, "home_equity adjustment version_ref is required");
            requireNonNull(condition, "home_equity adjustment condition is required");
            requireText(reasonCode, "home_equity adjustment reason_code is required");
        }
    }

    public record HomeEquityAdjustmentCondition(HomeEquityConditionType type, BigDecimal decimalValue,
            Integer integerValue, String textValue) {
        public HomeEquityAdjustmentCondition {
            requireNonNull(type, "home_equity adjustment condition type is required");
        }

        boolean appliesTo(HomeEquityPriceRequest request, BigDecimal cltv) {
            return switch (type) {
                case CLTV_GREATER_THAN -> decimalValue != null && cltv.compareTo(decimalValue) > 0;
                case CREDIT_SCORE_LESS_THAN -> integerValue != null && request.creditScore() < integerValue;
                case PROPERTY_TYPE_EQUALS -> textValue != null && textValue.equalsIgnoreCase(request.propertyType());
                case LIEN_POSITION_EQUALS -> textValue != null && textValue.equalsIgnoreCase(request.lienPosition().name());
                case PRODUCT_TYPE_EQUALS -> textValue != null && textValue.equalsIgnoreCase(request.productType().name());
            };
        }

        String evidence(HomeEquityPriceRequest request, BigDecimal cltv) {
            return switch (type) {
                case CLTV_GREATER_THAN -> "cltv=" + percent(cltv) + ">" + percent(decimalValue);
                case CREDIT_SCORE_LESS_THAN -> "creditScore=" + request.creditScore() + "<" + integerValue;
                case PROPERTY_TYPE_EQUALS -> "propertyType=" + request.propertyType();
                case LIEN_POSITION_EQUALS -> "lienPosition=" + request.lienPosition();
                case PRODUCT_TYPE_EQUALS -> "productType=" + request.productType();
            };
        }
    }

    private record RateBoundaryResult(BigDecimal initialRate, BigDecimal maximumAnnualAdjustmentRate,
            BigDecimal maximumLifetimeRate, String reasonCode) {
    }

    public record HomeEquityPriceResponse(UUID priceId, String tenantId, String scenarioId,
            HomeEquityProductType productType, String status, HomeEquityIndexCode indexCode, BigDecimal indexRate,
            int baseMarginBps, int totalMarginBps, BigDecimal combinedLoanToValue, BigDecimal fullyIndexedRate,
            BigDecimal initialRate, BigDecimal maximumAnnualAdjustmentRate, BigDecimal maximumLifetimeRate,
            BigDecimal drawPeriodInterestOnlyPayment, BigDecimal repaymentPeriodAmortizedPayment,
            List<HomeEquityAdjustmentResult> adjustments, List<HomeEquityBlocker> blockers,
            List<HomeEquityWaterfallLine> waterfall, List<String> versionRefs, String resultHash,
            String correlationId) {
        public HomeEquityPriceResponse {
            combinedLoanToValue = combinedLoanToValue == null ? null : combinedLoanToValue.setScale(5, RoundingMode.HALF_UP);
            indexRate = rateOrNull(indexRate);
            fullyIndexedRate = rateOrNull(fullyIndexedRate);
            initialRate = rateOrNull(initialRate);
            maximumAnnualAdjustmentRate = rateOrNull(maximumAnnualAdjustmentRate);
            maximumLifetimeRate = rateOrNull(maximumLifetimeRate);
            drawPeriodInterestOnlyPayment = drawPeriodInterestOnlyPayment == null ? null : drawPeriodInterestOnlyPayment.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            repaymentPeriodAmortizedPayment = repaymentPeriodAmortizedPayment == null ? null : repaymentPeriodAmortizedPayment.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            adjustments = adjustments == null ? List.of() : List.copyOf(adjustments);
            blockers = blockers == null ? List.of() : List.copyOf(blockers);
            waterfall = waterfall == null ? List.of() : List.copyOf(waterfall);
            versionRefs = versionRefs == null ? List.of() : List.copyOf(versionRefs);
        }
    }

    public record HomeEquityAdjustmentResult(String ruleId, String versionRef, int amountBps,
            String reasonCode, String conditionEvidence) {
    }

    public record HomeEquityBlocker(String code, String message, String configRef, String remediationHint) {
    }

    public record HomeEquityWaterfallLine(String step, BigDecimal inputValue, BigDecimal outputValue,
            String configRef, String reasonCode) {
    }

    public record PpeHomeEquityMappingResult(Map<String, String> canonicalFields, List<String> unmappedFields) {
        public PpeHomeEquityMappingResult {
            canonicalFields = canonicalFields == null ? Map.of() : Map.copyOf(canonicalFields);
            unmappedFields = unmappedFields == null ? List.of() : List.copyOf(unmappedFields);
        }
    }

    public static class HomeEquityPricingException extends RuntimeException {
        public HomeEquityPricingException(String message) {
            super(message);
        }
    }
}
