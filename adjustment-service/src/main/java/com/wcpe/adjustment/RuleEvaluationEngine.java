package com.wcpe.adjustment;

import com.wcpe.adjustment.AdjustmentRuleBook.AdjustmentOutputType;
import com.wcpe.adjustment.AdjustmentRuleBook.PricingPrecisionPolicy;
import com.wcpe.adjustment.RuleIndexer.CompiledRule;
import com.wcpe.adjustment.RuleIndexer.RuleBookIndex;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Priority-ordered real LLPA rule evaluator backed by compiled conditions and a dimension index. */
@Service
public final class RuleEvaluationEngine {
    private static final Comparator<CompiledRule> RULE_ORDER = Comparator
        .comparingInt(CompiledRule::priority)
        .thenComparing(CompiledRule::ruleId);

    private final RuleIndexer indexer;
    private final PrecisionNormalizer normalizer;

    public RuleEvaluationEngine() {
        this(new RuleIndexer(), new PrecisionNormalizer());
    }

    public RuleEvaluationEngine(RuleIndexer indexer, PrecisionNormalizer normalizer) {
        this.indexer = Objects.requireNonNull(indexer, "indexer is required");
        this.normalizer = Objects.requireNonNull(normalizer, "normalizer is required");
    }

    public AdjustmentCalculationResult calculate(AdjustmentCalculationRequest request, AdjustmentRuleBook ruleBook) {
        return evaluate(indexer.index(ruleBook), FactMap.from(request), request.basePriceDecision().scenarioId(), request.basePriceDecision().basePriceId());
    }

    public AdjustmentCalculationResult evaluate(RuleBookIndex index, FactMap facts, String scenarioId, String basePriceId) {
        Objects.requireNonNull(index, "rule book index is required");
        Objects.requireNonNull(facts, "facts are required");
        PricingPrecisionPolicy policy = index.precisionPolicy();
        List<AdjustmentLine> lines = new ArrayList<>();
        Set<UUID> alreadyBlocked = new HashSet<>();

        for (CompiledRule rule : missingFactRules(index, facts)) {
            lines.add(blockedLine(rule, index, firstMissingReason(rule, facts)));
            alreadyBlocked.add(rule.ruleId());
        }

        List<CompiledRule> candidates = candidateRules(index, facts, alreadyBlocked);

        Map<String, UUID> exclusiveWinners = new LinkedHashMap<>();
        List<RuleEvaluation> matchedEvaluations = new ArrayList<>();
        for (CompiledRule rule : candidates) {
            RuleEvaluation evaluation = evaluateRule(rule, facts, policy);
            if (evaluation.blocked()) {
                lines.add(blockedLine(rule, index, evaluation.blockReason()));
                alreadyBlocked.add(rule.ruleId());
                continue;
            }
            if (!evaluation.matched()) {
                continue;
            }
            if (rule.exclusivityGroup() != null && !rule.exclusivityGroup().isBlank()) {
                exclusiveWinners.putIfAbsent(rule.exclusivityGroup(), rule.ruleId());
            }
            matchedEvaluations.add(evaluation);
        }

        BigDecimal pointsTotal = BigDecimal.ZERO.setScale(policy.pointsScale(), policy.roundingMode());
        BigDecimal bpsTotal = BigDecimal.ZERO.setScale(policy.bpsScale(), policy.roundingMode());
        BigDecimal moneyTotal = BigDecimal.ZERO.setScale(policy.moneyScale(), policy.roundingMode());
        int labelCount = 0;
        boolean blocked = !alreadyBlocked.isEmpty();

        for (RuleEvaluation evaluation : matchedEvaluations) {
            CompiledRule rule = evaluation.rule();
            boolean applied = true;
            List<String> warnings = new ArrayList<>(evaluation.warnings());
            if (rule.exclusivityGroup() != null && !rule.exclusivityGroup().isBlank()
                && !exclusiveWinners.get(rule.exclusivityGroup()).equals(rule.ruleId())) {
                applied = false;
                warnings.add("suppressed_by_exclusivity_group:" + rule.exclusivityGroup());
            }
            BigDecimal amount = applied ? evaluation.amount() : BigDecimal.ZERO;
            if (applied) {
                switch (rule.output().type()) {
                    case POINTS_DELTA, PERCENT_OF_LOAN_AMOUNT -> pointsTotal = normalizer.normalize(policy, AdjustmentOutputType.POINTS_DELTA, pointsTotal.add(amount));
                    case BPS_DELTA -> bpsTotal = normalizer.normalize(policy, AdjustmentOutputType.BPS_DELTA, bpsTotal.add(amount));
                    case MONEY_FEE -> moneyTotal = normalizer.normalize(policy, AdjustmentOutputType.MONEY_FEE, moneyTotal.add(amount));
                    case LABEL_ONLY -> labelCount++;
                    case BLOCKING_CONFLICT -> blocked = true;
                }
            }
            lines.add(toLine(rule, index, amount, applied, warnings));
        }

        CapResult totalCap = applyCapFloor(pointsTotal, index.minTotalPointsDelta(), index.maxTotalPointsDelta(), policy, AdjustmentOutputType.POINTS_DELTA);
        if (!totalCap.warnings().isEmpty()) {
            lines.add(new AdjustmentLine("RULE_BOOK_CAP_FLOOR", totalCap.amount().doubleValue(), "RULE_BOOK_CAP_FLOOR",
                index.version(), "POINTS_DELTA", index.ruleBookId().toString(), "rulebook:" + index.version(), true,
                null, auditRef(index, null), totalCap.warnings()));
        }

        Map<String, Object> totalsByType = new LinkedHashMap<>();
        totalsByType.put("POINTS_DELTA", totalCap.amount());
        totalsByType.put("BPS_DELTA", bpsTotal);
        totalsByType.put("MONEY_FEE", moneyTotal);
        totalsByType.put("LABEL_ONLY", labelCount);
        List<String> auditRefs = lines.stream().map(AdjustmentLine::auditRef).filter(ref -> ref != null && !ref.isBlank()).distinct().toList();
        String resultHash = hash(index.contentHash(), scenarioId, basePriceId, new TreeMap<>(facts.asMap()), hashLines(lines), totalsByType, blocked);
        return new AdjustmentCalculationResult(scenarioId, basePriceId, lines, totalCap.amount().doubleValue(),
            index.version(), "real-rule-book", auditRefs, resultHash, totalsByType, blocked);
    }

