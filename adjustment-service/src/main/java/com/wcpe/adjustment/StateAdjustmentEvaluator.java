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
 * Configurable jurisdiction/state adjustment evaluator for PII-06-S05.
 * It consumes tenant-owned rule rows and never embeds state fee or LLPA values.
 */
public final class StateAdjustmentEvaluator {
    public static final String POINT_CATEGORY = "LLPA_STATE";
    public static final String MONEY_CATEGORY = "FEE_STATE";
    public static final String BLOCK_CATEGORY = "COMPLIANCE_JURISDICTION_BLOCK";
    public static final int DEFAULT_WATERFALL_SEQUENCE = 60;

    public StateAdjustmentResult evaluate(EvaluationRequest request, List<JurisdictionAdjustmentRule> rules) {
        Objects.requireNonNull(request, "request is required");
        List<JurisdictionAdjustmentRule> configuredRules = List.copyOf(rules == null ? List.of() : rules);
        if (request.propertyState == null || request.propertyState.isBlank()) {
            return request.failClosed("ADJ_STATE_MISSING");
        }

        StateCode stateCode;
        try {
            stateCode = StateCode.of(request.propertyState);
        } catch (IllegalArgumentException ex) {
            return request.failClosed("ADJ_STATE_MISSING");
        }
        String countyFips = normalizeOptional(request.propertyCountyFips);
        if (countyFips != null && !countyFips.matches("\\d{5}")) {
            return request.failClosed("ADJ_JURISDICTION_UNSUPPORTED");
        }
        String municipality = normalizeOptional(request.propertyMunicipality);

        List<JurisdictionAdjustmentRule> matches = configuredRules.stream()
            .filter(JurisdictionAdjustmentRule::enabled)
            .filter(rule -> rule.tenantId().equals(request.tenantId))
            .filter(rule -> request.ruleBookVersion == null || rule.ruleBookVersion().equals(request.ruleBookVersion))
            .filter(rule -> rule.effectiveOn(request.quoteDate))
            .filter(rule -> rule.stateCode().equals(stateCode))
            .filter(rule -> rule.countyFips() == null || rule.countyFips().equals(countyFips))
            .filter(rule -> rule.municipalityCode() == null || rule.municipalityCode().equals(municipality))
            .filter(rule -> selectorMatches(rule.productId(), request.productId))
            .filter(rule -> selectorMatches(rule.investorId(), request.investorId))
            .filter(rule -> selectorMatches(rule.channel(), request.channel))
            .filter(rule -> selectorMatches(rule.loanPurpose(), request.loanPurpose))
            .filter(rule -> selectorMatches(rule.occupancy(), request.occupancy))
            .filter(rule -> selectorMatches(rule.propertyType(), request.propertyType))
            .filter(rule -> loanAmountMatches(rule.minLoanAmount(), rule.maxLoanAmount(), request.loanAmount))
            .sorted(ruleOrder())
            .toList();

        if (matches.isEmpty()) {
            return request.failClosed("ADJ_JURISDICTION_UNSUPPORTED");
        }

        int maxSpecificity = matches.stream().mapToInt(JurisdictionAdjustmentRule::specificityRank).max().orElse(0);
        List<JurisdictionAdjustmentRule> mostSpecific = matches.stream()
            .filter(rule -> rule.specificityRank() == maxSpecificity)
            .toList();

        if (hasSameSpecificityPriorityConflict(mostSpecific)) {
            return request.conflict("ADJ_STATE_RULE_AMBIGUOUS");
        }

        List<JurisdictionAdjustmentRule> applied = new ArrayList<>();
        List<JurisdictionAdjustmentRule> suppressed = new ArrayList<>();
        Map<String, List<JurisdictionAdjustmentRule>> exclusiveGroups = new LinkedHashMap<>();
        for (JurisdictionAdjustmentRule match : mostSpecific) {
            if (match.exclusivityGroup() == null || match.exclusivityGroup().isBlank()) {
                applied.add(match);
            } else {
                exclusiveGroups.computeIfAbsent(match.exclusivityGroup(), ignored -> new ArrayList<>()).add(match);
            }
        }

        for (List<JurisdictionAdjustmentRule> groupMatches : exclusiveGroups.values()) {
            List<JurisdictionAdjustmentRule> sorted = groupMatches.stream().sorted(ruleOrder()).toList();
            JurisdictionAdjustmentRule selected = sorted.get(0);
            if (sorted.size() > 1 && sorted.get(1).priority() == selected.priority()) {
                return request.conflict("ADJ_STATE_RULE_AMBIGUOUS");
            }
            applied.add(selected);
            suppressed.addAll(sorted.subList(1, sorted.size()));
        }

        applied.sort(ruleOrder());
        suppressed.sort(ruleOrder());
        return request.applied(applied, suppressed, stateCode, countyFips, municipality, maxSpecificity);
    }

