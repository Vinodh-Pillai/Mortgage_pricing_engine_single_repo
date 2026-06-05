package com.wcpe.adjustment;

import static org.assertj.core.api.Assertions.assertThat;

import com.wcpe.adjustment.PropertyOccupancyLlpaEvaluator.BoundaryPolicy;
import com.wcpe.adjustment.PropertyOccupancyLlpaEvaluator.EvaluationRequest;
import com.wcpe.adjustment.PropertyOccupancyLlpaEvaluator.EvaluationStatus;
import com.wcpe.adjustment.PropertyOccupancyLlpaEvaluator.LedgerEntry;
import com.wcpe.adjustment.PropertyOccupancyLlpaEvaluator.LtvMetric;
import com.wcpe.adjustment.PropertyOccupancyLlpaEvaluator.PropertyOccupancyAdjustmentResult;
import com.wcpe.adjustment.PropertyOccupancyLlpaEvaluator.PropertyOccupancyRule;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PropertyOccupancyLlpaEvaluatorTest {
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000064");
    private static final UUID OTHER_TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000164");
    private static final UUID RULE_BOOK_ID = UUID.fromString("20000000-0000-0000-0000-000000000064");
    private static final UUID RULE_ID = UUID.fromString("30000000-0000-0000-0000-000000000064");
    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void PropertyOccupancyRequiredFieldsTest_failsClosedWithoutDefaultingCollateralFields() {
        PropertyOccupancyLlpaEvaluator evaluator = new PropertyOccupancyLlpaEvaluator();

        PropertyOccupancyAdjustmentResult missingOccupancy = evaluator.evaluate(request(null, "CONFIGURED_PROPERTY", 2), List.of(rule()));
        PropertyOccupancyAdjustmentResult missingProperty = evaluator.evaluate(request("CONFIGURED_OCCUPANCY", null, 2), List.of(rule()));
        PropertyOccupancyAdjustmentResult missingUnits = evaluator.evaluate(request("CONFIGURED_OCCUPANCY", "CONFIGURED_PROPERTY", null), List.of(rule()));

        assertThat(missingOccupancy.status()).isEqualTo(EvaluationStatus.FAIL_CLOSED);
        assertThat(missingOccupancy.validationMessages()).containsExactly("ADJ_PROPERTY_FIELD_MISSING: occupancyCode");
        assertThat(missingProperty.validationMessages()).containsExactly("ADJ_PROPERTY_FIELD_MISSING: propertyTypeCode");
        assertThat(missingUnits.validationMessages()).containsExactly("ADJ_PROPERTY_FIELD_MISSING: units");
    }

    @Test
    void PropertyQualifierStackingTest_stacksConfiguredNonExclusiveRules() {
        PropertyOccupancyRule baseRule = rule(RULE_ID, "0.125000", 1, null, List.of());
        PropertyOccupancyRule qualifierRule = rule(
            UUID.fromString("30000000-0000-0000-0000-000000000065"),
            "0.250000",
            2,
            null,
            List.of("CONFIGURED_PROJECT_QUALIFIER")
        );

        PropertyOccupancyAdjustmentResult result = new PropertyOccupancyLlpaEvaluator().evaluate(request(), List.of(baseRule, qualifierRule));

        assertThat(result.status()).isEqualTo(EvaluationStatus.APPLIED);
        assertThat(result.category()).isEqualTo("LLPA_PROPERTY_OCCUPANCY");
        assertThat(result.pointsDelta()).isEqualByComparingTo("0.375000");
        assertThat(result.bpsDelta()).isEqualByComparingTo("37.5000");
        assertThat(result.moneyImpact()).isEqualByComparingTo("1500.00");
        assertThat(result.priceAfterPropertyOccupancy()).isEqualByComparingTo("100.375000");
        assertThat(result.appliedRuleIds()).containsExactly(baseRule.ruleId(), qualifierRule.ruleId());
        assertThat(result.reasonCodes()).containsExactly("CONFIGURED_PROPERTY_REASON", "CONFIGURED_PROPERTY_REASON");
        assertThat(result.inputSnapshotHash()).hasSize(64);
    }

    @Test
    void PropertyOccupancyExclusivityTest_selectsPriorityWinnerAndRecordsSuppressedRules() {
        PropertyOccupancyRule selected = rule(RULE_ID, "0.125000", 1, "CONFIGURED_EXCLUSIVE", List.of());
        PropertyOccupancyRule suppressed = rule(
            UUID.fromString("30000000-0000-0000-0000-000000000066"),
            "0.500000",
            3,
            "CONFIGURED_EXCLUSIVE",
            List.of()
        );

        PropertyOccupancyAdjustmentResult result = new PropertyOccupancyLlpaEvaluator().evaluate(request(), List.of(suppressed, selected));

        assertThat(result.status()).isEqualTo(EvaluationStatus.APPLIED);
        assertThat(result.pointsDelta()).isEqualByComparingTo("0.125000");
        assertThat(result.appliedRuleIds()).containsExactly(selected.ruleId());
        assertThat(result.suppressedRuleIds()).containsExactly(suppressed.ruleId());
        assertThat(result.ruleContentHash()).hasSize(64);
    }

    @Test
    void PropertyOccupancyExclusivityTest_rejectsSamePriorityExclusiveAmbiguity() {
        PropertyOccupancyRule first = rule(RULE_ID, "0.125000", 1, "CONFIGURED_EXCLUSIVE", List.of());
        PropertyOccupancyRule second = rule(
            UUID.fromString("30000000-0000-0000-0000-000000000067"),
            "0.250000",
            1,
            "CONFIGURED_EXCLUSIVE",
            List.of()
        );

        PropertyOccupancyAdjustmentResult result = new PropertyOccupancyLlpaEvaluator().evaluate(request(), List.of(first, second));

        assertThat(result.status()).isEqualTo(EvaluationStatus.CONFLICT);
        assertThat(result.validationMessages()).containsExactly("ADJ_PROPERTY_RULE_AMBIGUOUS");
        assertThat(result.pointsDelta()).isZero();
    }

    @Test
    void PropertyOccupancyPrecisionTest_roundsOutputsAndCreatesDeterministicWaterfallAfterCashOut() {
        PropertyOccupancyLlpaEvaluator evaluator = new PropertyOccupancyLlpaEvaluator();
        PropertyOccupancyAdjustmentResult first = evaluator.evaluate(request(), List.of(rule(RULE_ID, "0.1266666", 1, null, List.of())));
        PropertyOccupancyAdjustmentResult second = evaluator.evaluate(request(), List.of(rule(RULE_ID, "0.1266666", 1, null, List.of())));
        LedgerEntry ledger = LedgerEntry.from(TENANT_ID, "QUOTE-PII-06-S04", first);

        assertThat(first.adjustmentId()).isEqualTo(second.adjustmentId());
        assertThat(first.pointsDelta()).isEqualByComparingTo("0.126667");
        assertThat(first.bpsDelta()).isEqualByComparingTo("12.6667");
        assertThat(first.moneyImpact()).isEqualByComparingTo("506.67");
        assertThat(ledger.ruleType()).isEqualTo("PROPERTY_OCCUPANCY_LLPA");
        assertThat(ledger.waterfallSequence()).isGreaterThan(40);
        assertThat(ledger.contentHash()).hasSize(64);
    }

    @Test
    void PropertyOccupancyTenantIsolationIT_ignoresOtherTenantRulesAndFailsClosedWhenNoTenantRuleMatches() {
        PropertyOccupancyRule otherTenantRule = new PropertyOccupancyRule(
            OTHER_TENANT_ID,
            RULE_BOOK_ID,
            RULE_ID,
            "v1",
            "configured-product",
            "configured-investor",
            "configured-channel",
            "CONFIGURED_OCCUPANCY",
            "CONFIGURED_PROPERTY",
            1,
            4,
            "CONFIGURED_PROJECT",
            false,
            "CONFIGURED_STATE",
            "CONFIGURED_COUNTY",
            LtvMetric.LTV,
            "CONFIGURED_LTV_BAND",
            new BigDecimal("80.000000"),
            new BigDecimal("85.000000"),
            BoundaryPolicy.MIN_INCLUSIVE_MAX_INCLUSIVE,
            "CONFIGURED_AMOUNT_BAND",
            new BigDecimal("250000.00"),
            new BigDecimal("500000.00"),
            true,
            List.of("CONFIGURED_PROJECT_QUALIFIER"),
            new BigDecimal("0.125000"),
            "CONFIGURED_PROPERTY_REASON",
            1,
            null,
            START,
            null,
            null,
            true
        );

        PropertyOccupancyAdjustmentResult result = new PropertyOccupancyLlpaEvaluator().evaluate(request(), List.of(otherTenantRule));

        assertThat(result.status()).isEqualTo(EvaluationStatus.FAIL_CLOSED);
        assertThat(result.validationMessages()).containsExactly("ADJ_PROPERTY_RULE_NOT_FOUND");
    }

    private static EvaluationRequest request() {
        return request("CONFIGURED_OCCUPANCY", "CONFIGURED_PROPERTY", 2);
    }

    private static EvaluationRequest request(String occupancyCode, String propertyTypeCode, Integer units) {
        return new EvaluationRequest(
            TENANT_ID,
            "QUOTE-PII-06-S04",
            "SCENARIO-PII-06-S04",
            "configured-product",
            "configured-investor",
            "configured-channel",
            Instant.parse("2026-02-01T00:00:00Z"),
            new BigDecimal("400000.00"),
            new BigDecimal("100.000000"),
            occupancyCode,
            propertyTypeCode,
            units,
            "CONFIGURED_PROJECT",
            false,
            "CONFIGURED_STATE",
            "CONFIGURED_COUNTY",
            new BigDecimal("82.500000"),
            null,
            null,
            true,
            List.of("CONFIGURED_PROJECT_QUALIFIER"),
            "v1"
        );
    }

    private static PropertyOccupancyRule rule() {
        return rule(RULE_ID, "0.125000", 1, null, List.of());
    }

    private static PropertyOccupancyRule rule(
        UUID ruleId,
        String pointsDelta,
        int priority,
        String exclusivityGroup,
        List<String> qualifiers
    ) {
        return new PropertyOccupancyRule(
            TENANT_ID,
            RULE_BOOK_ID,
            ruleId,
            "v1",
            "configured-product",
            "configured-investor",
            "configured-channel",
            "CONFIGURED_OCCUPANCY",
            "CONFIGURED_PROPERTY",
            1,
            4,
            "CONFIGURED_PROJECT",
            false,
            "CONFIGURED_STATE",
            "CONFIGURED_COUNTY",
            LtvMetric.LTV,
            "CONFIGURED_LTV_BAND",
            new BigDecimal("80.000000"),
            new BigDecimal("85.000000"),
            BoundaryPolicy.MIN_INCLUSIVE_MAX_INCLUSIVE,
            "CONFIGURED_AMOUNT_BAND",
            new BigDecimal("250000.00"),
            new BigDecimal("500000.00"),
            true,
            qualifiers,
            new BigDecimal(pointsDelta),
            "CONFIGURED_PROPERTY_REASON",
            priority,
            exclusivityGroup,
            START,
            null,
            null,
            true
        );
    }
}
