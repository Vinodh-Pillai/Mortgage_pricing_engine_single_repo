package com.wcpe.adjustment;

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
import java.util.UUID;

/**
 * Configurable property/occupancy LLPA evaluator. It uses only tenant supplied
 * rule rows and fails closed when scenario collateral fields are unavailable.
 */
public final class PropertyOccupancyLlpaEvaluator {
    public static final String CATEGORY = "LLPA_PROPERTY_OCCUPANCY";
    public static final int DEFAULT_WATERFALL_SEQUENCE = 50;

    public PropertyOccupancyAdjustmentResult evaluate(EvaluationRequest request, List<PropertyOccupancyRule> rules) {
        Objects.requireNonNull(request, "request is required");
        List<PropertyOccupancyRule> configuredRules = List.copyOf(rules == null ? List.of() : rules);
        if (request.occupancyCode == null || request.occupancyCode.isBlank()) {
            return request.failClosed("ADJ_PROPERTY_FIELD_MISSING: occupancyCode");
        }
        if (request.propertyTypeCode == null || request.propertyTypeCode.isBlank()) {
            return request.failClosed("ADJ_PROPERTY_FIELD_MISSING: propertyTypeCode");
        }
        if (request.units == null) {
            return request.failClosed("ADJ_PROPERTY_FIELD_MISSING: units");
        }
        if (request.units < 1) {
            return request.failClosed("ADJ_OCCUPANCY_UNSUPPORTED: units");
        }

        List<PropertyOccupancyRule> matches = configuredRules.stream()
            .filter(PropertyOccupancyRule::enabled)
            .filter(rule -> rule.tenantId.equals(request.tenantId))
            .filter(rule -> rule.productId.equals(request.productId))
            .filter(rule -> rule.investorId.equals(request.investorId))
            .filter(rule -> rule.channel.equals(request.channel))
            .filter(rule -> request.ruleBookVersion == null || rule.ruleBookVersion.equals(request.ruleBookVersion))
            .filter(rule -> rule.effectiveOn(request.quoteDate))
            .filter(rule -> rule.occupancyCode.equals(request.occupancyCode))
            .filter(rule -> rule.propertyTypeCode.equals(request.propertyTypeCode))
            .filter(rule -> rule.containsUnits(request.units))
            .filter(rule -> optionalMatch(rule.projectTypeCode, request.projectTypeCode))
            .filter(rule -> optionalMatch(rule.stateCode, request.stateCode))
            .filter(rule -> optionalMatch(rule.countyCode, request.countyCode))
            .filter(rule -> optionalMatch(rule.manufacturedHousingFlag, request.manufacturedHousingFlag))
            .filter(rule -> optionalMatch(rule.firstTimeHomebuyerFlag, request.firstTimeHomebuyerFlag))
            .filter(rule -> rule.containsLtv(request.ltvValue(rule.ltvMetric)))
            .filter(rule -> rule.containsLoanAmount(request.loanAmount))
            .filter(rule -> request.qualifierCodes.containsAll(rule.requiredQualifierCodes))
            .sorted(Comparator.comparingInt(PropertyOccupancyRule::priority).thenComparing(rule -> rule.ruleId))
            .toList();

        if (matches.isEmpty()) {
            return request.failClosed("ADJ_PROPERTY_RULE_NOT_FOUND");
        }

        List<PropertyOccupancyRule> applied = new ArrayList<>();
        List<PropertyOccupancyRule> suppressed = new ArrayList<>();
        Map<String, List<PropertyOccupancyRule>> exclusiveGroups = new LinkedHashMap<>();
        for (PropertyOccupancyRule match : matches) {
            if (match.exclusivityGroup == null || match.exclusivityGroup.isBlank()) {
                applied.add(match);
            } else {
                exclusiveGroups.computeIfAbsent(match.exclusivityGroup, ignored -> new ArrayList<>()).add(match);
            }
        }

        for (List<PropertyOccupancyRule> groupMatches : exclusiveGroups.values()) {
            PropertyOccupancyRule selected = groupMatches.get(0);
            if (groupMatches.size() > 1 && groupMatches.get(1).priority == selected.priority) {
                return request.conflict("ADJ_PROPERTY_RULE_AMBIGUOUS");
            }
            applied.add(selected);
            suppressed.addAll(groupMatches.subList(1, groupMatches.size()));
        }

        applied.sort(Comparator.comparingInt(PropertyOccupancyRule::priority).thenComparing(rule -> rule.ruleId));
        suppressed.sort(Comparator.comparingInt(PropertyOccupancyRule::priority).thenComparing(rule -> rule.ruleId));
        return request.applied(applied, suppressed);
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
        FAIL_CLOSED,
        CONFLICT
    }

