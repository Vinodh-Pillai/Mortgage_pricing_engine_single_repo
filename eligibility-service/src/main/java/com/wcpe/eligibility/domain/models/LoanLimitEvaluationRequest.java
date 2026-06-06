package com.wcpe.eligibility.domain.models;

import java.util.List;
import java.util.UUID;

public record LoanLimitEvaluationRequest(
    UUID scenarioId,
    int scenarioVersion,
    String asOfDate,
    List<LoanLimitProductCandidate> productCandidates,
    LoanLimitFacts facts
) {}
