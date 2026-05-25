package com.wcpe.eligibility.domain.rules;

import com.wcpe.eligibility.domain.models.EligibilityRequest;
import com.wcpe.eligibility.domain.models.RuleDecision;

public interface EligibilityRule {
    RuleType getRuleType();
    RuleDecision evaluate(EligibilityRequest request, String productCode, String investorCode);
}
