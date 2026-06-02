package com.wcpe.eligibility.domain.models;

import java.util.UUID;

public record PropertyTypeEvaluationRequest(
    UUID scenarioId,
    int scenarioVersion,
    String asOfDate,
    PropertyTypeProductCandidate productCandidate,
    PropertyTypeFacts facts
) {}
