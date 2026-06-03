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

class EligibilityExplanationAssemblerTest {
    @Test
    void ordersRulesByDecisionChain() {
        UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID quoteId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID quoteOptionId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        EligibilityPersistRepository repository = mock(EligibilityPersistRepository.class);
        when(repository.findEligibilityExplanation(tenantId, quoteId, quoteOptionId))
            .thenReturn(Optional.of(response(quoteId, quoteOptionId, List.of(
                rule("INVESTOR_OVERLAY", "WARN"),
                rule("CONF_LOAN_LIMIT", "PASS"),
                rule("FICO_LTV", "PASS")
            ))));

        EligibilityExplanationResponse assembled = new EligibilityExplanationService(repository)
            .getExplanation(tenantId, quoteId, quoteOptionId, "actor-1", Set.of("quote:read"), "corr-1");

        assertEquals("CONF_LOAN_LIMIT", assembled.rules().get(0).ruleCode());
        assertEquals("FICO_LTV", assembled.rules().get(1).ruleCode());
        assertEquals("INVESTOR_OVERLAY", assembled.rules().get(2).ruleCode());
    }

    private EligibilityExplanationResponse response(UUID quoteId, UUID quoteOptionId, List<EligibilityExplanationResponse.Rule> rules) {
        return new EligibilityExplanationResponse(
            quoteId,
            quoteOptionId,
            UUID.fromString("44444444-4444-4444-4444-444444444444"),
            1,
            "CONF30",
            "FNMA",
            "ELIGIBLE",
            new EligibilityExplanationResponse.Summary(2, 0, 1, 0),
            rules,
            new EligibilityExplanationResponse.Audit(UUID.fromString("55555555-5555-5555-5555-555555555555"), "sha256:result", "sha256:graph")
        );
    }

    private EligibilityExplanationResponse.Rule rule(String ruleCode, String status) {
        return new EligibilityExplanationResponse.Rule(ruleCode, ruleCode, status, "INFO", "RC", "message", "710", null, "rv1", "ev1", null);
    }
}
