package com.wcpe.eligibility.domain.models;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record InvestorEligibilityEvaluationRequest(
    UUID investorId,
    String investorName,
    LoanScenario scenario,
    LocalDate quoteDate,
    List<InvestorEligibilityMatrixRow> matrixRows
) {}
