package com.wcpe.adjustment;

import static org.assertj.core.api.Assertions.assertThat;

import com.wcpe.adjustment.AdjustmentRuleBook.AdjustmentCondition;
import com.wcpe.adjustment.AdjustmentRuleBook.AdjustmentOutput;
import com.wcpe.adjustment.AdjustmentRuleBook.AdjustmentOutputType;
import com.wcpe.adjustment.AdjustmentRuleBook.AdjustmentRule;
import com.wcpe.adjustment.AdjustmentRuleBook.ConditionOperator;
import com.wcpe.adjustment.AdjustmentRuleBook.EffectiveWindow;
import com.wcpe.adjustment.AdjustmentRuleBook.PricingPrecisionPolicy;
import com.wcpe.adjustment.AdjustmentRuleBook.RuleBookSelector;
import com.wcpe.adjustment.AdjustmentRuleBook.RuleBookStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RuleEvaluationEngineTest {
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000027");
    private static final RuleBookSelector SELECTOR = new RuleBookSelector("CONVENTIONAL", "FNMA", "RETAIL");
    private final RuleEvaluationEngine engine = new RuleEvaluationEngine();

    @Test
    void ficoBandRangeMatch() {
        AdjustmentCalculationResult result = engine.calculate(request(Map.of("representativeFico", 745)),
            book(List.of(rule(1, "representativeFico", ConditionOperator.RANGE_CLOSED, List.of("740", "759"), AdjustmentOutputType.POINTS_DELTA, "-0.125000", "LLPA-FICO"))));

        assertThat(result.adjustments()).singleElement().extracting(AdjustmentLine::factorKey).isEqualTo("LLPA-FICO");
        assertThat(result.totalAdjustment()).isEqualTo(-0.125000d);
    }

    @Test
    void ltvBandRangeMatch() {
        AdjustmentCalculationResult result = engine.calculate(request(Map.of("ltv", "82.50")),
            book(List.of(rule(2, "ltv", ConditionOperator.RANGE_CLOSED, List.of("80.01", "85.00"), AdjustmentOutputType.POINTS_DELTA, "0.250000", "LLPA-LTV"))));

        assertThat(result.totalAdjustment()).isEqualTo(0.250000d);
    }

    @Test
    void cashOutFlagBoolean() {
        AdjustmentCalculationResult result = engine.calculate(request(Map.of("cashOutFlag", true)),
            book(List.of(rule(3, "cashOutFlag", ConditionOperator.BOOLEAN_IS, List.of("true"), AdjustmentOutputType.POINTS_DELTA, "0.375000", "LLPA-CASHOUT"))));

        assertThat(result.adjustments()).singleElement().extracting(AdjustmentLine::applied).isEqualTo(true);
    }

    @Test
    void all58DimensionsSupportExistsEvaluator() {
        List<AdjustmentRule> rules = new ArrayList<>();
        Map<String, Object> facts = new LinkedHashMap<>();
        int index = 10;
        for (String dimension : AdjustmentRuleBook.SUPPORTED_CONDITION_DIMENSIONS.stream().sorted().toList()) {
            facts.put(dimension, dimension + "-configured");
            rules.add(rule(index++, dimension, ConditionOperator.EXISTS, List.of(), AdjustmentOutputType.LABEL_ONLY, null, "DIM-" + dimension));
        }

        AdjustmentCalculationResult result = engine.calculate(request(facts), book(rules));

        assertThat(AdjustmentRuleBook.SUPPORTED_CONDITION_DIMENSIONS).hasSize(58);
        assertThat(result.adjustments()).hasSize(58);
        assertThat(result.adjustments()).allMatch(AdjustmentLine::applied);
    }

    @Test
    void allOutputTypesAreComputedAndAudited() {
        AdjustmentCalculationResult result = engine.calculate(request(Map.of("productFamily", "CONVENTIONAL")), book(List.of(
            rule(101, "productFamily", ConditionOperator.EQ, List.of("CONVENTIONAL"), AdjustmentOutputType.POINTS_DELTA, "0.1250004", "POINTS"),
            rule(102, "productFamily", ConditionOperator.EQ, List.of("CONVENTIONAL"), AdjustmentOutputType.BPS_DELTA, "12.34567", "BPS"),
            rule(103, "productFamily", ConditionOperator.EQ, List.of("CONVENTIONAL"), AdjustmentOutputType.MONEY_FEE, "500.005", "MONEY"),
            rule(104, "productFamily", ConditionOperator.EQ, List.of("CONVENTIONAL"), AdjustmentOutputType.PERCENT_OF_LOAN_AMOUNT, "0.1250004", "PERCENT"),
            labelRule(105, AdjustmentOutputType.LABEL_ONLY, "LPMI Required", "LABEL"),
            labelRule(106, AdjustmentOutputType.BLOCKING_CONFLICT, "Ineligible", "BLOCK")
        )));

        assertThat(result.adjustments()).extracting(AdjustmentLine::outputType)
            .containsExactly("POINTS_DELTA", "BPS_DELTA", "MONEY_FEE", "PERCENT_OF_LOAN_AMOUNT", "LABEL_ONLY", "BLOCKING_CONFLICT");
        assertThat(result.totalsByType()).containsEntry("POINTS_DELTA", new BigDecimal("0.250000"));
        assertThat(result.totalsByType()).containsEntry("BPS_DELTA", new BigDecimal("12.3457"));
        assertThat(result.totalsByType()).containsEntry("MONEY_FEE", new BigDecimal("500.01"));
        assertThat(result.blocked()).isTrue();
    }

    @Test
    void exclusivityGroupOnlyHighestPriority() {
        AdjustmentCalculationResult result = engine.calculate(request(Map.of("state", "TX")), book(List.of(
            exclusiveRule(201, 2, "0.500000", "LLPA-LOWER", "state-group"),
            exclusiveRule(202, 1, "0.250000", "LLPA-HIGHEST", "state-group")
        )));

        assertThat(result.adjustments()).extracting(AdjustmentLine::factorKey).containsExactly("LLPA-HIGHEST", "LLPA-LOWER");
        assertThat(result.adjustments().get(0).applied()).isTrue();
        assertThat(result.adjustments().get(1).applied()).isFalse();
        assertThat(result.totalAdjustment()).isEqualTo(0.250000d);
    }

    @Test
    void capFloorApplied() {
        AdjustmentRule cappedRule = new AdjustmentRule(UUID.fromString("00000000-0000-0000-0000-000000000301"), 1,
            List.of(new AdjustmentCondition("loanPurpose", ConditionOperator.EQ, List.of("CASH_OUT_REFI"))),
            new AdjustmentOutput(AdjustmentOutputType.POINTS_DELTA, new BigDecimal("2.000000"), null),
            "RULE-CAP", null, true, "source", null, new BigDecimal("1.000000"));
        AdjustmentRuleBook book = book(List.of(cappedRule), new BigDecimal("0.750000"), new BigDecimal("-0.750000"));

        AdjustmentCalculationResult result = engine.calculate(request(Map.of("loanPurpose", "CASH_OUT_REFI")), book);

        assertThat(result.adjustments().get(0).warnings()).contains("cap_applied:1.000000");
        assertThat(result.adjustments()).extracting(AdjustmentLine::factorKey).contains("RULE_BOOK_CAP_FLOOR");
        assertThat(result.totalAdjustment()).isEqualTo(0.750000d);
    }

    @Test
    void precisionPolicyEnforced() {
        AdjustmentCalculationResult result = engine.calculate(request(Map.of("channel", "RETAIL")),
            book(List.of(rule(401, "channel", ConditionOperator.EQ, List.of("RETAIL"), AdjustmentOutputType.POINTS_DELTA, "0.1234567", "PRECISION"))));

        assertThat(result.totalAdjustment()).isEqualTo(0.123457d);
    }

    @Test
    void blockingConflictStopsPricing() {
        AdjustmentCalculationResult result = engine.calculate(request(Map.of("representativeFico", 610)),
            book(List.of(labelRule(501, AdjustmentOutputType.BLOCKING_CONFLICT, "FICO below configured minimum", "INELIGIBLE"))));

        assertThat(result.blocked()).isTrue();
        assertThat(result.adjustments()).singleElement().extracting(AdjustmentLine::outputType).isEqualTo("BLOCKING_CONFLICT");
    }

    @Test
    void deterministicResultHash() {
        AdjustmentRuleBook book = book(List.of(rule(601, "county", ConditionOperator.IN, List.of("HARRIS", "TRAVIS"), AdjustmentOutputType.POINTS_DELTA, "0.100000", "COUNTY")));
        AdjustmentCalculationRequest request = request(Map.of("county", "HARRIS"));

        assertThat(engine.calculate(request, book).resultHash()).isEqualTo(engine.calculate(request, book).resultHash());
    }

    @Test
    void missingFactFailsClosed() {
        AdjustmentCalculationResult result = engine.calculate(request(Map.of()),
            book(List.of(rule(701, "subordinateFinancingFlag", ConditionOperator.BOOLEAN_IS, List.of("true"), AdjustmentOutputType.POINTS_DELTA, "0.125000", "MISSING"))));

        assertThat(result.blocked()).isTrue();
        assertThat(result.adjustments()).singleElement().extracting(AdjustmentLine::reason).asString().contains("missing_fact:subordinateFinancingFlag");
    }

    @Test
    void openEndedRangeAndNotExistsConditionsMatch() {
        AdjustmentCalculationResult result = engine.calculate(request(Map.of("loanAmount", 650000)), book(List.of(
            rule(801, "loanAmount", ConditionOperator.RANGE_OPEN_END, List.of("500000"), AdjustmentOutputType.POINTS_DELTA, "0.050000", "OPEN-RANGE"),
            rule(802, "escrowWaiverFlag", ConditionOperator.NOT_EXISTS, List.of(), AdjustmentOutputType.POINTS_DELTA, "0.025000", "NOT-EXISTS")
        )));

        assertThat(result.adjustments()).extracting(AdjustmentLine::factorKey).containsExactly("OPEN-RANGE", "NOT-EXISTS");
        assertThat(result.totalAdjustment()).isEqualTo(0.075000d);
    }

    private static AdjustmentCalculationRequest request(Map<String, Object> facts) {
        return new AdjustmentCalculationRequest(new BasePriceDecisionStub("SCN-027", "BP-027", "fixed-rate-30yr", 450000.0, "USD", "pricing-service"),
            Map.of(), List.of(), "rulebook-2026.06.v3", TENANT_ID, SELECTOR, Instant.parse("2026-06-12T00:00:00Z"), facts);
    }

    private static AdjustmentRuleBook book(List<AdjustmentRule> rules) {
        return book(rules, null, null);
    }

    private static AdjustmentRuleBook book(List<AdjustmentRule> rules, BigDecimal maxTotal, BigDecimal minTotal) {
        return new AdjustmentRuleBook(TENANT_ID, UUID.fromString("20000000-0000-0000-0000-000000000027"), "llpa-real-engine",
            "rulebook-2026.06.v3", RuleBookStatus.PUBLISHED, SELECTOR, new EffectiveWindow(Instant.parse("2026-01-01T00:00:00Z"), null),
            new PricingPrecisionPolicy(6, 4, 2, RoundingMode.HALF_UP), rules, "author", "approver",
            Instant.parse("2026-06-01T00:00:00Z"), null, maxTotal, minTotal);
    }

    private static AdjustmentRule rule(int suffix, String dimension, ConditionOperator operator, List<String> values, AdjustmentOutputType type, String amount, String reason) {
        AdjustmentOutput output = (type == AdjustmentOutputType.LABEL_ONLY || type == AdjustmentOutputType.BLOCKING_CONFLICT)
            ? new AdjustmentOutput(type, null, reason)
            : new AdjustmentOutput(type, amount == null ? null : new BigDecimal(amount), null);
        return new AdjustmentRule(id(suffix), 1,
            List.of(new AdjustmentCondition(dimension, operator, values)),
            output, reason, null, true, "source:" + reason);
    }

    private static AdjustmentRule labelRule(int suffix, AdjustmentOutputType type, String label, String reason) {
        return new AdjustmentRule(id(suffix), suffix,
            List.of(new AdjustmentCondition("productFamily", ConditionOperator.EQ, List.of("CONVENTIONAL"))),
            new AdjustmentOutput(type, null, label), reason, null, true, "source:" + reason);
    }

    private static AdjustmentRule exclusiveRule(int suffix, int priority, String amount, String reason, String group) {
        return new AdjustmentRule(id(suffix), priority,
            List.of(new AdjustmentCondition("state", ConditionOperator.EQ, List.of("TX"))),
            new AdjustmentOutput(AdjustmentOutputType.POINTS_DELTA, new BigDecimal(amount), null), reason, group, true, "source:" + reason);
    }

    private static UUID id(int suffix) {
        return UUID.fromString("00000000-0000-0000-0000-" + String.format("%012d", suffix));
    }
}
