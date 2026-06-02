package com.wcpe.eligibility.domain.models;

import java.math.BigDecimal;
import java.util.UUID;

public record FicoLtvMatrixEvaluationRequest(
    UUID scenarioId,
    int scenarioVersion,
    String asOfDate,
    FicoLtvProductCandidate productCandidate,
    FicoLtvFacts facts
) {}