    public enum EvaluationStatus {
        APPLIED,
        FAIL_CLOSED,
        CONFLICT,
        COMPLIANCE_BLOCK
    }

    public record EvaluationRequest(
        UUID tenantId,
        String quoteId,
        String scenarioId,
        String productId,
        String investorId,
        String channel,
        String loanPurpose,
        String occupancy,
        String propertyType,
        Instant quoteDate,
        BigDecimal loanAmount,
        BigDecimal priceBeforeStateAdjustment,
        String propertyState,
        String propertyCountyFips,
        String propertyMunicipality,
        String ruleBookVersion
    ) {
        public EvaluationRequest {
            Objects.requireNonNull(tenantId, "tenantId is required");
            requireText(quoteId, "quoteId is required");
            requireText(scenarioId, "scenarioId is required");
            requireText(productId, "productId is required");
            requireText(investorId, "investorId is required");
            requireText(channel, "channel is required");
            requireText(loanPurpose, "loanPurpose is required");
            requireText(occupancy, "occupancy is required");
            requireText(propertyType, "propertyType is required");
            Objects.requireNonNull(quoteDate, "quoteDate is required");
            Objects.requireNonNull(loanAmount, "loanAmount is required");
            Objects.requireNonNull(priceBeforeStateAdjustment, "priceBeforeStateAdjustment is required");
        }

        StateAdjustmentResult failClosed(String message) {
            return result(EvaluationStatus.FAIL_CLOSED, List.of(), List.of(), message, null, null, null, 0);
        }

        StateAdjustmentResult conflict(String message) {
            return result(EvaluationStatus.CONFLICT, List.of(), List.of(), message, null, null, null, 0);
        }

        StateAdjustmentResult applied(
            List<JurisdictionAdjustmentRule> appliedRules,
            List<JurisdictionAdjustmentRule> suppressedRules,
            StateCode stateCode,
            String countyFips,
            String municipality,
            int specificityRank
        ) {
            boolean complianceBlock = appliedRules.stream().anyMatch(JurisdictionAdjustmentRule::isBlocking);
            return result(
                complianceBlock ? EvaluationStatus.COMPLIANCE_BLOCK : EvaluationStatus.APPLIED,
                appliedRules,
                suppressedRules,
                complianceBlock ? "ADJ_STATE_COMPLIANCE_BLOCK" : null,
                stateCode,
                countyFips,
                municipality,
                specificityRank
            );
        }

        private StateAdjustmentResult result(
            EvaluationStatus status,
            List<JurisdictionAdjustmentRule> appliedRules,
            List<JurisdictionAdjustmentRule> suppressedRules,
            String message,
            StateCode stateCode,
            String countyFips,
            String municipality,
            int specificityRank
        ) {
            List<JurisdictionAdjustmentRule> applied = List.copyOf(appliedRules == null ? List.of() : appliedRules);
            List<JurisdictionAdjustmentRule> suppressed = List.copyOf(suppressedRules == null ? List.of() : suppressedRules);
            List<StateAdjustmentLine> lines = status == EvaluationStatus.FAIL_CLOSED || status == EvaluationStatus.CONFLICT
                ? List.of()
                : applied.stream().map(rule -> StateAdjustmentLine.from(rule, loanAmount, specificityRank)).toList();
            BigDecimal pointsDelta = lines.stream()
                .map(StateAdjustmentLine::pointsDelta)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(6, RoundingMode.HALF_UP);
            BigDecimal bpsDelta = lines.stream()
                .map(StateAdjustmentLine::bpsDelta)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(4, RoundingMode.HALF_UP);
            BigDecimal moneyImpact = lines.stream()
                .map(StateAdjustmentLine::moneyImpact)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
            BigDecimal priceAfter = priceBeforeStateAdjustment.add(pointsDelta).setScale(6, RoundingMode.HALF_UP);
            List<UUID> appliedRuleIds = applied.stream().map(JurisdictionAdjustmentRule::ruleId).toList();
            List<UUID> suppressedRuleIds = suppressed.stream().map(JurisdictionAdjustmentRule::ruleId).toList();
            String ruleBookVersion = applied.isEmpty() ? null : applied.get(0).ruleBookVersion();
            UUID ruleBookId = applied.isEmpty() ? null : applied.get(0).ruleBookId();
            String category = status == EvaluationStatus.COMPLIANCE_BLOCK
                ? BLOCK_CATEGORY
                : lines.stream().anyMatch(StateAdjustmentLine::hasMoneyImpact)
                    ? MONEY_CATEGORY
                    : POINT_CATEGORY;
            return new StateAdjustmentResult(
                deterministicId(tenantId + quoteId + scenarioId + status + appliedRuleIds + suppressedRuleIds + message),
                category,
                status,
                ruleBookId,
                ruleBookVersion,
                inputSnapshotHash(),
                jurisdictionKey(stateCode, countyFips, municipality),
                specificityRank,
                pointsDelta,
                bpsDelta,
                moneyImpact,
                priceAfter,
                lines,
                message == null ? List.of() : List.of(message),
                appliedRuleIds,
                suppressedRuleIds,
                hashOf(applied.stream().map(JurisdictionAdjustmentRule::contentHash).toList(), suppressedRuleIds),
                "HALF_UP"
            );
        }

        private String inputSnapshotHash() {
            return hashOf(tenantId, quoteId, scenarioId, productId, investorId, channel, loanPurpose, occupancy,
                propertyType, quoteDate, loanAmount, propertyState, propertyCountyFips, propertyMunicipality,
                ruleBookVersion);
        }
    }

