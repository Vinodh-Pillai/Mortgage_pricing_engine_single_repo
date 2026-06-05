package com.wcpe.adjustment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wcpe.adjustment.CashOutLlpaEvaluator.BoundaryPolicy;
import com.wcpe.adjustment.CashOutLlpaEvaluator.CashOutAdjustmentResult;
import com.wcpe.adjustment.CashOutLlpaEvaluator.CashOutClassification;
import com.wcpe.adjustment.CashOutLlpaEvaluator.CashOutLlpaRule;
import com.wcpe.adjustment.CashOutLlpaEvaluator.EvaluationRequest;
import com.wcpe.adjustment.CashOutLlpaEvaluator.EvaluationStatus;
import com.wcpe.adjustment.CashOutLlpaEvaluator.LedgerEntry;
import com.wcpe.adjustment.CashOutLlpaEvaluator.LtvMetric;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CashOutLlpaEvaluatorTest {
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000063");
    private static final UUID RULE_BOOK_ID = UUID.fromString("20000000-0000-0000-0000-000000000063");
    private static final UUID RULE_ID = UUID.fromString("30000000-0000-0000-0000-000000000063");
    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void CashOutBandMatchTest_appliesConfiguredCashOutRuleWithoutHardcodedLlpaValues() {
        CashOutAdjustmentResult result = new CashOutLlpaEvaluator().evaluate(request(cashOutClassification()), List.of(rule(
            "CONFIGURED_LTV_BAND_A",
            "80.000000",
            "85.000000",
            "CONFIGURED_AMOUNT_BAND_A",
            "250000.00",
            "500000.00",
            "0.375000",
            1
        )));

        assertThat(result.status()).isEqualTo(EvaluationStatus.APPLIED);
        assertThat(result.category()).isEqualTo("LLPA_CASH_OUT");
        assertThat(result.pointsDelta()).isEqualByComparingTo("0.375000");
        assertThat(result.bpsDelta()).isEqualByComparingTo("37.5000");
        assertThat(result.moneyImpact()).isEqualByComparingTo("1500.00");
        assertThat(result.priceAfterCashOut()).isEqualByComparingTo("100.375000");
        assertThat(result.selectedLtvBandKey()).isEqualTo("CONFIGURED_LTV_BAND_A");
        assertThat(result.selectedLoanAmountBandKey()).isEqualTo("CONFIGURED_AMOUNT_BAND_A");
        assertThat(result.classificationCode()).isEqualTo("CONFIGURED_CASH_OUT");
        assertThat(result.inputSnapshotHash()).hasSize(64);
    }

    @Test
    void CashOutClassificationRequiredTest_failsClosedWhenClassificationIsMissingOrAmbiguous() {
        CashOutLlpaEvaluator evaluator = new CashOutLlpaEvaluator();

        CashOutAdjustmentResult missing = evaluator.evaluate(request(null), List.of(rule()));
        CashOutAdjustmentResult ambiguous = evaluator.evaluate(
            request(new CashOutClassification("CONFIGURED_CASH_OUT", "scenario-service", START, true, true)),
            List.of(rule())
        );

        assertThat(missing.status()).isEqualTo(EvaluationStatus.FAIL_CLOSED);
        assertThat(missing.validationMessages()).containsExactly("ADJ_CASH_OUT_CLASSIFICATION_MISSING");
        assertThat(ambiguous.status()).isEqualTo(EvaluationStatus.CONFLICT);
        assertThat(ambiguous.validationMessages()).containsExactly("ADJ_CASH_OUT_AMBIGUOUS");
    }

    @Test
    void CashOutNonApplicableTest_returnsExplicitMarkerForNonCashOutScenario() {
        CashOutClassification rateTerm = new CashOutClassification("CONFIGURED_RATE_TERM", "scenario-service", START, false, false);

        CashOutAdjustmentResult result = new CashOutLlpaEvaluator().evaluate(request(rateTerm), List.of(rule()));

        assertThat(result.status()).isEqualTo(EvaluationStatus.NON_APPLICABLE);
        assertThat(result.pointsDelta()).isZero();
        assertThat(result.validationMessages()).containsExactly("scenario is not classified as cash-out");
    }

    @Test
    void CashOutPrecisionTest_roundsOutputsAndCreatesDeterministicWaterfallLedgerAfterFicoLtv() {
        CashOutLlpaEvaluator evaluator = new CashOutLlpaEvaluator();
        CashOutAdjustmentResult first = evaluator.evaluate(request(cashOutClassification()), List.of(rule(
            "CONFIGURED_LTV_BAND_A",
            "80.000000",
            "85.000000",
            "CONFIGURED_AMOUNT_BAND_A",
            "250000.00",
            "500000.00",
            "0.1266666",
            1
        )));
        CashOutAdjustmentResult second = evaluator.evaluate(request(cashOutClassification()), List.of(rule(
            "CONFIGURED_LTV_BAND_A",
            "80.000000",
            "85.000000",
            "CONFIGURED_AMOUNT_BAND_A",
            "250000.00",
            "500000.00",
            "0.1266666",
            1
        )));
        LedgerEntry ledger = LedgerEntry.from(TENANT_ID, "QUOTE-PII-06-S03", first);

        assertThat(first.adjustmentId()).isEqualTo(second.adjustmentId());
        assertThat(first.pointsDelta()).isEqualByComparingTo("0.126667");
        assertThat(first.bpsDelta()).isEqualByComparingTo("12.6667");
        assertThat(first.moneyImpact()).isEqualByComparingTo("506.67");
        assertThat(ledger.ruleType()).isEqualTo("CASH_OUT_LLPA");
        assertThat(ledger.waterfallSequence()).isGreaterThan(30);
        assertThat(ledger.contentHash()).hasSize(64);
    }

    @Test
    void CashOutAmbiguousRuleTest_rejectsOverlappingRulesAndSamePriorityMatches() {
        CashOutLlpaRule first = rule("LTV_A", "80.000000", "85.000000", "AMOUNT_A", "250000.00", "500000.00", "0.125000", 1);
        CashOutLlpaRule second = rule("LTV_B", "84.000000", "90.000000", "AMOUNT_B", "300000.00", "600000.00", "0.250000", 1);

        assertThatThrownBy(() -> CashOutLlpaEvaluator.validateNoOverlaps(List.of(first, second)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("overlapping cash-out LLPA rules");
    }

    private static EvaluationRequest request(CashOutClassification classification) {
        return new EvaluationRequest(
            TENANT_ID,
            "QUOTE-PII-06-S03",
            "SCENARIO-PII-06-S03",
            "configured-product",
            "configured-investor",
            "configured-channel",
            Instant.parse("2026-02-01T00:00:00Z"),
            new BigDecimal("400000.00"),
            new BigDecimal("100.000000"),
            classification,
            "CONFIGURED_REFINANCE_TYPE",
            new BigDecimal("50000.00"),
            new BigDecimal("350000.00"),
            new BigDecimal("82.500000"),
            null,
            null,
            "CONFIGURED_OCCUPANCY",
            "CONFIGURED_PROPERTY_TYPE",
            "CONFIGURED_STATE",
            "v1"
        );
    }

    private static CashOutClassification cashOutClassification() {
        return new CashOutClassification("CONFIGURED_CASH_OUT", "scenario-service", START, true, false);
    }

    private static CashOutLlpaRule rule() {
        return rule("CONFIGURED_LTV_BAND_A", "80.000000", "85.000000", "CONFIGURED_AMOUNT_BAND_A", "250000.00", "500000.00", "0.375000", 1);
    }

    private static CashOutLlpaRule rule(
        String ltvBandKey,
        String ltvMin,
        String ltvMax,
        String loanAmountBandKey,
        String loanAmountMin,
        String loanAmountMax,
        String pointsDelta,
        int priority
    ) {
        return new CashOutLlpaRule(
            TENANT_ID,
            RULE_BOOK_ID,
            RULE_ID,
            "v1",
            "configured-product",
            "configured-investor",
            "configured-channel",
            "CONFIGURED_CASH_OUT",
            LtvMetric.LTV,
            ltvBandKey,
            new BigDecimal(ltvMin),
            new BigDecimal(ltvMax),
            BoundaryPolicy.MIN_INCLUSIVE_MAX_INCLUSIVE,
            loanAmountBandKey,
            new BigDecimal(loanAmountMin),
            new BigDecimal(loanAmountMax),
            "CONFIGURED_OCCUPANCY",
            "CONFIGURED_PROPERTY_TYPE",
            "CONFIGURED_STATE",
            new BigDecimal(pointsDelta),
            "CONFIGURED_CASH_OUT_REASON",
            priority,
            START,
            null,
            null,
            true
        );
    }
}
