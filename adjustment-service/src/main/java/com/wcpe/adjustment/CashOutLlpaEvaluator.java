package com.wcpe.adjustment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Configurable cash-out refinance LLPA evaluator. It consumes an explicit
 * upstream classification and never infers cash-out status from free text.
 */
public final class CashOutLlpaEvaluator {
    public static final String CATEGORY = "LLPA_CASH_OUT";
    public static final int DEFAULT_WATERFALL_SEQUENCE = 40;

    public CashOutAdjustmentResult evaluate(EvaluationRequest request, List<CashOutLlpaRule> rules) {
        Objects.requireNonNull(request, "request is required");
        List<CashOutLlpaRule> configuredRules = List.copyOf(rules == null ? List.of() : rules);
        if (request.classification == null) {
            return request.failClosed("ADJ_CASH_OUT_CLASSIFICATION_MISSING");
        }
        if (request.classification.ambiguous) {
            return request.conflict("ADJ_CASH_OUT_AMBIGUOUS");
        }
        if (!request.classification.cashOut) {
            return request.nonApplicable("scenario is not classified as cash-out");
        }
        validateNoOverlaps(configuredRules);

        List<CashOutLlpaRule> matches = configuredRules.stream()
            .filter(CashOutLlpaRule::enabled)
            .filter(rule -> rule.tenantId.equals(request.tenantId))
            .filter(rule -> rule.productId.equals(request.productId))
            .filter(rule -> rule.investorId.equals(request.investorId))
            .filter(rule -> rule.channel.equals(request.channel))
            .filter(rule -> request.ruleBookVersion == null || rule.ruleBookVersion.equals(request.ruleBookVersion))
            .filter(rule -> rule.effectiveOn(request.quoteDate))
            .filter(rule -> rule.classificationCode.equals(request.classification.code))
            .filter(rule -> rule.containsLtv(request.ltvValue(rule.ltvMetric)))
            .filter(rule -> rule.containsLoanAmount(request.newLoanAmount))
            .filter(rule -> optionalMatch(rule.occupancyCode, request.occupancyCode))
            .filter(rule -> optionalMatch(rule.propertyTypeCode, request.propertyTypeCode))
            .filter(rule -> optionalMatch(rule.stateCode, request.stateCode))
            .sorted(Comparator.comparingInt(CashOutLlpaRule::priority))
            .toList();

        if (matches.isEmpty()) {
            return request.failClosed("ADJ_CASH_OUT_RULE_NOT_FOUND");
        }
        CashOutLlpaRule selected = matches.get(0);
        if (matches.size() > 1 && matches.get(1).priority == selected.priority) {
            return request.conflict("ADJ_CASH_OUT_AMBIGUOUS");
        }
        return request.applied(selected);
    }

    public static void validateNoOverlaps(List<CashOutLlpaRule> rules) {
        List<CashOutLlpaRule> configuredRules = List.copyOf(rules == null ? List.of() : rules);
        for (int i = 0; i < configuredRules.size(); i++) {
            CashOutLlpaRule left = configuredRules.get(i);
            for (int j = i + 1; j < configuredRules.size(); j++) {
                CashOutLlpaRule right = configuredRules.get(j);
                if (left.sameSelectorAs(right) && left.ltvOverlaps(right) && left.loanAmountOverlaps(right)
                    && left.effectiveWindowOverlaps(right)) {
                    throw new IllegalArgumentException("overlapping cash-out LLPA rules for tenant/rule book/selector");
                }
            }
        }
    }

    public enum LtvMetric {
        LTV,
        CLTV,
        HCLTV
    }

    public enum BoundaryPolicy {
        MIN_INCLUSIVE_MAX_INCLUSIVE,
        MIN_EXCLUSIVE_MAX_INCLUSIVE
    }

    public enum EvaluationStatus {
        APPLIED,
        NON_APPLICABLE,
        FAIL_CLOSED,
        CONFLICT
    }

    public record CashOutClassification(
        String code,
        String sourceSystem,
        Instant sourceTimestamp,
        boolean cashOut,
        boolean ambiguous
    ) {
        public CashOutClassification {
            requireText(code, "classification code is required");
            requireText(sourceSystem, "classification source system is required");
            Objects.requireNonNull(sourceTimestamp, "classification source timestamp is required");
        }
    }

