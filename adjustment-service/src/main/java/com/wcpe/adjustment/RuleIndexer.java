package com.wcpe.adjustment;

import com.wcpe.adjustment.AdjustmentRuleBook.AdjustmentOutput;
import com.wcpe.adjustment.AdjustmentRuleBook.AdjustmentOutputType;
import com.wcpe.adjustment.AdjustmentRuleBook.AdjustmentRule;
import com.wcpe.adjustment.AdjustmentRuleBook.PricingPrecisionPolicy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class RuleIndexer {
    private static final Comparator<CompiledRule> RULE_ORDER = Comparator
        .comparingInt(CompiledRule::priority)
        .thenComparing(CompiledRule::ruleId);

    private final ConditionCompiler conditionCompiler;
    private final PrecisionNormalizer normalizer;

    public RuleIndexer() {
        this(new ConditionCompiler(), new PrecisionNormalizer());
    }

    public RuleIndexer(ConditionCompiler conditionCompiler, PrecisionNormalizer normalizer) {
        this.conditionCompiler = conditionCompiler;
        this.normalizer = normalizer;
    }

    public RuleBookIndex index(AdjustmentRuleBook ruleBook) {
        Map<String, Set<UUID>> dimensionIndex = new HashMap<>();
        Map<String, Map<String, Set<UUID>>> exactDimensionIndex = new HashMap<>();
        Map<String, Set<UUID>> residualDimensionIndex = new HashMap<>();
        Map<UUID, CompiledRule> compiledRules = new HashMap<>();
        Set<UUID> rulesWithNoRequiredDimensions = new HashSet<>();

        for (AdjustmentRule rule : ruleBook.rules()) {
            Set<String> requiredDimensions = new HashSet<>();
            Map<String, Set<String>> exactConditionValues = new HashMap<>();
            List<CompiledCondition> compiledConditions = new ArrayList<>();
            for (AdjustmentRuleBook.AdjustmentCondition condition : rule.conditions()) {
                CompiledCondition compiledCondition = conditionCompiler.compile(condition, ruleBook.precisionPolicy());
                compiledConditions.add(compiledCondition);
                requiredDimensions.addAll(compiledCondition.requiredDimensions());
                if (FactMap.SUPPORTED_DIMENSIONS.contains(condition.dimension())
                    && (condition.operator() == AdjustmentRuleBook.ConditionOperator.EQ
                    || condition.operator() == AdjustmentRuleBook.ConditionOperator.IN)) {
                    exactConditionValues.computeIfAbsent(condition.dimension(), ignored -> new HashSet<>())
                        .addAll(condition.configuredValues().stream().map(RuleIndexer::normalizeToken).toList());
                }
            }
            CompiledRule compiledRule = new CompiledRule(rule.ruleId(), rule.priority(), compiledConditions, Set.copyOf(requiredDimensions),
                compileOutput(rule.output(), ruleBook.precisionPolicy()), rule.reasonCode(), rule.exclusivityGroup(),
                rule.enabled(), rule.sourceRef(), rule.minOutput(), rule.maxOutput(), isSingleExactMatchOnly(rule));
            compiledRules.put(rule.ruleId(), compiledRule);
            if (requiredDimensions.isEmpty()) {
                rulesWithNoRequiredDimensions.add(rule.ruleId());
            }
            for (String dimension : requiredDimensions) {
                dimensionIndex.computeIfAbsent(dimension, ignored -> new HashSet<>()).add(rule.ruleId());
                Set<String> exactValues = exactConditionValues.get(dimension);
                if (exactValues == null || exactValues.isEmpty()) {
                    residualDimensionIndex.computeIfAbsent(dimension, ignored -> new HashSet<>()).add(rule.ruleId());
                } else {
                    for (String value : exactValues) {
                        exactDimensionIndex.computeIfAbsent(dimension, ignored -> new HashMap<>())
                            .computeIfAbsent(value, ignored -> new HashSet<>()).add(rule.ruleId());
                    }
                }
            }
        }
        Map<String, Set<UUID>> immutableDimensionIndex = new HashMap<>();
        dimensionIndex.forEach((dimension, ids) -> immutableDimensionIndex.put(dimension, Set.copyOf(ids)));
        List<CompiledRule> orderedEnabledRules = compiledRules.values().stream()
            .filter(CompiledRule::enabled)
            .sorted(RULE_ORDER)
            .toList();
        Map<String, List<CompiledRule>> dimensionRulesByPriority = new HashMap<>();
        dimensionIndex.forEach((dimension, ids) -> dimensionRulesByPriority.put(dimension, orderedRules(ids, compiledRules)));
        Map<String, Map<String, List<CompiledRule>>> exactDimensionRulesByValueByPriority = new HashMap<>();
        exactDimensionIndex.forEach((dimension, valueBuckets) -> {
            Map<String, List<CompiledRule>> orderedBuckets = new HashMap<>();
            valueBuckets.forEach((value, ids) -> orderedBuckets.put(value, orderedRules(ids, compiledRules)));
            exactDimensionRulesByValueByPriority.put(dimension, Map.copyOf(orderedBuckets));
        });
        Map<String, List<CompiledRule>> residualDimensionRulesByPriority = new HashMap<>();
        residualDimensionIndex.forEach((dimension, ids) -> residualDimensionRulesByPriority.put(dimension, orderedRules(ids, compiledRules)));
        List<CompiledRule> rulesWithNoRequiredDimensionsByPriority = orderedEnabledRules.stream()
            .filter(rule -> rulesWithNoRequiredDimensions.contains(rule.ruleId()))
            .toList();
        return new RuleBookIndex(ruleBook.ruleBookId(), ruleBook.version(), ruleBook.contentHash(), Map.copyOf(compiledRules),
            Map.copyOf(immutableDimensionIndex), Set.copyOf(rulesWithNoRequiredDimensions), ruleBook.precisionPolicy(),
            ruleBook.minTotalPointsDelta(), ruleBook.maxTotalPointsDelta(), orderedEnabledRules,
            Map.copyOf(dimensionRulesByPriority), Map.copyOf(exactDimensionRulesByValueByPriority),
            Map.copyOf(residualDimensionRulesByPriority), rulesWithNoRequiredDimensionsByPriority);
    }

    static String normalizeToken(Object value) {
        return value == null ? "" : String.valueOf(value).trim().toLowerCase(Locale.ROOT);
    }

    private CompiledOutput compileOutput(AdjustmentOutput output, PricingPrecisionPolicy policy) {
        BigDecimal amount = output.configuredAmount() == null ? BigDecimal.ZERO : normalizer.normalize(policy, output.type(), output.configuredAmount());
        return new CompiledOutput(output.type(), amount, output.configuredLabel(), policy.pointsScale(), policy.bpsScale(), policy.moneyScale(), policy.roundingMode());
    }

    private List<CompiledRule> orderedRules(Set<UUID> ruleIds, Map<UUID, CompiledRule> compiledRules) {
        return ruleIds.stream()
            .map(compiledRules::get)
            .filter(CompiledRule::enabled)
            .sorted(RULE_ORDER)
            .toList();
    }

    private boolean isSingleExactMatchOnly(AdjustmentRule rule) {
        if (rule.conditions().size() != 1) {
            return false;
        }
        AdjustmentRuleBook.AdjustmentCondition condition = rule.conditions().get(0);
        return FactMap.SUPPORTED_DIMENSIONS.contains(condition.dimension())
            && (condition.operator() == AdjustmentRuleBook.ConditionOperator.EQ
            || condition.operator() == AdjustmentRuleBook.ConditionOperator.IN)
            && !condition.configuredValues().isEmpty();
    }

    public record CompiledRule(UUID ruleId, int priority, List<CompiledCondition> conditions, Set<String> requiredDimensions,
                                CompiledOutput output,
                                String reasonCode, String exclusivityGroup, boolean enabled, String sourceRef,
                                BigDecimal minOutput, BigDecimal maxOutput, boolean singleExactMatchOnly) {}

    public record CompiledOutput(AdjustmentOutputType type, BigDecimal amount, String label, int pointsScale,
                                 int bpsScale, int moneyScale, RoundingMode rounding) {}

    public record RuleBookIndex(UUID ruleBookId, String version, String contentHash, Map<UUID, CompiledRule> rules,
                                  Map<String, Set<UUID>> dimensionIndex, Set<UUID> rulesWithNoRequiredDimensions,
                                  PricingPrecisionPolicy precisionPolicy, BigDecimal minTotalPointsDelta,
                                  BigDecimal maxTotalPointsDelta, List<CompiledRule> orderedEnabledRules,
                                  Map<String, List<CompiledRule>> dimensionRulesByPriority,
                                  Map<String, Map<String, List<CompiledRule>>> exactDimensionRulesByValueByPriority,
                                  Map<String, List<CompiledRule>> residualDimensionRulesByPriority,
                                  List<CompiledRule> rulesWithNoRequiredDimensionsByPriority) {}
}