    public record StateAdjustmentLine(
        String category,
        String method,
        String jurisdictionKey,
        int specificityRank,
        UUID ruleId,
        BigDecimal rawAmount,
        BigDecimal pointsDelta,
        BigDecimal bpsDelta,
        BigDecimal moneyImpact,
        boolean capApplied,
        boolean floorApplied,
        String reasonCode,
        String sourceRef,
        String ruleBookVersion,
        String contentHash
    ) {
        static StateAdjustmentLine from(JurisdictionAdjustmentRule rule, BigDecimal loanAmount, int specificityRank) {
            BigDecimal raw = rule.applyFormula(loanAmount);
            AdjustmentCapFloor capFloor = new AdjustmentCapFloor(rule.capAmount(), rule.floorAmount());
            BigDecimal finalAmount = capFloor.apply(raw);
            BigDecimal points = BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
            BigDecimal bps = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
            BigDecimal money = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            String category = POINT_CATEGORY;
            switch (rule.method()) {
                case "POINTS_DELTA" -> {
                    points = finalAmount.setScale(6, RoundingMode.HALF_UP);
                    bps = points.multiply(new BigDecimal("100")).setScale(4, RoundingMode.HALF_UP);
                }
                case "BPS_DELTA" -> {
                    bps = finalAmount.setScale(4, RoundingMode.HALF_UP);
                    points = bps.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP);
                }
                case "FIXED_MONEY", "PERCENT_OF_LOAN_AMOUNT" -> {
                    category = MONEY_CATEGORY;
                    money = finalAmount.setScale(2, RoundingMode.HALF_UP);
                }
                default -> throw new IllegalStateException("method is not supported");
            }
            String jurisdictionKey = StateAdjustmentEvaluator.jurisdictionKey(
                rule.stateCode(),
                rule.countyFips(),
                rule.municipalityCode()
            );
            return new StateAdjustmentLine(
                rule.isBlocking() ? BLOCK_CATEGORY : category,
                rule.method(),
                jurisdictionKey,
                specificityRank,
                rule.ruleId(),
                raw,
                points,
                bps,
                money,
                capFloor.isCapApplied(raw),
                capFloor.isFloorApplied(raw),
                rule.reasonCode(),
                rule.sourceRef(),
                rule.ruleBookVersion(),
                hashOf(rule.contentHash(), jurisdictionKey, raw, points, bps, money)
            );
        }