    public record EvaluationRequest(
        UUID tenantId,
        String quoteId,
        String scenarioId,
        String productId,
        String investorId,
        String channel,
        Instant quoteDate,
        BigDecimal newLoanAmount,
        BigDecimal priceBeforeCashOut,
        CashOutClassification classification,
        String refinanceType,
        BigDecimal cashOutAmount,
        BigDecimal payoffAmount,
        BigDecimal ltv,
        BigDecimal cltv,
        BigDecimal hcltv,
        String occupancyCode,
        String propertyTypeCode,
        String stateCode,
        String ruleBookVersion
    ) {
        public EvaluationRequest {
            Objects.requireNonNull(tenantId, "tenantId is required");
            requireText(quoteId, "quoteId is required");
            requireText(scenarioId, "scenarioId is required");
            requireText(productId, "productId is required");
            requireText(investorId, "investorId is required");
            requireText(channel, "channel is required");
            Objects.requireNonNull(quoteDate, "quoteDate is required");
            Objects.requireNonNull(newLoanAmount, "newLoanAmount is required");
            Objects.requireNonNull(priceBeforeCashOut, "priceBeforeCashOut is required");
        }

        BigDecimal ltvValue(LtvMetric metric) {
            return switch (metric) {
                case LTV -> ltv;
                case CLTV -> cltv;
                case HCLTV -> hcltv;
            };
        }

        CashOutAdjustmentResult failClosed(String message) {
            return result(EvaluationStatus.FAIL_CLOSED, null, message);
        }

        CashOutAdjustmentResult conflict(String message) {
            return result(EvaluationStatus.CONFLICT, null, message);
        }

        CashOutAdjustmentResult nonApplicable(String message) {
            return result(EvaluationStatus.NON_APPLICABLE, null, message);
        }

        CashOutAdjustmentResult applied(CashOutLlpaRule rule) {
            BigDecimal pointsDelta = rule.pointsDelta.setScale(6, RoundingMode.HALF_UP);
            BigDecimal bpsDelta = pointsDelta.multiply(new BigDecimal("100")).setScale(4, RoundingMode.HALF_UP);
            BigDecimal moneyImpact = newLoanAmount.multiply(pointsDelta)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            return new CashOutAdjustmentResult(
                deterministicId(tenantId + quoteId + rule.ruleId + rule.contentHash),
                CATEGORY,
                EvaluationStatus.APPLIED,
                rule.ruleId,
                rule.ruleBookId,
                rule.ruleBookVersion,
                inputSnapshotHash(),
                pointsDelta,
                bpsDelta,
                moneyImpact,
                priceBeforeCashOut.add(pointsDelta).setScale(6, RoundingMode.HALF_UP),
                rule.reasonCode,
                List.of(),
                classification.code,
                classification.sourceSystem,
                rule.ltvBandKey,
                rule.loanAmountBandKey,
                rule.contentHash,
                "HALF_UP"
            );
        }

        private CashOutAdjustmentResult result(EvaluationStatus status, CashOutLlpaRule rule, String message) {
            String classificationCode = classification == null ? null : classification.code;
            String classificationSource = classification == null ? null : classification.sourceSystem;
            return new CashOutAdjustmentResult(
                deterministicId(tenantId + quoteId + scenarioId + message),
                CATEGORY,
                status,
                rule == null ? null : rule.ruleId,
                rule == null ? null : rule.ruleBookId,
                rule == null ? null : rule.ruleBookVersion,
                inputSnapshotHash(),
                BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP),
                null,
                priceBeforeCashOut.setScale(6, RoundingMode.HALF_UP),
                null,
                List.of(message),
                classificationCode,
                classificationSource,
                null,
                null,
                null,
                "HALF_UP"
            );
        }

