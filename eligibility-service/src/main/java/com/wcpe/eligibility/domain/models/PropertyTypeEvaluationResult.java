package com.wcpe.eligibility.domain.models;

import java.util.UUID;

public record PropertyTypeEvaluationResult(
    UUID evaluationId,
    PropertyTypeDecision decision,
    String resultHash
) {}
