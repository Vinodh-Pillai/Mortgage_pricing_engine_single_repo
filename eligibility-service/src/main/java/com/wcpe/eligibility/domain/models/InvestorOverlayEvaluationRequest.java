package com.wcpe.eligibility.domain.models;

import java.util.Map;
import java.util.UUID;

public record InvestorOverlayEvaluationRequest(
    UUID scenarioId,
    int scenarioVersion,
    String asOfDate,
    String baseDecisionStatus,
    InvestorOverlayProductCandidate productCandidate,
    Map<String, Object> facts
) {}
