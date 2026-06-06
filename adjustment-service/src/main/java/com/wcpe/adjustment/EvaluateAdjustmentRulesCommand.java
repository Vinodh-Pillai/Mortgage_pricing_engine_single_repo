package com.wcpe.adjustment;

import com.wcpe.adjustment.AdjustmentRuleBook.AdjustmentCondition;
import com.wcpe.adjustment.AdjustmentRuleBook.AdjustmentOutput;
import com.wcpe.adjustment.AdjustmentRuleBook.AdjustmentOutputType;
import com.wcpe.adjustment.AdjustmentRuleBook.AdjustmentRule;
import com.wcpe.adjustment.AdjustmentRuleBook.PricingPrecisionPolicy;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Internal quote-waterfall command for evaluating a published adjustment rule book
 * against normalized quote attributes.
 */
public record EvaluateAdjustmentRulesCommand(
    UUID ruleBookId,
    String ruleBookVersion,
    Map<String, String> loanAttributes,
    BigDecimal loanAmount,
    String correlationId,
    PricingPrecisionPolicy precisionPolicy
) {
    public EvaluateAdjustmentRulesCommand {
        Objects.requireNonNull(ruleBookId, "ruleBookId is required");
        Objects.requireNonNull(ruleBookVersion, "ruleBookVersion is required");
        loanAttributes = Map.copyOf(Objects.requireNonNull(loanAttributes, "loanAttributes is required"));
        Objects.requireNonNull(loanAmount, "loanAmount is required");
        Objects.requireNonNull(correlationId, "correlationId is required");
        precisionPolicy = precisionPolicy == null ? PricingPrecisionPolicy.defaultPolicy() : precisionPolicy;
    }

    public AdjustmentEvaluationResult evaluate(
        AdjustmentRuleBook ruleBook,
        ExclusivityResolution.GroupStrategy defaultStrategy
    ) {
        Objects.requireNonNull(ruleBook, "ruleBook is required");
        Objects.requireNonNull(defaultStrategy, "defaultStrategy is required");
        if (!ruleBook.ruleBookId().equals(ruleBookId)) {
            throw new IllegalArgumentException("ruleBookId does not match command");
        }
        if (!ruleBook.version().equals(ruleBookVersion)) {
            throw new IllegalArgumentException("ruleBookVersion does not match command");
        }

        List<AdjustmentRule> matchingRules = ruleBook.rules().stream()
            .filter(AdjustmentRule::enabled)
            .filter(this::matchesAllConditions)
            .sorted(ruleComparator())
            .toList();

        Set<UUID> appliedRuleIds = new HashSet<>();
        Set<UUID> suppressedRuleIds = new HashSet<>();
        Map<String, List<AdjustmentRule>> exclusiveGroups = new LinkedHashMap<>();

        for (AdjustmentRule rule : matchingRules) {
            if (rule.exclusivityGroup() == null || rule.exclusivityGroup().isBlank()) {
                appliedRuleIds.add(rule.ruleId());
            } else {
                exclusiveGroups.computeIfAbsent(rule.exclusivityGroup(), ignored -> new ArrayList<>()).add(rule);
            }
        }

        for (List<AdjustmentRule> groupRules : exclusiveGroups.values()) {
            ExclusivityResolution.ResolutionResult resolution = ExclusivityResolution.resolve(groupRules, defaultStrategy);
            if (resolution.winningRule() != null) {
                appliedRuleIds.add(resolution.winningRule().ruleId());
            }
            suppressedRuleIds.addAll(resolution.suppressedRuleIds());
        }

        List<AdjustmentLine> lines = matchingRules.stream()
            .filter(rule -> appliedRuleIds.contains(rule.ruleId()) || suppressedRuleIds.contains(rule.ruleId()))
            .map(rule -> toLine(rule, appliedRuleIds.contains(rule.ruleId())))
            .toList();

        return new AdjustmentEvaluationResult(lines, ruleBookId, ruleBookVersion, correlationId);
    }

    private boolean matchesAllConditions(AdjustmentRule rule) {
        return rule.conditions().stream().allMatch(this::matchesCondition);
    }

    private boolean matchesCondition(AdjustmentCondition condition) {
        String actualValue = loanAttributes.get(condition.dimension());
        return switch (condition.operator()) {
            case EQ -> !condition.configuredValues().isEmpty()
                && Objects.equals(actualValue, condition.configuredValues().get(0));
            case IN -> actualValue != null && condition.configuredValues().contains(actualValue);
            case EXISTS -> loanAttributes.containsKey(condition.dimension());
            case NOT_EXISTS -> !loanAttributes.containsKey(condition.dimension());
            case RANGE_CLOSED -> matchesClosedRange(actualValue, condition.configuredValues());
            case RANGE_OPEN_END -> matchesOpenEndRange(actualValue, condition.configuredValues());
            case BOOLEAN_IS -> matchesBoolean(actualValue, condition.configuredValues());
        };
    }

    private static boolean matchesClosedRange(String actualValue, List<String> configuredValues) {
        if (configuredValues.size() < 2) {
            return false;
        }
        BigDecimal actual = parseDecimal(actualValue);
        BigDecimal minimum = parseDecimal(configuredValues.get(0));
        BigDecimal maximum = parseDecimal(configuredValues.get(1));
        return actual != null && minimum != null && maximum != null
            && actual.compareTo(minimum) >= 0
            && actual.compareTo(maximum) <= 0;
    }

    private static boolean matchesOpenEndRange(String actualValue, List<String> configuredValues) {
        if (configuredValues.isEmpty()) {
            return false;
        }
        BigDecimal actual = parseDecimal(actualValue);
        BigDecimal minimum = parseDecimal(configuredValues.get(0));
        return actual != null && minimum != null && actual.compareTo(minimum) >= 0;
    }

    private static boolean matchesBoolean(String actualValue, List<String> configuredValues) {
        if (actualValue == null || configuredValues.isEmpty()) {
            return false;
        }
        String normalizedActual = actualValue.trim().toLowerCase(java.util.Locale.ROOT);
        String normalizedExpected = configuredValues.get(0).trim().toLowerCase(java.util.Locale.ROOT);
        if ((!"true".equals(normalizedActual) && !"false".equals(normalizedActual))
            || (!"true".equals(normalizedExpected) && !"false".equals(normalizedExpected))) {
            return false;
        }
        return normalizedActual.equals(normalizedExpected);
    }

    private static BigDecimal parseDecimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private AdjustmentLine toLine(AdjustmentRule rule, boolean applied) {
        AdjustmentOutput output = rule.output();
        return new AdjustmentLine(
            rule.ruleId(),
            rule.reasonCode(),
            applied ? adjustmentAmount(output) : BigDecimal.ZERO,
            output.type().name(),
            applied
        );
    }

    private BigDecimal adjustmentAmount(AdjustmentOutput output) {
        if (output.configuredAmount() == null) {
            return BigDecimal.ZERO;
        }
        if (output.type() == AdjustmentOutputType.PERCENT_OF_LOAN_AMOUNT) {
            return loanAmount
                .multiply(output.configuredAmount())
                .divide(new BigDecimal("100"), precisionPolicy.moneyScale(), precisionPolicy.roundingMode());
        }
        return precisionPolicy.normalize(output.type(), output.configuredAmount());
    }

    private static Comparator<AdjustmentRule> ruleComparator() {
        return Comparator.comparingInt(AdjustmentRule::priority)
            .thenComparing(AdjustmentRule::ruleId);
    }

    public record AdjustmentEvaluationResult(
        List<AdjustmentLine> lines,
        UUID ruleBookId,
        String version,
        String correlationId
    ) {
        public AdjustmentEvaluationResult {
            lines = List.copyOf(lines == null ? List.of() : lines);
            Objects.requireNonNull(ruleBookId, "ruleBookId is required");
            Objects.requireNonNull(version, "version is required");
            Objects.requireNonNull(correlationId, "correlationId is required");
        }
    }

    public record AdjustmentLine(
        UUID ruleId,
        String reasonCode,
        BigDecimal adjustmentAmount,
        String adjustmentType,
        boolean applied
    ) {
        public AdjustmentLine {
            Objects.requireNonNull(ruleId, "ruleId is required");
            Objects.requireNonNull(reasonCode, "reasonCode is required");
            Objects.requireNonNull(adjustmentAmount, "adjustmentAmount is required");
            Objects.requireNonNull(adjustmentType, "adjustmentType is required");
        }
    }
}
