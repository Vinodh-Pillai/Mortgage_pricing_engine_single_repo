package com.wcpe.eligibility.domain.extension;

import com.wcpe.eligibility.domain.models.RuleDecision;

import java.util.List;

public record RuleDecisionSet(List<RuleDecision> decisions) {
    public RuleDecisionSet {
        decisions = List.copyOf(decisions);
    }
}