    public record EvaluationRequest(
        UUID tenantId,
        String quoteId,
        String scenarioId,
        String productId,
        String investorId,
        String channel,
        Instant quoteDate,
        BigDecimal loanAmount,
        BigDecimal priceBeforePropertyOccupancy,
        String occupancyCode,
        String propertyTypeCode,
        Integer units,
        String projectTypeCode,
        Boolean manufacturedHousingFlag,
        String stateCode,
        String countyCode,
        BigDecimal ltv,
        BigDecimal cltv,
        BigDecimal hcltv,
        Boolean firstTimeHomebuyerFlag,
        List<String> qualifierCodes,
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
            Objects.requireNonNull(loanAmount, "loanAmount is required");
            Objects.requireNonNull(priceBeforePropertyOccupancy, "priceBeforePropertyOccupancy is required");
            qualifierCodes = List.copyOf(qualifierCodes == null ? List.of() : qualifierCodes);
        }

        BigDecimal ltvValue(LtvMetric metric) {
            if (metric == null) {
                return null;
            }
            return switch (metric) {
                case LTV -> ltv;
                case CLTV -> cltv;
                case HCLTV -> hcltv;
            };
        }

        PropertyOccupancyAdjustmentResult failClosed(String message) {
            return result(EvaluationStatus.FAIL_CLOSED, List.of(), List.of(), message);
        }

        PropertyOccupancyAdjustmentResult conflict(String message) {
            return result(EvaluationStatus.CONFLICT, List.of(), List.of(), message);
        }

        PropertyOccupancyAdjustmentResult applied(
            List<PropertyOccupancyRule> appliedRules,
            List<PropertyOccupancyRule> suppressedRules
        ) {
            return result(EvaluationStatus.APPLIED, appliedRules, suppressedRules, null);
        }