    private List<CompiledRule> missingFactRules(RuleBookIndex index, FactMap facts) {
        if (index.dimensionRulesByPriority().isEmpty()) {
            return List.of();
        }
        List<CompiledRule> missingRules = new ArrayList<>();
        Set<UUID> added = new HashSet<>();
        for (Map.Entry<String, List<CompiledRule>> entry : index.dimensionRulesByPriority().entrySet()) {
            if (facts.exists(entry.getKey())) {
                continue;
            }
            for (CompiledRule rule : entry.getValue()) {
                if (added.add(rule.ruleId())) {
                    missingRules.add(rule);
                }
            }
        }
        if (missingRules.size() > 1) {
            missingRules.sort(RULE_ORDER);
        }
        return missingRules;
    }

    private String firstMissingReason(CompiledRule rule, FactMap facts) {
        return rule.conditions().stream()
            .flatMap(condition -> condition.requiredDimensions().stream())
            .filter(dimension -> !facts.exists(dimension))
            .findFirst()
            .map(dimension -> "missing_fact:" + dimension)
            .orElse("missing_fact");
    }

    private List<CompiledRule> candidateRules(RuleBookIndex index, FactMap facts, Set<UUID> alreadyBlocked) {
        List<CompiledRule> noRequiredRules = index.rulesWithNoRequiredDimensionsByPriority();
        if (noRequiredRules.isEmpty() && facts.asMap().size() == 1) {
            String dimension = facts.asMap().keySet().iterator().next();
            List<CompiledRule> singleDimensionRules = index.dimensionRulesByPriority().get(dimension);
            if (singleDimensionRules == null || singleDimensionRules.isEmpty()) {
                return List.of();
            }
            if (alreadyBlocked.isEmpty()) {
                return singleDimensionRules;
            }
            return singleDimensionRules.stream()
                .filter(rule -> !alreadyBlocked.contains(rule.ruleId()))
                .toList();
        }

        Set<UUID> candidates = new HashSet<>();
        for (CompiledRule rule : noRequiredRules) {
            candidates.add(rule.ruleId());
        }
        for (String dimension : facts.asMap().keySet()) {
            List<CompiledRule> rules = index.dimensionRulesByPriority().get(dimension);
            if (rules != null) {
                for (CompiledRule rule : rules) {
                    candidates.add(rule.ruleId());
                }
            }
        }
        List<CompiledRule> orderedCandidates = new ArrayList<>(candidates.size());
        for (CompiledRule rule : index.orderedEnabledRules()) {
            if (candidates.contains(rule.ruleId()) && !alreadyBlocked.contains(rule.ruleId())) {
                orderedCandidates.add(rule);
            }
        }
        return orderedCandidates;
    }

