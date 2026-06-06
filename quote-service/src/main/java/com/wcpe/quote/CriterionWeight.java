package com.wcpe.quote;

import java.math.BigDecimal;

public record CriterionWeight(
    String criterionId,
    BigDecimal weight,
    BigDecimal minWeight,
    BigDecimal maxWeight
) {
    public CriterionWeight {
        minWeight = minWeight == null ? BigDecimal.ZERO : minWeight;
        maxWeight = maxWeight == null ? BigDecimal.ONE : maxWeight;
    }
}
