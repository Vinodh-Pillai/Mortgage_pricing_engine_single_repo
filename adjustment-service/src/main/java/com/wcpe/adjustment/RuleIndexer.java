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
import java.util.List;
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
        Map<UUID, CompiledRule> compiledRules = new HashMap<>();
        Set<UUID> rulesWithNoRequiredDimensions = new HashSet<>();

        for (AdjustmentRule rule : ruleBook.rules()) {
            Set<String> requiredDimensions = new HashSet<>();
            List<CompiledCondition> compiledConditions = rule.conditions().stream()
                .map(condition -> conditionCompiler.compile(condition, ruleBook.precisionPolicy()))
                .peek(condition -> requiredDimensions.addAll(condition.requiredDimensions()))
                .toList();
            CompiledRule compiledRule = new CompiledRule(rule.ruleId(), rule.priority(), compiledConditions,
                compileOutput(rule.output(), ruleBook.precisionPolicy()), rule.reasonCode(), rule.exclusivityGroup(),
                rule.enabled(), rule.sourceRef(), rule.minOutput(), rule.maxOutput());
            compiledRules.put(rule.ruleId(), compiledRule);
            if (requiredDimensions.isEmpty()) {
                rulesWithNoRequiredDimensions.add(rule.ruleId());
            }
            for (String dimension : requiredDimensions) {
                dimensionIndex.computeIfAbsent(dimension, ignored -> new HashSet<>()).add(rule.ruleId());
            }
        }
        Map<String, Set<UUID>> immutableDimensionIndex = new HashMap<>();
        dimensionIndex.forEach((dimension, ids) -> immutableDimensionIndex.put(dimension, Set.copyOf(ids)));
        List<CompiledRule> orderedEnabledRules = compiledRules.values().stream()
            .filter(CompiledRule::enabled)
            .sorted(RULE_ORDER)
            .toList();
        Map<String, List<CompiledRule>> dimensionRulesByPriority = new HashMap<>();
        dimensionIndex.forEach((dimension, ids) -> dimensionRulesByPriority.put(dimension, orderedEnabledRules.stream()
            .filter(rule -> ids.contains(rule.ruleId()))
            .toList()));
        List<CompiledRule> rulesWithNoRequiredDimensionsByPriority = orderedEnabledRules.stream()
            .filter(rule -> rulesWithNoRequiredDimensions.contains(rule.ruleId()))
            .toList();
        return new RuleBookIndex(ruleBook.ruleBookId(), ruleBook.version(), ruleBook.contentHash(), Map.copyOf(compiledRules),
            Map.copyOf(immutableDimensionIndex), Set.copyOf(rulesWithNoRequiredDimensions), ruleBook.precisionPolicy(),
            ruleBook.minTotalPointsDelta(), ruleBook.maxTotalPointsDelta(), orderedEnabledRules,
            Map.copyOf(dimensionRulesByPriority), rulesWithNoRequiredDimensionsByPriority);
    }

    private CompiledOutput compileOutput(AdjustmentOutput output, PricingPrecisionPolicy policy) {
        BigDecimal amount = output.configuredAmount() == null ? BigDecimal.ZERO : normalizer.normalize(policy, output.type(), output.configuredAmount());
        return new CompiledOutput(output.type(), amount, output.configuredLabel(), policy.pointsScale(), policy.bpsScale(), policy.moneyScale(), policy.roundingMode());
    }

    public record CompiledRule(UUID ruleId, int priority, List<CompiledCondition> conditions, CompiledOutput output,
                               String reasonCode, String exclusivityGroup, boolean enabled, String sourceRef,
                               BigDecimal minOutput, BigDecimal maxOutput) {}

    public record CompiledOutput(AdjustmentOutputType type, BigDecimal amount, String label, int pointsScale,
                                 int bpsScale, int moneyScale, RoundingMode rounding) {}

    public record RuleBookIndex(UUID ruleBookId, String version, String contentHash, Map<UUID, CompiledRule> rules,
                                 Map<String, Set<UUID>> dimensionIndex, Set<UUID> rulesWithNoRequiredDimensions,
                                 PricingPrecisionPolicy precisionPolicy, BigDecimal minTotalPointsDelta,
                                 BigDecimal maxTotalPointsDelta, List<CompiledRule> orderedEnabledRules,
                                 Map<String, List<CompiledRule>> dimensionRulesByPriority,
                                 List<CompiledRule> rulesWithNoRequiredDimensionsByPriority) {}
}
