package com.wcpe.eligibility.domain.models;

import java.util.UUID;

public record OccupancyPurposeEvaluationResult(
    UUID evaluationId,
    OccupancyPurposeDecision decision,
    String resultHash
) {}
