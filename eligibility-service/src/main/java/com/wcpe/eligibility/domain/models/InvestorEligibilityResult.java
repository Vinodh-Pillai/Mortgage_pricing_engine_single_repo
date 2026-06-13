package com.wcpe.eligibility.domain.models;

import java.util.List;
import java.util.Map;

public record InvestorEligibilityResult(
    boolean eligible,
    String investorId,
    String investorName,
    List<EligibilityFailure> failures,
    List<String> warnings,
    Map<String, Object> thresholds
) {}
