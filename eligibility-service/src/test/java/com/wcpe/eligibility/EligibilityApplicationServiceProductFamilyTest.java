package com.wcpe.eligibility;

import com.wcpe.eligibility.domain.extension.UnsupportedProductFamilyException;
import com.wcpe.eligibility.domain.extension.UnsupportedProductFamilyPolicy;
import com.wcpe.eligibility.cache.EligibilityCacheService;
import com.wcpe.eligibility.domain.models.BorrowerProfile;
import com.wcpe.eligibility.domain.models.EligibilityRequest;
import com.wcpe.eligibility.domain.models.LoanProfile;
import com.wcpe.eligibility.domain.models.ProductCandidate;
import com.wcpe.eligibility.domain.models.ProductFamily;
import com.wcpe.eligibility.domain.models.PropertyProfile;
import com.wcpe.eligibility.domain.models.QuoteType;
import com.wcpe.eligibility.domain.ruleset.RuleEngine;
import com.wcpe.eligibility.repository.EligibilityPersistRepository;
import com.wcpe.eligibility.service.EligibilityApplicationService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;

class EligibilityApplicationServiceProductFamilyTest {
    @Test
    void rejectsGovernmentFamilyBeforeEvaluationPersistAndAuditsAttempt() {
        RuleEngine ruleEngine = mock(RuleEngine.class);
        EligibilityPersistRepository repository = mock(EligibilityPersistRepository.class);
        EligibilityCacheService cacheService = mock(EligibilityCacheService.class);
        EligibilityApplicationService service = new EligibilityApplicationService(
            ruleEngine,
            repository,
            new UnsupportedProductFamilyPolicy(),
            cacheService
        );
        UUID tenantId = UUID.randomUUID();
        EligibilityRequest request = request(ProductFamily.FHA, QuoteType.FHA_PURCHASE);

        assertThrows(UnsupportedProductFamilyException.class, () -> service.evaluate(tenantId, request));

        verify(repository).auditUnsupportedProductFamilyAttempt(tenantId, ProductFamily.FHA, QuoteType.FHA_PURCHASE);
        verify(ruleEngine, never()).evaluate(request, tenantId);
        verify(repository, never()).saveEvaluation(any(), any());
    }

    private EligibilityRequest request(ProductFamily productFamily, QuoteType quoteType) {
        return new EligibilityRequest(
            new BorrowerProfile(742, new BigDecimal("8500"), new BigDecimal("1200")),
            new PropertyProfile("TX", "Austin", "78701", "SINGLE_FAMILY", 1, "PRIMARY_RESIDENCE", new BigDecimal("500000"), new BigDecimal("500000")),
            new LoanProfile("PURCHASE", new BigDecimal("400000"), BigDecimal.ZERO, 30, "FULL_DOC", "AUTOMATED", 1),
            new ProductCandidate(UUID.randomUUID(), "CONV30", "FNMA"),
            productFamily,
            quoteType
        );
    }
}