        boolean hasMoneyImpact() {
            return moneyImpact.compareTo(BigDecimal.ZERO) != 0 || MONEY_CATEGORY.equals(category);
        }
    }

    public record StateAdjustmentResult(
        UUID adjustmentId,
        String category,
        EvaluationStatus status,
        UUID ruleBookId,
        String ruleBookVersion,
        String inputSnapshotHash,
        String jurisdictionKey,
        int specificityRank,
        BigDecimal pointsDelta,
        BigDecimal bpsDelta,
        BigDecimal moneyImpact,
        BigDecimal priceAfterStateAdjustment,
        List<StateAdjustmentLine> ledgerLines,
        List<String> validationMessages,
        List<UUID> appliedRuleIds,
        List<UUID> suppressedRuleIds,
        String ruleContentHash,
        String roundingMode
    ) {
        public StateAdjustmentResult {
            Objects.requireNonNull(adjustmentId, "adjustmentId is required");
            requireText(category, "category is required");
            Objects.requireNonNull(status, "status is required");
            requireText(inputSnapshotHash, "inputSnapshotHash is required");
            Objects.requireNonNull(pointsDelta, "pointsDelta is required");
            Objects.requireNonNull(bpsDelta, "bpsDelta is required");
            Objects.requireNonNull(moneyImpact, "moneyImpact is required");
            Objects.requireNonNull(priceAfterStateAdjustment, "priceAfterStateAdjustment is required");
            ledgerLines = List.copyOf(ledgerLines == null ? List.of() : ledgerLines);
            validationMessages = List.copyOf(validationMessages == null ? List.of() : validationMessages);
            appliedRuleIds = List.copyOf(appliedRuleIds == null ? List.of() : appliedRuleIds);
            suppressedRuleIds = List.copyOf(suppressedRuleIds == null ? List.of() : suppressedRuleIds);
            requireText(ruleContentHash, "ruleContentHash is required");
            requireText(roundingMode, "roundingMode is required");
        }
    }

    private static boolean hasSameSpecificityPriorityConflict(List<JurisdictionAdjustmentRule> rules) {
        Map<String, Integer> seen = new LinkedHashMap<>();
        for (JurisdictionAdjustmentRule rule : rules) {
            if (rule.exclusivityGroup() != null && !rule.exclusivityGroup().isBlank()) {
                continue;
            }
            String key = rule.specificityRank() + ":" + rule.priority() + ":" + rule.method();
            int count = seen.getOrDefault(key, 0) + 1;
            if (count > 1) {
                return true;
            }
            seen.put(key, count);
        }
        return false;
    }

    private static Comparator<JurisdictionAdjustmentRule> ruleOrder() {
        return Comparator.comparingInt(JurisdictionAdjustmentRule::priority)
            .thenComparing(JurisdictionAdjustmentRule::ruleId);
    }

    private static String jurisdictionKey(StateCode stateCode, String countyFips, String municipality) {
        if (stateCode == null) {
            return null;
        }
        String county = countyFips == null ? "*" : countyFips;
        String city = municipality == null ? "*" : municipality;
        return stateCode.code() + ":" + county + ":" + city;
    }

    private static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static boolean selectorMatches(String configuredSelector, String requestSelector) {
        return configuredSelector == null || configuredSelector.equals(normalizeOptional(requestSelector));
    }

    private static boolean loanAmountMatches(BigDecimal minimumInclusive, BigDecimal maximumInclusive, BigDecimal loanAmount) {
        Objects.requireNonNull(loanAmount, "loanAmount is required");
        return (minimumInclusive == null || loanAmount.compareTo(minimumInclusive) >= 0)
            && (maximumInclusive == null || loanAmount.compareTo(maximumInclusive) <= 0);
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
