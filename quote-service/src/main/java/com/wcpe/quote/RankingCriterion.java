package com.wcpe.quote;

import java.math.BigDecimal;

public record RankingCriterion(
    String criterionId,
    String type,
    String fieldRef,
    BigDecimal weight,
    boolean required,
    String configJson
) {
    public RankingCriterion {
        configJson = configJson == null ? "{}" : configJson;
    }
}
