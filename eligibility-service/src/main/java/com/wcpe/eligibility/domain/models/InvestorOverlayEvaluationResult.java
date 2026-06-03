package com.wcpe.eligibility.domain.models;

import java.util.List;
import java.util.UUID;

public record InvestorOverlayEvaluationResult(
    UUID evaluationId,
    String overlaySummaryStatus,
    List<InvestorOverlayDecision> decisions,
    String resultHash
) {}
