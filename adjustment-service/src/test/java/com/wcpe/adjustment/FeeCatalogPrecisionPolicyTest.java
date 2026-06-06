package com.wcpe.adjustment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wcpe.adjustment.FeeCatalogVersion.FeePrecisionPolicy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.junit.jupiter.api.Test;

class FeeCatalogPrecisionPolicyTest {
    @Test
    void precisionPolicyRoundsMoneyAndRateUsingConfiguredHalfUpPolicy() {
        FeePrecisionPolicy policy = new FeePrecisionPolicy(2, 6, 18, RoundingMode.HALF_UP);

        assertThat(policy.normalizeMoney(new BigDecimal("10.005"))).isEqualByComparingTo("10.01");
        assertThat(policy.normalizeRate(new BigDecimal("0.1234567"))).isEqualByComparingTo("0.123457");
    }

    @Test
    void precisionPolicyFailsClosedWhenConfiguredNumericValueExceedsPrecision() {
        FeePrecisionPolicy policy = new FeePrecisionPolicy(2, 6, 4, RoundingMode.HALF_UP);

        assertThatThrownBy(() -> policy.normalizeMoney(new BigDecimal("1000.00")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("exceeds precision policy");
    }
}
