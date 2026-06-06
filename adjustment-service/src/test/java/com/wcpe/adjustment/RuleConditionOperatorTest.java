package com.wcpe.adjustment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.wcpe.adjustment.AdjustmentRuleBook.AdjustmentCondition;
import com.wcpe.adjustment.AdjustmentRuleBook.ConditionOperator;
import java.util.List;
import org.junit.jupiter.api.Test;

class RuleConditionOperatorTest {
    private static final String SUPPORTED_DIMENSION = "productFamily";

    @Test
    void eqOperatorRequiresConfiguredValues() {
        AdjustmentCondition condition = condition(ConditionOperator.EQ, "CONVENTIONAL");

        assertThat(condition.validate()).isEmpty();
    }

    @Test
    void eqOperatorRejectsEmptyValues() {
        AdjustmentCondition condition = condition(ConditionOperator.EQ);

        assertThat(condition.validate())
            .containsExactly("condition productFamily requires configured values");
    }

    @Test
    void inOperatorRequiresConfiguredValues() {
        AdjustmentCondition condition = condition(ConditionOperator.IN, "CONVENTIONAL", "FHA");

        assertThat(condition.validate()).isEmpty();
    }

    @Test
    void rangeClosedRequiresTwoValues() {
        AdjustmentCondition validCondition = condition(ConditionOperator.RANGE_CLOSED, "CONFIGURED_MIN", "CONFIGURED_MAX");
        AdjustmentCondition invalidCondition = condition(ConditionOperator.RANGE_CLOSED);

        assertThat(validCondition.validate()).isEmpty();
        assertThat(invalidCondition.validate())
            .containsExactly("condition productFamily requires configured values");
    }

    @Test
    void rangeOpenEndRequiresValues() {
        AdjustmentCondition validCondition = condition(ConditionOperator.RANGE_OPEN_END, "CONFIGURED_MIN");
        AdjustmentCondition invalidCondition = condition(ConditionOperator.RANGE_OPEN_END);

        assertThat(validCondition.validate()).isEmpty();
        assertThat(invalidCondition.validate())
            .containsExactly("condition productFamily requires configured values");
    }

    @Test
    void existsOperatorRequiresNoValues() {
        AdjustmentCondition condition = condition(ConditionOperator.EXISTS);

        assertThat(condition.validate()).isEmpty();
    }

    @Test
    void notExistsOperatorRequiresNoValues() {
        AdjustmentCondition condition = condition(ConditionOperator.NOT_EXISTS);

        assertThat(condition.validate()).isEmpty();
    }

    @Test
    void booleanIsRequiresValues() {
        AdjustmentCondition validCondition = condition(ConditionOperator.BOOLEAN_IS, "true");
        AdjustmentCondition invalidCondition = condition(ConditionOperator.BOOLEAN_IS);

        assertThat(validCondition.validate()).isEmpty();
        assertThat(invalidCondition.validate())
            .containsExactly("condition productFamily requires configured values");
    }

    @Test
    void unknownDimensionFailsValidation() {
        AdjustmentCondition condition = new AdjustmentCondition("unsupportedDimension", ConditionOperator.EXISTS, List.of());

        assertThat(condition.validate())
            .containsExactly("unknown condition dimension: unsupportedDimension");
    }

    @Test
    void allSupportedDimensionsAreAccepted() {
        assertThat(AdjustmentRuleBook.SUPPORTED_CONDITION_DIMENSIONS)
            .allSatisfy(dimension -> assertThat(new AdjustmentCondition(dimension, ConditionOperator.EXISTS, List.of()).validate())
                .isEmpty());
    }

    @Test
    void conditionValidationReturnsErrorListNotException() {
        AdjustmentCondition condition = new AdjustmentCondition("unsupportedDimension", ConditionOperator.EQ, List.of());

        assertThatCode(condition::validate).doesNotThrowAnyException();
        assertThat(condition.validate())
            .containsExactly(
                "unknown condition dimension: unsupportedDimension",
                "condition unsupportedDimension requires configured values"
            );
    }

    private static AdjustmentCondition condition(ConditionOperator operator, String... configuredValues) {
        return new AdjustmentCondition(SUPPORTED_DIMENSION, operator, List.of(configuredValues));
    }
}