    private RuleEvaluation evaluateRule(CompiledRule rule, FactMap facts, PricingPrecisionPolicy policy) {
        for (CompiledCondition condition : rule.conditions()) {
            CompiledCondition.ConditionEvaluation result = condition.evaluate(facts);
            if (result.blocked()) {
                return new RuleEvaluation(rule, false, true, result.reason(), BigDecimal.ZERO, List.of());
            }
            if (!result.matched()) {
                return new RuleEvaluation(rule, false, false, "", BigDecimal.ZERO, List.of());
            }
        }
        CapResult amount = outputAmount(rule, policy);
        CapResult capped = applyCapFloor(amount.amount(), rule.minOutput(), rule.maxOutput(), policy, rule.output().type());
        List<String> warnings = new ArrayList<>(amount.warnings());
        warnings.addAll(capped.warnings());
        return new RuleEvaluation(rule, true, false, "", capped.amount(), warnings);
    }

    private CapResult outputAmount(CompiledRule rule, PricingPrecisionPolicy policy) {
        if (rule.output().type() == AdjustmentOutputType.LABEL_ONLY || rule.output().type() == AdjustmentOutputType.BLOCKING_CONFLICT) {
            return new CapResult(BigDecimal.ZERO, List.of());
        }
        return new CapResult(normalizer.normalize(policy, rule.output().type(), rule.output().amount()), List.of());
    }

    private CapResult applyCapFloor(BigDecimal raw, BigDecimal min, BigDecimal max, PricingPrecisionPolicy policy, AdjustmentOutputType type) {
        BigDecimal normalized = normalizer.normalize(policy, type, raw);
        List<String> warnings = new ArrayList<>();
        if (max != null && normalized.compareTo(max) > 0) {
            normalized = normalizer.normalize(policy, type, max);
            warnings.add("cap_applied:" + max);
        }
        if (min != null && normalized.compareTo(min) < 0) {
            normalized = normalizer.normalize(policy, type, min);
            warnings.add("floor_applied:" + min);
        }
        return new CapResult(normalized, warnings);
    }

    private AdjustmentLine toLine(CompiledRule rule, RuleBookIndex index, BigDecimal amount, boolean applied, List<String> warnings) {
        return new AdjustmentLine(rule.reasonCode(), amount.doubleValue(), rule.reasonCode(), applied ? index.version() : "suppressed",
            rule.output().type().name(), rule.ruleId().toString(), rule.sourceRef(), applied, rule.output().label(), auditRef(index, rule), warnings);
    }

    private AdjustmentLine blockedLine(CompiledRule rule, RuleBookIndex index, String reason) {
        return new AdjustmentLine(rule.reasonCode(), 0.0, reason, "blocked", "BLOCKING_CONFLICT",
            rule.ruleId().toString(), rule.sourceRef(), false, reason, auditRef(index, rule), List.of(reason));
    }

    private String auditRef(RuleBookIndex index, CompiledRule rule) {
        return "rulebook:" + index.version() + ":" + index.ruleBookId() + (rule == null ? "" : ":rule:" + rule.ruleId());
    }

    private static String hash(Object... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Object value : values) {
                digest.update(String.valueOf(value).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static String hashLines(List<AdjustmentLine> lines) {
        StringBuilder builder = new StringBuilder(lines.size() * 64);
        for (AdjustmentLine line : lines) {
            builder.append(line.ruleId()).append('|')
                .append(line.factorKey()).append('|')
                .append(line.amount()).append('|')
                .append(line.outputType()).append('|')
                .append(line.applied()).append(';');
        }
        return builder.toString();
    }

    private record RuleEvaluation(CompiledRule rule, boolean matched, boolean blocked, String blockReason, BigDecimal amount, List<String> warnings) {}
    private record CapResult(BigDecimal amount, List<String> warnings) {}
}