        private String inputSnapshotHash() {
            return hashOf(tenantId, quoteId, scenarioId, productId, investorId, channel, quoteDate,
                newLoanAmount, classification, refinanceType, cashOutAmount, payoffAmount, ltv, cltv, hcltv,
                occupancyCode, propertyTypeCode, stateCode, ruleBookVersion);
        }
    }

    public record CashOutLlpaRule(
        UUID tenantId,
        UUID ruleBookId,
        UUID ruleId,
        String ruleBookVersion,
        String productId,
        String investorId,
        String channel,
        String classificationCode,
        LtvMetric ltvMetric,
        String ltvBandKey,
        BigDecimal ltvMin,
        BigDecimal ltvMax,
        BoundaryPolicy boundaryPolicy,
        String loanAmountBandKey,
        BigDecimal loanAmountMin,
        BigDecimal loanAmountMax,
        String occupancyCode,
        String propertyTypeCode,
        String stateCode,
        BigDecimal pointsDelta,
        String reasonCode,
        int priority,
        Instant effectiveStart,
        Instant effectiveEnd,
        String contentHash,
        boolean enabled
    ) {
        public CashOutLlpaRule {
            Objects.requireNonNull(tenantId, "tenantId is required");
            Objects.requireNonNull(ruleBookId, "ruleBookId is required");
            Objects.requireNonNull(ruleId, "ruleId is required");
            requireText(ruleBookVersion, "ruleBookVersion is required");
            requireText(productId, "productId is required");
            requireText(investorId, "investorId is required");
            requireText(channel, "channel is required");
            requireText(classificationCode, "classificationCode is required");
            Objects.requireNonNull(ltvMetric, "ltvMetric is required");
            requireText(ltvBandKey, "ltvBandKey is required");
            Objects.requireNonNull(ltvMin, "ltvMin is required");
            Objects.requireNonNull(ltvMax, "ltvMax is required");
            boundaryPolicy = boundaryPolicy == null ? BoundaryPolicy.MIN_INCLUSIVE_MAX_INCLUSIVE : boundaryPolicy;
            requireText(loanAmountBandKey, "loanAmountBandKey is required");
            Objects.requireNonNull(loanAmountMin, "loanAmountMin is required");
            Objects.requireNonNull(loanAmountMax, "loanAmountMax is required");
            Objects.requireNonNull(pointsDelta, "pointsDelta is required");
            requireText(reasonCode, "reasonCode is required");
            Objects.requireNonNull(effectiveStart, "effectiveStart is required");
            if (ltvMin.compareTo(ltvMax) > 0) {
                throw new IllegalArgumentException("ltvMin cannot exceed ltvMax");
            }
            if (loanAmountMin.compareTo(loanAmountMax) > 0) {
                throw new IllegalArgumentException("loanAmountMin cannot exceed loanAmountMax");
            }
            if (priority < 0) {
                throw new IllegalArgumentException("priority must be non-negative");
            }
            contentHash = contentHash == null || contentHash.isBlank()
                ? hashOf(tenantId, ruleBookId, ruleId, ruleBookVersion, productId, investorId, channel,
                    classificationCode, ltvMetric, ltvBandKey, ltvMin, ltvMax, loanAmountBandKey,
                    loanAmountMin, loanAmountMax, occupancyCode, propertyTypeCode, stateCode, pointsDelta, reasonCode)
                : contentHash;
        }

        boolean effectiveOn(Instant quoteDate) {
            return !quoteDate.isBefore(effectiveStart) && (effectiveEnd == null || quoteDate.isBefore(effectiveEnd));
        }

        boolean containsLtv(BigDecimal ltvValue) {
            return containsRange(ltvValue, ltvMin, ltvMax, boundaryPolicy);
        }

        boolean containsLoanAmount(BigDecimal loanAmount) {
            return containsRange(loanAmount, loanAmountMin, loanAmountMax, BoundaryPolicy.MIN_INCLUSIVE_MAX_INCLUSIVE);
        }

        boolean sameSelectorAs(CashOutLlpaRule other) {
            return tenantId.equals(other.tenantId)
                && ruleBookId.equals(other.ruleBookId)
                && productId.equals(other.productId)
                && investorId.equals(other.investorId)
                && channel.equals(other.channel)
                && classificationCode.equals(other.classificationCode)
                && ltvMetric == other.ltvMetric
                && optionalOverlaps(occupancyCode, other.occupancyCode)
                && optionalOverlaps(propertyTypeCode, other.propertyTypeCode)
                && optionalOverlaps(stateCode, other.stateCode);
        }

        boolean ltvOverlaps(CashOutLlpaRule other) {
            return ltvMin.compareTo(other.ltvMax) <= 0 && other.ltvMin.compareTo(ltvMax) <= 0;
        }

        boolean loanAmountOverlaps(CashOutLlpaRule other) {
            return loanAmountMin.compareTo(other.loanAmountMax) <= 0 && other.loanAmountMin.compareTo(loanAmountMax) <= 0;
        }

        boolean effectiveWindowOverlaps(CashOutLlpaRule other) {
            Instant leftEnd = effectiveEnd == null ? Instant.MAX : effectiveEnd;
            Instant rightEnd = other.effectiveEnd == null ? Instant.MAX : other.effectiveEnd;
            return effectiveStart.isBefore(rightEnd) && other.effectiveStart.isBefore(leftEnd);
        }
    }

    public record CashOutAdjustmentResult(
        UUID adjustmentId,
        String category,
        EvaluationStatus status,
        UUID ruleId,
        UUID ruleBookId,
        String ruleBookVersion,
        String inputSnapshotHash,
        BigDecimal pointsDelta,
        BigDecimal bpsDelta,
        BigDecimal moneyImpact,
        BigDecimal priceAfterCashOut,
        String reasonCode,
        List<String> validationMessages,
        String classificationCode,
        String classificationSource,
        String selectedLtvBandKey,
        String selectedLoanAmountBandKey,
        String ruleContentHash,
        String roundingMode
    ) {
        public CashOutAdjustmentResult {
            Objects.requireNonNull(adjustmentId, "adjustmentId is required");
            requireText(category, "category is required");
            Objects.requireNonNull(status, "status is required");
            requireText(inputSnapshotHash, "inputSnapshotHash is required");
            Objects.requireNonNull(pointsDelta, "pointsDelta is required");
            Objects.requireNonNull(bpsDelta, "bpsDelta is required");
            Objects.requireNonNull(priceAfterCashOut, "priceAfterCashOut is required");
            validationMessages = List.copyOf(validationMessages == null ? List.of() : validationMessages);
            requireText(roundingMode, "roundingMode is required");
        }
    }

    public record LedgerEntry(
        UUID tenantId,
        String quoteId,
        UUID adjustmentId,
        String category,
        String ruleType,
        String inputSnapshotHash,
        BigDecimal pointsDelta,
        BigDecimal bpsDelta,
        String roundingMode,
        String ruleBookVersion,
        String contentHash,
        int waterfallSequence
    ) {
        public static LedgerEntry from(UUID tenantId, String quoteId, CashOutAdjustmentResult result) {
            return new LedgerEntry(
                tenantId,
                quoteId,
                result.adjustmentId,
                result.category,
                "CASH_OUT_LLPA",
                result.inputSnapshotHash,
                result.pointsDelta,
                result.bpsDelta,
                result.roundingMode,
                result.ruleBookVersion,
                hashOf(result.adjustmentId, result.inputSnapshotHash, result.pointsDelta, result.bpsDelta, result.ruleContentHash),
                DEFAULT_WATERFALL_SEQUENCE
            );
        }
    }

    private static boolean containsRange(BigDecimal value, BigDecimal min, BigDecimal max, BoundaryPolicy boundaryPolicy) {
        if (value == null) {
            return false;
        }
        int minComparison = value.compareTo(min);
        int maxComparison = value.compareTo(max);
        boolean aboveMin = boundaryPolicy == BoundaryPolicy.MIN_EXCLUSIVE_MAX_INCLUSIVE
            ? minComparison > 0
            : minComparison >= 0;
        return aboveMin && maxComparison <= 0;
    }

    private static boolean optionalMatch(String configured, String actual) {
        return configured == null || configured.isBlank() || configured.equals(actual);
    }

    private static boolean optionalOverlaps(String left, String right) {
        return left == null || left.isBlank() || right == null || right.isBlank() || left.equals(right);
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    private static UUID deterministicId(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String hashOf(Object... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Object value : values) {
                digest.update(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
