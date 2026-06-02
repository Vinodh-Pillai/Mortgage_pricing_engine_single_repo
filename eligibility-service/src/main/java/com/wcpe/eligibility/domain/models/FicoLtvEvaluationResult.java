package com.wcpe.eligibility.domain.models;

import java.math.BigDecimal;
import java.util.UUID;

public record FicoLtvEvaluationResult(
    UUID evaluationId,
    FicoLtvDecision decision,
    String resultHash
) {}
