package com.wcpe.quote;

import java.math.BigDecimal;
import java.util.List;

public record RankScore(
    String optionId,
    BigDecimal totalScore,
    List<BigDecimal> criterionScores,
    List<String> criterionReasons,
    String tieBreakerTrace,
    List<String> warnings
) {
    public RankScore {
        criterionScores = List.copyOf(criterionScores == null ? List.of() : criterionScores);
        criterionReasons = List.copyOf(criterionReasons == null ? List.of() : criterionReasons);
        warnings = List.copyOf(warnings == null ? List.of() : warnings);
    }
}
