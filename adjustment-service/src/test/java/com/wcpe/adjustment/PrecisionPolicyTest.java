package com.wcpe.adjustment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wcpe.adjustment.AdjustmentRuleBook.AdjustmentOutputType;
import com.wcpe.adjustment.AdjustmentRuleBook.PricingPrecisionPolicy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.junit.jupiter.api.Test;

class PrecisionPolicyTest {

    @Test
    void defaultPrecisionPolicyHasCorrectScalesAndRounding() {
        PricingPrecisionPolicy policy = PricingPrecisionPolicy.defaultPolicy();

        assertThat(policy.pointsScale()).isEqualTo(6);
        assertThat(policy.bpsScale()).isEqualTo(4);
        assertThat(policy.moneyScale()).isEqualTo(2);
        assertThat(policy.roundingMode()).isEqualTo(RoundingMode.HALF_UP);
    }

    @Test
    void pointsDeltaNormalizesToSixDecimals() {
        BigDecimal normalized = PricingPrecisionPolicy.defaultPolicy()
            .normalize(AdjustmentOutputType.POINTS_DELTA, new BigDecimal("0.125"));

        assertThat(normalized).isEqualByComparingTo("0.125000");
        assertThat(normalized.scale()).isEqualTo(6);
    }

    @Test
    void bpsDeltaNormalizesToFourDecimals() {
        BigDecimal normalized = PricingPrecisionPolicy.defaultPolicy()
            .normalize(AdjustmentOutputType.BPS_DELTA, new BigDecimal("12.5"));

        assertThat(normalized).isEqualByComparingTo("12.5000");
        assertThat(normalized.scale()).isEqualTo(4);
    }

    @Test
    void moneyFeeNormalizesToTwoDecimals() {
        BigDecimal normalized = PricingPrecisionPolicy.defaultPolicy()
            .normalize(AdjustmentOutputType.MONEY_FEE, new BigDecimal("1234.567"));

        assertThat(normalized).isEqualByComparingTo("1234.57");
        assertThat(normalized.scale()).isEqualTo(2);
    }

    @Test
    void percentOfLoanAmountNormalizesToSixDecimals() {
        BigDecimal normalized = PricingPrecisionPolicy.defaultPolicy()
            .normalize(AdjustmentOutputType.PERCENT_OF_LOAN_AMOUNT, new BigDecimal("2.5"));

        assertThat(normalized).isEqualByComparingTo("2.500000");
        assertThat(normalized.scale()).isEqualTo(6);
    }

    @Test
    void labelOnlyAndBlockingConflictNormalizeToZeroDecimals() {
        PricingPrecisionPolicy policy = PricingPrecisionPolicy.defaultPolicy();

        BigDecimal labelOnly = policy.normalize(AdjustmentOutputType.LABEL_ONLY, new BigDecimal("3.6"));
        BigDecimal blockingConflict = policy.normalize(AdjustmentOutputType.BLOCKING_CONFLICT, new BigDecimal("7.4"));

        assertThat(labelOnly).isEqualByComparingTo("4");
        assertThat(labelOnly.scale()).isZero();
        assertThat(blockingConflict).isEqualByComparingTo("7");
        assertThat(blockingConflict.scale()).isZero();
    }

    @Test
    void customRoundingModeIsRespected() {
        PricingPrecisionPolicy policy = new PricingPrecisionPolicy(2, 2, 2, RoundingMode.HALF_EVEN);

        BigDecimal normalized = policy.normalize(AdjustmentOutputType.MONEY_FEE, new BigDecimal("2.225"));

        assertThat(normalized).isEqualByComparingTo("2.22");
        assertThat(normalized.scale()).isEqualTo(2);
    }

    @Test
    void zeroScaleRoundsToInteger() {
        PricingPrecisionPolicy policy = new PricingPrecisionPolicy(6, 4, 0, RoundingMode.HALF_UP);

        BigDecimal normalized = policy.normalize(AdjustmentOutputType.MONEY_FEE, new BigDecimal("1234.5"));

        assertThat(normalized).isEqualByComparingTo("1235");
        assertThat(normalized.scale()).isZero();
    }

    @Test
    void negativeScaleRejected() {
        assertThatThrownBy(() -> new PricingPrecisionPolicy(-1, 4, 2, RoundingMode.HALF_UP))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("non-negative");
    }

    @Test
    void halfUpRoundingCorrectOnFive() {
        PricingPrecisionPolicy policy = new PricingPrecisionPolicy(3, 4, 2, RoundingMode.HALF_UP);

        BigDecimal normalized = policy.normalize(AdjustmentOutputType.POINTS_DELTA, new BigDecimal("0.1255"));

        assertThat(normalized).isEqualByComparingTo("0.126");
        assertThat(normalized.scale()).isEqualTo(3);
    }
}
