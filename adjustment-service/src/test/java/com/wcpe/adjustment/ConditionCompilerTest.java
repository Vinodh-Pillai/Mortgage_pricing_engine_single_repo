package com.wcpe.adjustment;

import static org.assertj.core.api.Assertions.assertThat;

import com.wcpe.adjustment.AdjustmentRuleBook.AdjustmentCondition;
import com.wcpe.adjustment.AdjustmentRuleBook.ConditionOperator;
import com.wcpe.adjustment.AdjustmentRuleBook.PricingPrecisionPolicy;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConditionCompilerTest {
    private final ConditionCompiler compiler = new ConditionCompiler();
    private final PricingPrecisionPolicy policy = new PricingPrecisionPolicy(6, 4, 2, RoundingMode.HALF_UP);

    @Test
    void allOperatorsCompile() {
        FactMap facts = new FactMap(Map.of(
            "state", "TX",
            "county", "HARRIS",
            "ltv", "82.50",
            "loanAmount", "650000",
            "cashOutFlag", "Y",
            "productFamily", "CONVENTIONAL"
        ));

        assertThat(evaluate("state", ConditionOperator.EQ, List.of("TX"), facts)).isTrue();
        assertThat(evaluate("county", ConditionOperator.IN, List.of("HARRIS", "TRAVIS"), facts)).isTrue();
        assertThat(evaluate("ltv", ConditionOperator.RANGE_CLOSED, List.of("80.01", "85.00"), facts)).isTrue();
        assertThat(evaluate("loanAmount", ConditionOperator.RANGE_OPEN_END, List.of("500000"), facts)).isTrue();
        assertThat(evaluate("productFamily", ConditionOperator.EXISTS, List.of(), facts)).isTrue();
        assertThat(evaluate("escrowWaiverFlag", ConditionOperator.NOT_EXISTS, List.of(), facts)).isTrue();
        assertThat(evaluate("cashOutFlag", ConditionOperator.BOOLEAN_IS, List.of("true"), facts)).isTrue();
    }

    private boolean evaluate(String dimension, ConditionOperator operator, List<String> values, FactMap facts) {
        CompiledCondition.ConditionEvaluation result = compiler.compile(new AdjustmentCondition(dimension, operator, values), policy).evaluate(facts);
        assertThat(result.blocked()).isFalse();
        return result.matched();
    }
}
