package com.wcpe.eligibility.repository;

import com.wcpe.eligibility.nonqm.NonQmEligibilityModels.NonQmEligibilityRuleSet;

import java.time.Instant;
import java.util.Optional;

public interface NonQmRuleSetStore {
    void save(NonQmEligibilityRuleSet ruleSet);

    Optional<NonQmEligibilityRuleSet> findById(String ruleSetId);

    Optional<NonQmEligibilityRuleSet> resolve(String productCode, String investorCode, String channelCode, Instant asOf);
}
