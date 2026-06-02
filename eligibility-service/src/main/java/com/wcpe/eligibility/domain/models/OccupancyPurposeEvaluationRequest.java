package com.wcpe.eligibility.domain.models;

import java.util.UUID;

public record OccupancyPurposeEvaluationRequest(
    UUID scenarioId,
    int scenarioVersion,
    String asOfDate,
    OccupancyPurposeProductCandidate productCandidate,
    OccupancyPurposeFacts facts
) {}