        private PropertyOccupancyAdjustmentResult result(
            EvaluationStatus status,
            List<PropertyOccupancyRule> appliedRules,
            List<PropertyOccupancyRule> suppressedRules,
            String message
        ) {
            List<PropertyOccupancyRule> applied = List.copyOf(appliedRules == null ? List.of() : appliedRules);
            List<PropertyOccupancyRule> suppressed = List.copyOf(suppressedRules == null ? List.of() : suppressedRules);
            BigDecimal pointsDelta = applied.stream()
                .map(rule -> rule.pointsDelta)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(6, RoundingMode.HALF_UP);
            BigDecimal bpsDelta = pointsDelta.multiply(new BigDecimal("100")).setScale(4, RoundingMode.HALF_UP);
            BigDecimal moneyImpact = status == EvaluationStatus.APPLIED
                ? loanAmount.multiply(pointsDelta).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP)
                : null;
            BigDecimal priceAfter = priceBeforePropertyOccupancy.add(pointsDelta).setScale(6, RoundingMode.HALF_UP);
            List<UUID> appliedRuleIds = applied.stream().map(PropertyOccupancyRule::ruleId).toList();
            List<UUID> suppressedRuleIds = suppressed.stream().map(PropertyOccupancyRule::ruleId).toList();
            List<String> reasonCodes = applied.stream().map(PropertyOccupancyRule::reasonCode).toList();
            String ruleBookVersion = applied.isEmpty() ? null : applied.get(0).ruleBookVersion;
            UUID ruleBookId = applied.isEmpty() ? null : applied.get(0).ruleBookId;
            String contentHash = hashOf(applied.stream().map(PropertyOccupancyRule::contentHash).toList(), suppressedRuleIds);
            return new PropertyOccupancyAdjustmentResult(
                deterministicId(tenantId + quoteId + scenarioId + status + appliedRuleIds + suppressedRuleIds + message),
                CATEGORY,
                status,
                ruleBookId,
                ruleBookVersion,
                inputSnapshotHash(),
                pointsDelta,
                bpsDelta,
                moneyImpact,
                priceAfter,
                reasonCodes,
                message == null ? List.of() : List.of(message),
                occupancyCode,
                propertyTypeCode,
                units,
                projectTypeCode,
                List.copyOf(qualifierCodes),
                appliedRuleIds,
                suppressedRuleIds,
                contentHash,
                "HALF_UP"
            );
        }

        private String inputSnapshotHash() {
            return hashOf(tenantId, quoteId, scenarioId, productId, investorId, channel, quoteDate, loanAmount,
                occupancyCode, propertyTypeCode, units, projectTypeCode, manufacturedHousingFlag, stateCode, countyCode,
                ltv, cltv, hcltv, firstTimeHomebuyerFlag, qualifierCodes, ruleBookVersion);
        }
    }

    public record PropertyOccupancyRule(
        UUID tenantId,
        UUID ruleBookId,
        UUID ruleId,
        String ruleBookVersion,
        String productId,
        String investorId,
        String channel,
        String occupancyCode,
        String propertyTypeCode,
        int unitMin,
        int unitMax,
        String projectTypeCode,
        Boolean manufacturedHousingFlag,
        String stateCode,
        String countyCode,
        LtvMetric ltvMetric,
        String ltvBandKey,
        BigDecimal ltvMin,
        BigDecimal ltvMax,
        BoundaryPolicy boundaryPolicy,
        String loanAmountBandKey,
        BigDecimal loanAmountMin,
        BigDecimal loanAmountMax,
        Boolean firstTimeHomebuyerFlag,
        List<String> requiredQualifierCodes,
        BigDecimal pointsDelta,
        String reasonCode,
        int priority,
        String exclusivityGroup,
        Instant effectiveStart,
        Instant effectiveEnd,
        String contentHash,
        boolean enabled
    ) {
        public PropertyOccupancyRule {
            Objects.requireNonNull(tenantId, "tenantId is required");
            Objects.requireNonNull(ruleBookId, "ruleBookId is required");
            Objects.requireNonNull(ruleId, "ruleId is required");
            requireText(ruleBookVersion, "ruleBookVersion is required");
            requireText(productId, "productId is required");
            requireText(investorId, "investorId is required");
            requireText(channel, "channel is required");
            requireText(occupancyCode, "occupancyCode is required");
            requireText(propertyTypeCode, "propertyTypeCode is required");
            Objects.requireNonNull(pointsDelta, "pointsDelta is required");
            requireText(reasonCode, "reasonCode is required");
            Objects.requireNonNull(effectiveStart, "effectiveStart is required");
            boundaryPolicy = boundaryPolicy == null ? BoundaryPolicy.MIN_INCLUSIVE_MAX_INCLUSIVE : boundaryPolicy;
            requiredQualifierCodes = List.copyOf(requiredQualifierCodes == null ? List.of() : requiredQualifierCodes);
            if (unitMin < 1 || unitMax < 1 || unitMin > unitMax) {
                throw new IllegalArgumentException("unit range must be positive and ordered");
            }
            if (priority < 0) {
                throw new IllegalArgumentException("priority must be non-negative");
            }
            if (ltvBandKey != null && !ltvBandKey.isBlank()) {
                Objects.requireNonNull(ltvMetric, "ltvMetric is required when ltvBandKey is configured");
                Objects.requireNonNull(ltvMin, "ltvMin is required when ltvBandKey is configured");
                Objects.requireNonNull(ltvMax, "ltvMax is required when ltvBandKey is configured");
                if (ltvMin.compareTo(ltvMax) > 0) {
                    throw new IllegalArgumentException("ltvMin cannot exceed ltvMax");
                }
            }
            if (loanAmountBandKey != null && !loanAmountBandKey.isBlank()) {
                Objects.requireNonNull(loanAmountMin, "loanAmountMin is required when loanAmountBandKey is configured");
                Objects.requireNonNull(loanAmountMax, "loanAmountMax is required when loanAmountBandKey is configured");
                if (loanAmountMin.compareTo(loanAmountMax) > 0) {
                    throw new IllegalArgumentException("loanAmountMin cannot exceed loanAmountMax");
                }
            }
            contentHash = contentHash == null || contentHash.isBlank()
                ? hashOf(tenantId, ruleBookId, ruleId, ruleBookVersion, productId, investorId, channel, occupancyCode,
                    propertyTypeCode, unitMin, unitMax, projectTypeCode, manufacturedHousingFlag, stateCode, countyCode,
                    ltvMetric, ltvBandKey, ltvMin, ltvMax, loanAmountBandKey, loanAmountMin, loanAmountMax,
                    firstTimeHomebuyerFlag, requiredQualifierCodes, pointsDelta, reasonCode, priority, exclusivityGroup)
                : contentHash;
        }

        boolean effectiveOn(Instant quoteDate) {
            return !quoteDate.isBefore(effectiveStart) && (effectiveEnd == null || quoteDate.isBefore(effectiveEnd));
        }

        boolean containsUnits(Integer units) {
            return units != null && units >= unitMin && units <= unitMax;
        }

        boolean containsLtv(BigDecimal ltvValue) {
            if (ltvBandKey == null || ltvBandKey.isBlank()) {
                return true;
            }
            return containsRange(ltvValue, ltvMin, ltvMax, boundaryPolicy);
        }

        boolean containsLoanAmount(BigDecimal loanAmount) {
            if (loanAmountBandKey == null || loanAmountBandKey.isBlank()) {
                return true;
            }
            return containsRange(loanAmount, loanAmountMin, loanAmountMax, BoundaryPolicy.MIN_INCLUSIVE_MAX_INCLUSIVE);
        }
    }

    public record PropertyOccupancyAdjustmentResult(
        UUID adjustmentId,
        String category,
        EvaluationStatus status,
        UUID ruleBookId,
        String ruleBookVersion,
        String inputSnapshotHash,
        BigDecimal pointsDelta,
        BigDecimal bpsDelta,
        BigDecimal moneyImpact,
        BigDecimal priceAfterPropertyOccupancy,
        List<String> reasonCodes,
        List<String> validationMessages,
        String occupancyCode,
        String propertyTypeCode,
        Integer units,
        String projectTypeCode,
        List<String> qualifierCodes,
        List<UUID> appliedRuleIds,
        List<UUID> suppressedRuleIds,
        String ruleContentHash,
        String roundingMode
    ) {
        public PropertyOccupancyAdjustmentResult {
            Objects.requireNonNull(adjustmentId, "adjustmentId is required");
            requireText(category, "category is required");
            Objects.requireNonNull(status, "status is required");
            requireText(inputSnapshotHash, "inputSnapshotHash is required");
            Objects.requireNonNull(pointsDelta, "pointsDelta is required");
            Objects.requireNonNull(bpsDelta, "bpsDelta is required");
            Objects.requireNonNull(priceAfterPropertyOccupancy, "priceAfterPropertyOccupancy is required");
            reasonCodes = List.copyOf(reasonCodes == null ? List.of() : reasonCodes);
            validationMessages = List.copyOf(validationMessages == null ? List.of() : validationMessages);
            qualifierCodes = List.copyOf(qualifierCodes == null ? List.of() : qualifierCodes);
            appliedRuleIds = List.copyOf(appliedRuleIds == null ? List.of() : appliedRuleIds);
            suppressedRuleIds = List.copyOf(suppressedRuleIds == null ? List.of() : suppressedRuleIds);
            requireText(ruleContentHash, "ruleContentHash is required");
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
        List<UUID> appliedRuleIds,
        List<UUID> suppressedRuleIds,
        String contentHash,
        int waterfallSequence
    ) {
        public LedgerEntry {
            appliedRuleIds = List.copyOf(appliedRuleIds == null ? List.of() : appliedRuleIds);
            suppressedRuleIds = List.copyOf(suppressedRuleIds == null ? List.of() : suppressedRuleIds);
        }

        public static LedgerEntry from(UUID tenantId, String quoteId, PropertyOccupancyAdjustmentResult result) {
            return new LedgerEntry(
                tenantId,
                quoteId,
                result.adjustmentId,
                result.category,
                "PROPERTY_OCCUPANCY_LLPA",
                result.inputSnapshotHash,
                result.pointsDelta,
                result.bpsDelta,
                result.roundingMode,
                result.ruleBookVersion,
                result.appliedRuleIds,
                result.suppressedRuleIds,
                hashOf(result.adjustmentId, result.inputSnapshotHash, result.pointsDelta, result.bpsDelta,
                    result.appliedRuleIds, result.suppressedRuleIds, result.ruleContentHash),
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

    private static boolean optionalMatch(Boolean configured, Boolean actual) {
        return configured == null || configured.equals(actual);
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
