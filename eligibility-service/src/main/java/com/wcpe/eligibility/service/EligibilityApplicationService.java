package com.wcpe.eligibility.service;

import com.wcpe.eligibility.domain.models.EligibilityRequest;
import com.wcpe.eligibility.domain.models.EligibilityResult;
import com.wcpe.eligibility.domain.ruleset.RuleEngine;
import com.wcpe.eligibility.repository.EligibilityPersistRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class EligibilityApplicationService {
    private final RuleEngine ruleEngine;
    private final EligibilityPersistRepository repository;

    public EligibilityApplicationService(RuleEngine ruleEngine, EligibilityPersistRepository repository) {
        this.ruleEngine = ruleEngine;
        this.repository = repository;
    }

    public EligibilityResult evaluate(UUID tenantId, EligibilityRequest request) {
        EligibilityResult result = ruleEngine.evaluate(request, tenantId);
        repository.saveEvaluation(tenantId, result);
        return result;
    }
}
