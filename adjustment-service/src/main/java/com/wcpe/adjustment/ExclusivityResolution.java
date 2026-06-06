package com.wcpe.adjustment;

import com.wcpe.adjustment.AdjustmentRuleBook.AdjustmentRule;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Resolves matched adjustment rules that share an exclusivity group.
 */
public final class ExclusivityResolution {
    private ExclusivityResolution() {
    }

    public enum GroupStrategy {
        HIGHEST_COST,
        LOWEST_COST,
        FIRST_PRIORITY,
        FAIL_ON_MULTIPLE
    }

    public record ResolutionResult(AdjustmentRule winningRule, List<UUID> suppressedRuleIds) {
        public ResolutionResult {
            suppressedRuleIds = List.copyOf(suppressedRuleIds == null ? List.of() : suppressedRuleIds);
        }
    }

    public static ResolutionResult resolve(List<AdjustmentRule> matchingRules, GroupStrategy strategy) {
        Objects.requireNonNull(matchingRules, "matchingRules is required");
        Objects.requireNonNull(strategy, "strategy is required");

        if (matchingRules.isEmpty()) {
            return new ResolutionResult(null, List.of());
        }
        if (strategy == GroupStrategy.FAIL_ON_MULTIPLE && matchingRules.size() > 1) {
            throw new IllegalStateException("multiple matching rules in exclusivity group");
        }

        AdjustmentRule winningRule = matchingRules.stream()
            .min(comparatorFor(strategy))
            .orElseThrow();
        List<UUID> suppressedRuleIds = matchingRules.stream()
            .filter(rule -> !rule.ruleId().equals(winningRule.ruleId()))
            .map(AdjustmentRule::ruleId)
            .toList();
        return new ResolutionResult(winningRule, suppressedRuleIds);
    }

    public static List<ResolutionResult> resolveAll(List<AdjustmentRule> matchingRules, GroupStrategy strategy) {
        Objects.requireNonNull(matchingRules, "matchingRules is required");
        Objects.requireNonNull(strategy, "strategy is required");

        Map<String, List<AdjustmentRule>> groupedRules = new LinkedHashMap<>();
        for (AdjustmentRule rule : matchingRules) {
            if (rule.exclusivityGroup() != null && !rule.exclusivityGroup().isBlank()) {
                groupedRules.computeIfAbsent(rule.exclusivityGroup(), ignored -> new ArrayList<>()).add(rule);
            }
        }

        List<ResolutionResult> results = new ArrayList<>();
        List<String> emittedGroups = new ArrayList<>();
        for (AdjustmentRule rule : matchingRules) {
            if (rule.exclusivityGroup() == null || rule.exclusivityGroup().isBlank()) {
                results.add(new ResolutionResult(rule, List.of()));
            } else if (!emittedGroups.contains(rule.exclusivityGroup())) {
                results.add(resolve(groupedRules.get(rule.exclusivityGroup()), strategy));
                emittedGroups.add(rule.exclusivityGroup());
            }
        }
        return List.copyOf(results);
    }

    private static Comparator<AdjustmentRule> comparatorFor(GroupStrategy strategy) {
        return switch (strategy) {
            case HIGHEST_COST -> (left, right) -> compareByCostDescendingThenPriority(left, right);
            case LOWEST_COST -> (left, right) -> compareByCostAscendingThenPriority(left, right);
            case FIRST_PRIORITY, FAIL_ON_MULTIPLE -> priorityComparator();
        };
    }

    private static int compareByCostDescendingThenPriority(AdjustmentRule left, AdjustmentRule right) {
        BigDecimal leftAmount = left.output().configuredAmount();
        BigDecimal rightAmount = right.output().configuredAmount();
        if (leftAmount != null && rightAmount != null) {
            int amountComparison = rightAmount.compareTo(leftAmount);
            if (amountComparison != 0) {
                return amountComparison;
            }
        }
        return priorityComparator().compare(left, right);
    }

    private static int compareByCostAscendingThenPriority(AdjustmentRule left, AdjustmentRule right) {
        BigDecimal leftAmount = left.output().configuredAmount();
        BigDecimal rightAmount = right.output().configuredAmount();
        if (leftAmount != null && rightAmount != null) {
            int amountComparison = leftAmount.compareTo(rightAmount);
            if (amountComparison != 0) {
                return amountComparison;
            }
        }
        return priorityComparator().compare(left, right);
    }

    private static Comparator<AdjustmentRule> priorityComparator() {
        return Comparator.comparingInt(AdjustmentRule::priority)
            .thenComparing(AdjustmentRule::ruleId);
    }
}
