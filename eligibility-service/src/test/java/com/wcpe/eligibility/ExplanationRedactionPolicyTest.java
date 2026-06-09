package com.wcpe.eligibility;

import com.wcpe.eligibility.domain.models.EligibilityExplanationResponse;
import com.wcpe.eligibility.repository.EligibilityPersistRepository;
import com.wcpe.eligibility.service.EligibilityExplanationService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExplanationRedactionPolicyTest {
    @Test
    void bucketsFicoForManager() {
        UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID quoteId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID quoteOptionId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        EligibilityPersistRepository repository = mock(EligibilityPersistRepository.class);
        when(repository.findEligibilityExplanation(tenantId, quoteId, quoteOptionId))
            .thenReturn(Optional.of(new EligibilityExplanationResponse(
                quoteId,
                quoteOptionId,
                UUID.fromString("44444444-4444-4444-4444-444444444444"),
                1,
                "CONF30",
                "FNMA",
                "ELIGIBLE",
                new EligibilityExplanationResponse.Summary(1, 0, 0, 0),
                List.of(new EligibilityExplanationResponse.Rule("FICO_LTV", "FICO/LTV", "ELIGIBLE", "INFO", "OK", "message", "715", "minimum configured", List.of("fact:representativeFico"), List.of(), "FRESH", "cache:eligibility:fico-ltv", "rv1", "ev1", null)),
                new EligibilityExplanationResponse.Audit(UUID.fromString("55555555-5555-5555-5555-555555555555"), "sha256:result", "sha256:graph")
            )));

        EligibilityExplanationResponse response = new EligibilityExplanationService(repository)
            .getExplanation(tenantId, quoteId, quoteOptionId, "manager-1", Set.of("quote:read:any_branch"), "corr-1");

        assertEquals("700-719", response.rules().get(0).actualDisplay());
    }
}
