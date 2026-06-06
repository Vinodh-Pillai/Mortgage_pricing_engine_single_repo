package com.wcpe.eligibility.domain.models;

import java.util.List;
import java.util.UUID;

public record LoanLimitEvaluationResult(
    UUID evaluationId,
    String status,
    List<LoanLimitDecision> decisions,
    String resultHash
) {}
