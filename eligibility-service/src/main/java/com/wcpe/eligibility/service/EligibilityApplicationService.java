package com.wcpe.eligibility.service;

import com.wcpe.eligibility.domain.models.EligibilityRequest;
import com.wcpe.eligibility.domain.models.EligibilityResult;
import com.wcpe.eligibility.cache.CachedEligibilityDecision;
import com.wcpe.eligibility.cache.EligibilityCacheService;
import com.wcpe.eligibility.domain.extension.UnsupportedProductFamilyException;
import com.wcpe.eligibility.domain.extension.UnsupportedProductFamilyPolicy;
import com.wcpe.eligibility.domain.ruleset.RuleEngine;
import com.wcpe.eligibility.repository.EligibilityPersistRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class EligibilityApplicationService {
    private final RuleEngine ruleEngine;
    private final EligibilityPersistRepository repository;
    private final UnsupportedProductFamilyPolicy unsupportedProductFamilyPolicy;
    private final EligibilityCacheService cacheService;

    public EligibilityApplicationService(RuleEngine ruleEngine, EligibilityPersistRepository repository,
                                         UnsupportedProductFamilyPolicy unsupportedProductFamilyPolicy,
                                         EligibilityCacheService cacheService) {
        this.ruleEngine = ruleEngine;
        this.repository = repository;
        this.unsupportedProductFamilyPolicy = unsupportedProductFamilyPolicy;
        this.cacheService = cacheService;
    }

    public EligibilityResult evaluate(UUID tenantId, EligibilityRequest request) {
        try {
            unsupportedProductFamilyPolicy.rejectUnsupportedForPii03(request);
        } catch (UnsupportedProductFamilyException ex) {
            repository.auditUnsupportedProductFamilyAttempt(tenantId, ex.productFamily(), ex.quoteType());
            throw ex;
        }
        String ruleVersionGraphHash = ruleEngine.currentRuleVersionGraphHash();
        EligibilityResult result = cacheService.getDecision(tenantId, request, ruleVersionGraphHash)
            .map(CachedEligibilityDecision::result)
            .orElseGet(() -> {
                EligibilityResult evaluated = ruleEngine.evaluate(request, tenantId);
                cacheService.putDecision(tenantId, request, evaluated.eligibilityRuleSetVersion(), evaluated);
                return evaluated;
            });
        repository.saveEvaluation(tenantId, result);
        return result;
    }
}
