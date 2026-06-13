package com.wcpe.eligibility.domain.models;

import java.time.LocalDate;
import java.util.List;

public record InvestorEligibilityBatchEvaluationRequest(
    LoanScenario scenario,
    LocalDate quoteDate,
    List<InvestorEligibilityEvaluationRequest> investors
) {}
