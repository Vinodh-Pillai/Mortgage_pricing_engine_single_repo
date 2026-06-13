package com.wcpe.adjustment;

import com.wcpe.adjustment.AdjustmentRuleBook.AdjustmentOutputType;
import com.wcpe.adjustment.AdjustmentRuleBook.PricingPrecisionPolicy;
import java.math.BigDecimal;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class PrecisionNormalizer {
    public BigDecimal normalize(PricingPrecisionPolicy policy, AdjustmentOutputType type, BigDecimal amount) {
        Objects.requireNonNull(policy, "precision policy is required");
        Objects.requireNonNull(type, "output type is required");
        Objects.requireNonNull(amount, "amount is required");
        int scale = switch (type) {
            case POINTS_DELTA, PERCENT_OF_LOAN_AMOUNT -> policy.pointsScale();
            case BPS_DELTA -> policy.bpsScale();
            case MONEY_FEE -> policy.moneyScale();
            case LABEL_ONLY, BLOCKING_CONFLICT -> 0;
        };
        return amount.setScale(scale, policy.roundingMode());
    }

    public BigDecimal applyCapFloor(BigDecimal value, BigDecimal min, BigDecimal max) {
        BigDecimal capped = value;
        if (max != null && capped.compareTo(max) > 0) {
            capped = max;
        }
        if (min != null && capped.compareTo(min) < 0) {
            capped = min;
        }
        return capped;
    }
}
