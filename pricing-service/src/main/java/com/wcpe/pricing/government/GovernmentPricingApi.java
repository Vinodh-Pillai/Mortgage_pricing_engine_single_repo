package com.wcpe.pricing.government;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class GovernmentPricingApi {
    public static final String GOVERNMENT_PRICE_PERMISSION = "pricing.government.price";
    public static final String CONFIG_MISSING_BLOCKER = "GOVERNMENT_CONFIG_MISSING";

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal TWELVE = new BigDecimal("12");

    public GovernmentPriceResponse price(String tenantId, GovernmentPricingHeaders headers, GovernmentPriceRequest request) {
        requireTenant(tenantId);
        requirePermission(headers, GOVERNMENT_PRICE_PERMISSION);
        validateRequest(request);

        List<GovernmentEligibilityBlocker> blockers = new ArrayList<>();
        Optional<GovernmentProductConfiguration> matchingConfiguration = request.configurations().stream()
                .filter(configuration -> configuration.catalog().productType() == request.productType())
                .filter(GovernmentProductConfiguration::active)
                .sorted(Comparator.comparing(configuration -> configuration.catalog().displayOrder()))
                .findFirst();
        if (matchingConfiguration.isEmpty()) {
            blockers.add(new GovernmentEligibilityBlocker(CONFIG_MISSING_BLOCKER,
                    "Configured government fee, limit, and eligibility tables are required for " + request.productType(),
                    request.productType().name(), "active government product configuration"));
            return response(tenantId, headers, request, null, blockers);
        }

        GovernmentProductConfiguration configuration = matchingConfiguration.get();
        GovernmentPriceOption option = switch (request.productType()) {
            case FHA -> priceFha(request, configuration, blockers);
            case VA -> priceVa(request, configuration, blockers);
            case USDA -> priceUsda(request, configuration, blockers);
        };
        return response(tenantId, headers, request, blockers.isEmpty() ? option : null, blockers);
    }

    public GovernmentImportValidationResult validateImportedGovernmentRows(String sourceType, List<Map<String, String>> rows) {
        String normalizedSource = normalizeSourceType(sourceType);
        List<String> unsupported = new ArrayList<>();
        List<Map<String, String>> canonicalRows = new ArrayList<>();
        for (Map<String, String> row : rows == null ? List.<Map<String, String>>of() : rows) {
            Map<String, String> canonical = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : row.entrySet()) {
                String canonicalField = canonicalField(entry.getKey());
                if (canonicalField == null) {
                    unsupported.add(entry.getKey());
                } else {
                    canonical.put(canonicalField, entry.getValue());
                }
            }
            canonicalRows.add(Map.copyOf(canonical));
        }
        return new GovernmentImportValidationResult(normalizedSource, canonicalRows,
                unsupported.stream().distinct().sorted().toList(),
                stableHash("government-import-validation", normalizedSource, canonicalRows, unsupported));
    }

    private static GovernmentPriceOption priceFha(GovernmentPriceRequest request,
            GovernmentProductConfiguration configuration, List<GovernmentEligibilityBlocker> blockers) {
        if (configuration.fhaMip() == null) {
            blockers.add(missing("FHA_MIP_TABLE_MISSING", "FHA MIP table is required", configuration.catalog().productCode()));
            return null;
        }
        LoanLimit loanLimit = configuration.loanLimitsByCounty().get(normalizeKey(request.countyFips()));
        if (loanLimit == null) {
            blockers.add(missing(CONFIG_MISSING_BLOCKER, "FHA county loan limit is required", request.countyFips()));
            return null;
        }
        if (request.loanAmount().compareTo(loanLimit.limitAmount()) > 0) {
            blockers.add(new GovernmentEligibilityBlocker("FHA_LOAN_LIMIT_EXCEEDED",
                    "FHA loan amount exceeds configured county limit", request.loanAmount().toPlainString(),
                    loanLimit.limitAmount().toPlainString()));
        }
        FhaMipTable mip = configuration.fhaMip();
        List<GovernmentFeeLineItem> lineItems = List.of(
                feeLine("FHA_UPFRONT_MIP", request.loanAmount(), mip.upfrontRatePercent(), GovernmentFeeFrequency.UPFRONT,
                        mip.sourceRef(), mip.versionRef(), "countyFips=" + request.countyFips()),
                feeLine("FHA_ANNUAL_MIP", request.loanAmount(), mip.annualRatePercent(), GovernmentFeeFrequency.ANNUAL,
                        mip.sourceRef(), mip.versionRef(), "countyFips=" + request.countyFips()),
                feeLine("FHA_MONTHLY_MIP", request.loanAmount(), mip.annualRatePercent().divide(TWELVE, 10, RoundingMode.HALF_UP),
                        GovernmentFeeFrequency.MONTHLY, mip.sourceRef(), mip.versionRef(), "countyFips=" + request.countyFips()));
        return option(request, configuration, lineItems, loanLimit, null, null,
                List.of("loanLimitSource=" + loanLimit.sourceRef(), "loanLimitVersion=" + loanLimit.versionRef()));
    }

    private static GovernmentPriceOption priceVa(GovernmentPriceRequest request,
            GovernmentProductConfiguration configuration, List<GovernmentEligibilityBlocker> blockers) {
        VaFundingFeeRule feeRule = configuration.vaFundingFeeRules().stream()
                .filter(rule -> rule.matches(request))
                .sorted(Comparator.comparing(VaFundingFeeRule::precedence).thenComparing(VaFundingFeeRule::sourceRef))
                .findFirst()
                .orElse(null);
        if (feeRule == null) {
            blockers.add(missing(CONFIG_MISSING_BLOCKER, "VA funding-fee table row is required", configuration.catalog().productCode()));
            return null;
        }
        LoanLimit loanLimit = configuration.loanLimitsByCounty().get(normalizeKey(request.countyFips()));
        if (loanLimit == null) {
            blockers.add(missing(CONFIG_MISSING_BLOCKER, "VA county loan limit is required", request.countyFips()));
            return null;
        }
        VaEntitlementConfig entitlement = configuration.vaEntitlement();
        if (entitlement == null) {
            blockers.add(missing(CONFIG_MISSING_BLOCKER, "VA entitlement configuration is required", configuration.catalog().productCode()));
            return null;
        }
        BigDecimal availableEntitlement = entitlement.baseEntitlementAmount().subtract(request.vaEntitlementUsed())
                .max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        if (request.loanAmount().compareTo(loanLimit.limitAmount()) > 0) {
            blockers.add(new GovernmentEligibilityBlocker("VA_LOAN_LIMIT_EXCEEDED",
                    "VA loan amount exceeds configured county limit", request.loanAmount().toPlainString(),
                    loanLimit.limitAmount().toPlainString()));
        }
        List<GovernmentFeeLineItem> lineItems = List.of(feeLine("VA_FUNDING_FEE", request.loanAmount(),
                request.vaFundingFeeExempt() ? BigDecimal.ZERO : feeRule.feeRatePercent(), GovernmentFeeFrequency.UPFRONT,
                feeRule.sourceRef(), feeRule.versionRef(), "firstUse=" + request.vaFirstUse()));
        return option(request, configuration, lineItems, loanLimit, availableEntitlement, null,
                List.of("entitlementSource=" + entitlement.sourceRef(), "entitlementVersion=" + entitlement.versionRef()));
    }

    private static GovernmentPriceOption priceUsda(GovernmentPriceRequest request,
            GovernmentProductConfiguration configuration, List<GovernmentEligibilityBlocker> blockers) {
        if (request.householdIncome() == null || request.propertyEligibilityRef() == null
                || request.propertyEligibilityRef().isBlank()) {
            blockers.add(new GovernmentEligibilityBlocker("GOVERNMENT_FACT_MISSING",
                    "USDA household income and property eligibility facts are required", "missing supplied facts",
                    "householdIncome and propertyEligibilityRef"));
            return null;
        }
        if (configuration.usdaGuaranteeFee() == null) {
            blockers.add(missing(CONFIG_MISSING_BLOCKER, "USDA guarantee fee table is required", configuration.catalog().productCode()));
            return null;
        }
        UsdaIncomeLimit incomeLimit = configuration.usdaIncomeLimits().get(normalizeKey(request.countyFips()));
        if (incomeLimit == null) {
            incomeLimit = configuration.usdaIncomeLimits().get(normalizeKey(request.state()));
        }
        if (incomeLimit == null) {
            blockers.add(missing(CONFIG_MISSING_BLOCKER, "USDA income limit table is required", request.countyFips()));
            return null;
        }
        if (request.householdIncome().compareTo(incomeLimit.limitAmount()) > 0) {
            blockers.add(new GovernmentEligibilityBlocker("USDA_INCOME_LIMIT_EXCEEDED",
                    "USDA household income exceeds configured geographic limit",
                    request.householdIncome().toPlainString(), incomeLimit.limitAmount().toPlainString()));
        }
        if (!configuration.usdaEligiblePropertyRefs().contains(normalizeKey(request.propertyEligibilityRef()))) {
            blockers.add(new GovernmentEligibilityBlocker("USDA_PROPERTY_INELIGIBLE",
                    "USDA property eligibility must match configured geographic/property table",
                    request.propertyEligibilityRef(), "configured eligible property ref"));
        }
        UsdaGuaranteeFeeTable fee = configuration.usdaGuaranteeFee();
        List<GovernmentFeeLineItem> lineItems = List.of(
                feeLine("USDA_UPFRONT_GUARANTEE_FEE", request.loanAmount(), fee.upfrontRatePercent(), GovernmentFeeFrequency.UPFRONT,
                        fee.sourceRef(), fee.versionRef(), "incomeLimitSource=" + incomeLimit.sourceRef()),
                feeLine("USDA_ANNUAL_GUARANTEE_FEE", request.loanAmount(), fee.annualRatePercent(), GovernmentFeeFrequency.ANNUAL,
                        fee.sourceRef(), fee.versionRef(), "incomeLimitSource=" + incomeLimit.sourceRef()));
        return option(request, configuration, lineItems, null, null, incomeLimit,
                List.of("propertyEligibilityRef=" + request.propertyEligibilityRef(), "incomeLimitVersion=" + incomeLimit.versionRef()));
    }

    private static GovernmentPriceResponse response(String tenantId, GovernmentPricingHeaders headers,
            GovernmentPriceRequest request, GovernmentPriceOption option, List<GovernmentEligibilityBlocker> blockers) {
        String replayHash = stableHash("government-price", tenantId, request, option, blockers);
        GovernmentPriceOption selected = option == null ? null : option.withReplayHash(replayHash);
        return new GovernmentPriceResponse(tenantId, request.productType(), selected,
                selected == null ? List.of() : List.of(selected), List.copyOf(blockers), replayHash, headers.correlationId());
    }

    private static GovernmentPriceOption option(GovernmentPriceRequest request, GovernmentProductConfiguration configuration,
            List<GovernmentFeeLineItem> lineItems, LoanLimit loanLimit, BigDecimal availableEntitlement,
            UsdaIncomeLimit incomeLimit, List<String> evidence) {
        List<String> conditionEvidence = new ArrayList<>();
        conditionEvidence.add("productType=" + request.productType());
        conditionEvidence.add("countyFips=" + request.countyFips());
        conditionEvidence.addAll(evidence == null ? List.of() : evidence);
        return new GovernmentPriceOption(configuration.catalog(), List.copyOf(lineItems), loanLimit,
                availableEntitlement, incomeLimit, configuration.versionRef(), "", List.copyOf(conditionEvidence));
    }

    private static GovernmentFeeLineItem feeLine(String feeType, BigDecimal basis, BigDecimal ratePercent,
            GovernmentFeeFrequency frequency, String sourceRef, String versionRef, String evidence) {
        BigDecimal amount = basis.multiply(ratePercent).divide(ONE_HUNDRED, 10, RoundingMode.HALF_UP)
                .setScale(2, RoundingMode.HALF_UP);
        return new GovernmentFeeLineItem(feeType, amount, ratePercent.setScale(8, RoundingMode.HALF_UP),
                frequency, sourceRef, versionRef, stableHash("government-fee-line", feeType, basis, ratePercent,
                frequency, sourceRef, versionRef, evidence), List.of(evidence));
    }

    private static GovernmentEligibilityBlocker missing(String code, String message, String actualValue) {
        return new GovernmentEligibilityBlocker(code, message, actualValue, "configured source row");
    }

    private static void validateRequest(GovernmentPriceRequest request) {
        if (request == null) {
            throw new GovernmentPricingException("government pricing request is required");
        }
        requireNonNull(request.productType(), "product_type is required");
        requirePositive(request.loanAmount(), "loan_amount must be positive");
        requireText(request.countyFips(), "county_fips is required");
        requireText(request.state(), "state is required");
    }

    private static void requirePermission(GovernmentPricingHeaders headers, String permission) {
        if (headers == null) {
            throw new GovernmentPricingException("headers are required");
        }
        requireText(headers.actorId(), "actor_id is required");
        requireText(headers.correlationId(), "correlation_id is required");
        if (!headers.permissions().contains(permission)) {
            throw new GovernmentPricingException(permission + " permission is required");
        }
    }

    private static String normalizeSourceType(String sourceType) {
        String normalized = sourceType == null ? "UNKNOWN" : sourceType.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        return switch (normalized) {
            case "OPTIMAL_BLUE", "POLLY", "LOANPASS" -> normalized;
            default -> "UNSUPPORTED:" + normalized;
        };
    }

    private static String canonicalField(String fieldName) {
        if (fieldName == null) {
            return null;
        }
        String normalized = fieldName.trim().toLowerCase(Locale.ROOT).replace("%", "percent").replace(' ', '_').replace('-', '_');
        return switch (normalized) {
            case "program", "agency", "product_type" -> "product_type";
            case "product_code" -> "product_code";
            case "investor", "investor_code" -> "investor_code";
            case "county_fips", "county" -> "county_fips";
            case "state" -> "state";
            case "loan_limit", "limit_amount" -> "limit_amount";
            case "upfront_mip_percent", "upfront_fee_percent", "guarantee_fee_upfront_percent" -> "upfront_rate_percent";
            case "annual_mip_percent", "annual_fee_percent", "guarantee_fee_annual_percent" -> "annual_rate_percent";
            case "funding_fee_percent", "va_funding_fee_percent" -> "funding_fee_rate_percent";
            case "income_limit", "household_income_limit" -> "income_limit";
            case "property_eligibility_ref", "geography_eligibility_ref" -> "property_eligibility_ref";
            case "source_ref", "row_id", "source_row_id" -> "source_ref";
            case "version_ref", "version" -> "version_ref";
            default -> null;
        };
    }

    private static void requireTenant(String tenantId) {
        requireText(tenantId, "tenant_id is required");
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new GovernmentPricingException(message);
        }
    }

    private static void requireNonNull(Object value, String message) {
        if (value == null) {
            throw new GovernmentPricingException(message);
        }
    }

    private static void requirePositive(BigDecimal value, String message) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new GovernmentPricingException(message);
        }
    }

    private static String normalizeKey(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
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

    public enum GovernmentProductType {
        FHA,
        VA,
        USDA
    }

    public enum GovernmentFeeFrequency {
        UPFRONT,
        ANNUAL,
        MONTHLY
    }

    public record GovernmentPricingHeaders(Set<String> permissions, String actorId, String correlationId) {
        public GovernmentPricingHeaders {
            permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
        }
    }

    public record GovernmentPriceRequest(GovernmentProductType productType, BigDecimal loanAmount, String countyFips,
            String state, BigDecimal householdIncome, String propertyEligibilityRef, boolean vaFundingFeeExempt,
            boolean vaFirstUse, BigDecimal downPaymentPercent, BigDecimal vaEntitlementUsed,
            List<GovernmentProductConfiguration> configurations) {
        public GovernmentPriceRequest {
            loanAmount = loanAmount == null ? null : loanAmount.setScale(2, RoundingMode.HALF_UP);
            householdIncome = householdIncome == null ? null : householdIncome.setScale(2, RoundingMode.HALF_UP);
            downPaymentPercent = downPaymentPercent == null ? BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP)
                    : downPaymentPercent.setScale(4, RoundingMode.HALF_UP);
            vaEntitlementUsed = vaEntitlementUsed == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                    : vaEntitlementUsed.setScale(2, RoundingMode.HALF_UP);
            configurations = configurations == null ? List.of() : List.copyOf(configurations);
        }
    }

    public record GovernmentProductConfiguration(GovernmentProductCatalog catalog, String versionRef,
            boolean active, Map<String, LoanLimit> loanLimitsByCounty, FhaMipTable fhaMip,
            List<VaFundingFeeRule> vaFundingFeeRules, VaEntitlementConfig vaEntitlement,
            UsdaGuaranteeFeeTable usdaGuaranteeFee, Map<String, UsdaIncomeLimit> usdaIncomeLimits,
            Set<String> usdaEligiblePropertyRefs) {
        public GovernmentProductConfiguration {
            catalog = Objects.requireNonNull(catalog, "government product catalog is required");
            requireText(versionRef, "government product version_ref is required");
            loanLimitsByCounty = normalizedMap(loanLimitsByCounty);
            vaFundingFeeRules = vaFundingFeeRules == null ? List.of() : List.copyOf(vaFundingFeeRules);
            usdaIncomeLimits = normalizedMap(usdaIncomeLimits);
            usdaEligiblePropertyRefs = usdaEligiblePropertyRefs == null ? Set.of()
                    : Set.copyOf(usdaEligiblePropertyRefs.stream().map(GovernmentPricingApi::normalizeKey).toList());
        }

        private static <T> Map<String, T> normalizedMap(Map<String, T> input) {
            if (input == null || input.isEmpty()) {
                return Map.of();
            }
            Map<String, T> normalized = new LinkedHashMap<>();
            input.forEach((key, value) -> normalized.put(normalizeKey(key), value));
            return Map.copyOf(normalized);
        }
    }

    public record GovernmentProductCatalog(GovernmentProductType productType, String productCode, String investorCode,
            String channelCode, int displayOrder, String sourceRef) {
        public GovernmentProductCatalog {
            requireNonNull(productType, "government product type is required");
            requireText(productCode, "government product code is required");
            requireText(investorCode, "government investor code is required");
            requireText(channelCode, "government channel code is required");
            requireText(sourceRef, "government product source_ref is required");
        }
    }

    public record FhaMipTable(BigDecimal upfrontRatePercent, BigDecimal annualRatePercent, String sourceRef, String versionRef) {
        public FhaMipTable {
            requirePositive(upfrontRatePercent, "fha upfront_rate_percent must be positive");
            requirePositive(annualRatePercent, "fha annual_rate_percent must be positive");
            upfrontRatePercent = upfrontRatePercent.setScale(8, RoundingMode.HALF_UP);
            annualRatePercent = annualRatePercent.setScale(8, RoundingMode.HALF_UP);
            requireText(sourceRef, "fha mip source_ref is required");
            requireText(versionRef, "fha mip version_ref is required");
        }
    }

    public record LoanLimit(BigDecimal limitAmount, String sourceRef, String versionRef) {
        public LoanLimit {
            requirePositive(limitAmount, "loan_limit amount must be positive");
            limitAmount = limitAmount.setScale(2, RoundingMode.HALF_UP);
            requireText(sourceRef, "loan_limit source_ref is required");
            requireText(versionRef, "loan_limit version_ref is required");
        }
    }

    public record VaFundingFeeRule(boolean exemptOnly, Boolean firstUse, BigDecimal minDownPaymentPercent,
            BigDecimal maxDownPaymentPercent, BigDecimal feeRatePercent, int precedence, String sourceRef, String versionRef) {
        public VaFundingFeeRule {
            minDownPaymentPercent = minDownPaymentPercent == null ? BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP)
                    : minDownPaymentPercent.setScale(4, RoundingMode.HALF_UP);
            maxDownPaymentPercent = maxDownPaymentPercent == null ? null : maxDownPaymentPercent.setScale(4, RoundingMode.HALF_UP);
            feeRatePercent = feeRatePercent == null ? BigDecimal.ZERO.setScale(8, RoundingMode.HALF_UP)
                    : feeRatePercent.setScale(8, RoundingMode.HALF_UP);
            requireText(sourceRef, "va funding fee source_ref is required");
            requireText(versionRef, "va funding fee version_ref is required");
        }

        boolean matches(GovernmentPriceRequest request) {
            if (exemptOnly != request.vaFundingFeeExempt()) {
                return false;
            }
            if (firstUse != null && firstUse != request.vaFirstUse()) {
                return false;
            }
            if (request.downPaymentPercent().compareTo(minDownPaymentPercent) < 0) {
                return false;
            }
            return maxDownPaymentPercent == null || request.downPaymentPercent().compareTo(maxDownPaymentPercent) <= 0;
        }
    }

    public record VaEntitlementConfig(BigDecimal baseEntitlementAmount, String sourceRef, String versionRef) {
        public VaEntitlementConfig {
            requirePositive(baseEntitlementAmount, "va base_entitlement_amount must be positive");
            baseEntitlementAmount = baseEntitlementAmount.setScale(2, RoundingMode.HALF_UP);
            requireText(sourceRef, "va entitlement source_ref is required");
            requireText(versionRef, "va entitlement version_ref is required");
        }
    }

    public record UsdaGuaranteeFeeTable(BigDecimal upfrontRatePercent, BigDecimal annualRatePercent, String sourceRef, String versionRef) {
        public UsdaGuaranteeFeeTable {
            requirePositive(upfrontRatePercent, "usda upfront_rate_percent must be positive");
            requirePositive(annualRatePercent, "usda annual_rate_percent must be positive");
            upfrontRatePercent = upfrontRatePercent.setScale(8, RoundingMode.HALF_UP);
            annualRatePercent = annualRatePercent.setScale(8, RoundingMode.HALF_UP);
            requireText(sourceRef, "usda guarantee fee source_ref is required");
            requireText(versionRef, "usda guarantee fee version_ref is required");
        }
    }

    public record UsdaIncomeLimit(BigDecimal limitAmount, String sourceRef, String versionRef) {
        public UsdaIncomeLimit {
            requirePositive(limitAmount, "usda income_limit amount must be positive");
            limitAmount = limitAmount.setScale(2, RoundingMode.HALF_UP);
            requireText(sourceRef, "usda income_limit source_ref is required");
            requireText(versionRef, "usda income_limit version_ref is required");
        }
    }

    public record GovernmentFeeLineItem(String feeType, BigDecimal amount, BigDecimal ratePercent,
            GovernmentFeeFrequency frequency, String sourceRef, String versionRef, String replayHash,
            List<String> conditionEvidence) {
        public GovernmentFeeLineItem {
            amount = amount == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : amount.setScale(2, RoundingMode.HALF_UP);
            ratePercent = ratePercent == null ? BigDecimal.ZERO.setScale(8, RoundingMode.HALF_UP) : ratePercent.setScale(8, RoundingMode.HALF_UP);
            requireNonNull(frequency, "government fee frequency is required");
            requireText(feeType, "government fee_type is required");
            requireText(sourceRef, "government fee source_ref is required");
            requireText(versionRef, "government fee version_ref is required");
            conditionEvidence = conditionEvidence == null ? List.of() : List.copyOf(conditionEvidence);
        }
    }

    public record GovernmentPriceOption(GovernmentProductCatalog catalog, List<GovernmentFeeLineItem> lineItems,
            LoanLimit loanLimit, BigDecimal availableEntitlement, UsdaIncomeLimit incomeLimit,
            String versionRef, String replayHash, List<String> conditionEvidence) {
        public GovernmentPriceOption {
            catalog = Objects.requireNonNull(catalog, "government product catalog is required");
            lineItems = lineItems == null ? List.of() : List.copyOf(lineItems);
            availableEntitlement = availableEntitlement == null ? null : availableEntitlement.setScale(2, RoundingMode.HALF_UP);
            requireText(versionRef, "government option version_ref is required");
            replayHash = replayHash == null ? "" : replayHash;
            conditionEvidence = conditionEvidence == null ? List.of() : List.copyOf(conditionEvidence);
        }

        GovernmentPriceOption withReplayHash(String newReplayHash) {
            return new GovernmentPriceOption(catalog, lineItems, loanLimit, availableEntitlement, incomeLimit,
                    versionRef, newReplayHash, conditionEvidence);
        }
    }

    public record GovernmentPriceResponse(String tenantId, GovernmentProductType productType,
            GovernmentPriceOption selectedOption, List<GovernmentPriceOption> rankedOptions,
            List<GovernmentEligibilityBlocker> blockers, String replayHash, String correlationId) {
        public GovernmentPriceResponse {
            rankedOptions = rankedOptions == null ? List.of() : List.copyOf(rankedOptions);
            blockers = blockers == null ? List.of() : List.copyOf(blockers);
        }
    }

    public record GovernmentEligibilityBlocker(String code, String message, String actualValue, String requiredValue) {}

    public record GovernmentImportValidationResult(String sourceType, List<Map<String, String>> canonicalRows,
            List<String> unsupportedFields, String replayHash) {
        public GovernmentImportValidationResult {
            canonicalRows = canonicalRows == null ? List.of() : List.copyOf(canonicalRows);
            unsupportedFields = unsupportedFields == null ? List.of() : List.copyOf(unsupportedFields);
        }
    }

    public static class GovernmentPricingException extends RuntimeException {
        public GovernmentPricingException(String message) {
            super(Objects.requireNonNull(message));
        }
    }
}
