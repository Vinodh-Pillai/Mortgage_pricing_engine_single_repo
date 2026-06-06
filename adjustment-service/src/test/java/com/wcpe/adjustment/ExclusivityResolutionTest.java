package com.wcpe.adjustment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wcpe.adjustment.AdjustmentRuleBook.AdjustmentOutput;
import com.wcpe.adjustment.AdjustmentRuleBook.AdjustmentOutputType;
import com.wcpe.adjustment.AdjustmentRuleBook.AdjustmentRule;
import com.wcpe.adjustment.ExclusivityResolution.GroupStrategy;
import com.wcpe.adjustment.ExclusivityResolution.ResolutionResult;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExclusivityResolutionTest {

    @Test
    void highestCostSelectsRuleWithLargestOutputAmount() {
        AdjustmentRule lowerCost = pointsRule("30000000-0000-0000-0000-000000000001", "0.125000", 1, "CONFIGURED_GROUP");
        AdjustmentRule higherCost = pointsRule("30000000-0000-0000-0000-000000000002", "0.375000", 2, "CONFIGURED_GROUP");

        ResolutionResult result = ExclusivityResolution.resolve(List.of(lowerCost, higherCost), GroupStrategy.HIGHEST_COST);

        assertThat(result.winningRule()).isEqualTo(higherCost);
        assertThat(result.suppressedRuleIds()).containsExactly(lowerCost.ruleId());
    }

    @Test
    void lowestCostSelectsRuleWithSmallestOutputAmount() {
        AdjustmentRule lowerCost = pointsRule("30000000-0000-0000-0000-000000000003", "0.125000", 3, "CONFIGURED_GROUP");
        AdjustmentRule higherCost = pointsRule("30000000-0000-0000-0000-000000000004", "0.375000", 1, "CONFIGURED_GROUP");

        ResolutionResult result = ExclusivityResolution.resolve(List.of(higherCost, lowerCost), GroupStrategy.LOWEST_COST);

        assertThat(result.winningRule()).isEqualTo(lowerCost);
        assertThat(result.suppressedRuleIds()).containsExactly(higherCost.ruleId());
    }

    @Test
    void firstPrioritySelectsLowestPriorityNumber() {
        AdjustmentRule priorityOne = pointsRule("30000000-0000-0000-0000-000000000005", "0.125000", 1, "CONFIGURED_GROUP");
        AdjustmentRule priorityThree = pointsRule("30000000-0000-0000-0000-000000000006", "0.500000", 3, "CONFIGURED_GROUP");

        ResolutionResult result = ExclusivityResolution.resolve(List.of(priorityThree, priorityOne), GroupStrategy.FIRST_PRIORITY);

        assertThat(result.winningRule()).isEqualTo(priorityOne);
        assertThat(result.suppressedRuleIds()).containsExactly(priorityThree.ruleId());
    }

    @Test
    void failOnMultipleThrowsWhenTwoRulesMatch() {
        AdjustmentRule first = pointsRule("30000000-0000-0000-0000-000000000007", "0.125000", 1, "CONFIGURED_GROUP");
        AdjustmentRule second = pointsRule("30000000-0000-0000-0000-000000000008", "0.250000", 2, "CONFIGURED_GROUP");

        assertThatThrownBy(() -> ExclusivityResolution.resolve(List.of(first, second), GroupStrategy.FAIL_ON_MULTIPLE))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("multiple matching rules");
    }

    @Test
    void differentExclusivityGroupsDontConflict() {
        AdjustmentRule firstGroup = pointsRule("30000000-0000-0000-0000-000000000009", "0.125000", 1, "CONFIGURED_GROUP_A");
        AdjustmentRule secondGroup = pointsRule("30000000-0000-0000-0000-000000000010", "0.250000", 1, "CONFIGURED_GROUP_B");

        List<ResolutionResult> results = ExclusivityResolution.resolveAll(List.of(firstGroup, secondGroup), GroupStrategy.FAIL_ON_MULTIPLE);

        assertThat(results).extracting(ResolutionResult::winningRule).containsExactly(firstGroup, secondGroup);
        assertThat(results).allSatisfy(result -> assertThat(result.suppressedRuleIds()).isEmpty());
    }

    @Test
    void nullExclusivityGroupMeansNoConflict() {
        AdjustmentRule first = pointsRule("30000000-0000-0000-0000-000000000011", "0.125000", 1, null);
        AdjustmentRule second = pointsRule("30000000-0000-0000-0000-000000000012", "0.250000", 1, null);

        List<ResolutionResult> results = ExclusivityResolution.resolveAll(List.of(first, second), GroupStrategy.FAIL_ON_MULTIPLE);

        assertThat(results).extracting(ResolutionResult::winningRule).containsExactly(first, second);
        assertThat(results).allSatisfy(result -> assertThat(result.suppressedRuleIds()).isEmpty());
    }

    @Test
    void highestCostWithBlockingConflictOutputUsesPriorityAsTiebreaker() {
        AdjustmentRule priorityOne = blockingRule("30000000-0000-0000-0000-000000000013", 1, "CONFIGURED_GROUP");
        AdjustmentRule priorityThree = blockingRule("30000000-0000-0000-0000-000000000014", 3, "CONFIGURED_GROUP");

        ResolutionResult result = ExclusivityResolution.resolve(List.of(priorityThree, priorityOne), GroupStrategy.HIGHEST_COST);

        assertThat(result.winningRule()).isEqualTo(priorityOne);
        assertThat(result.suppressedRuleIds()).containsExactly(priorityThree.ruleId());
    }

    @Test
    void matchedRulesRecordSuppressedRuleIds() {
        AdjustmentRule selected = pointsRule("30000000-0000-0000-0000-000000000015", "0.375000", 1, "CONFIGURED_GROUP");
        AdjustmentRule suppressedLow = pointsRule("30000000-0000-0000-0000-000000000016", "0.125000", 2, "CONFIGURED_GROUP");
        AdjustmentRule suppressedMid = pointsRule("30000000-0000-0000-0000-000000000017", "0.250000", 3, "CONFIGURED_GROUP");

        ResolutionResult result = ExclusivityResolution.resolve(
            List.of(suppressedLow, selected, suppressedMid),
            GroupStrategy.HIGHEST_COST
        );

        assertThat(result.winningRule()).isEqualTo(selected);
        assertThat(result.suppressedRuleIds()).containsExactly(suppressedLow.ruleId(), suppressedMid.ruleId());
    }

    @Test
    void emptyRuleListReturnsEmptyResult() {
        ResolutionResult result = ExclusivityResolution.resolve(List.of(), GroupStrategy.HIGHEST_COST);

        assertThat(result.winningRule()).isNull();
        assertThat(result.suppressedRuleIds()).isEmpty();
        assertThat(ExclusivityResolution.resolveAll(List.of(), GroupStrategy.HIGHEST_COST)).isEmpty();
    }

    @Test
    void singleRuleInGroupReturnsThatRule() {
        AdjustmentRule rule = pointsRule("30000000-0000-0000-0000-000000000018", "0.125000", 1, "CONFIGURED_GROUP");

        ResolutionResult result = ExclusivityResolution.resolve(List.of(rule), GroupStrategy.FAIL_ON_MULTIPLE);

        assertThat(result.winningRule()).isEqualTo(rule);
        assertThat(result.suppressedRuleIds()).isEmpty();
    }

    private static AdjustmentRule pointsRule(String ruleId, String amount, int priority, String exclusivityGroup) {
        return new AdjustmentRule(
            UUID.fromString(ruleId),
            priority,
            List.of(),
            new AdjustmentOutput(AdjustmentOutputType.POINTS_DELTA, new BigDecimal(amount), null),
            "CONFIGURED_REASON",
            exclusivityGroup,
            true,
            "configured-source-ref"
        );
    }

    private static AdjustmentRule blockingRule(String ruleId, int priority, String exclusivityGroup) {
        return new AdjustmentRule(
            UUID.fromString(ruleId),
            priority,
            List.of(),
            new AdjustmentOutput(AdjustmentOutputType.BLOCKING_CONFLICT, null, "CONFIGURED_BLOCKING_CONFLICT"),
            "CONFIGURED_REASON",
            exclusivityGroup,
            true,
            "configured-source-ref"
        );
    }
}
