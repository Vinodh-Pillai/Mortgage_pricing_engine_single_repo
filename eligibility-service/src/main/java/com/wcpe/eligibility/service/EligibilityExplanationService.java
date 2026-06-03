package com.wcpe.eligibility.service;

import com.wcpe.eligibility.domain.models.EligibilityExplanationResponse;
import com.wcpe.eligibility.repository.EligibilityPersistRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class EligibilityExplanationService {
    private static final List<String> DECISION_CHAIN = List.of(
        "CONF_LOAN_LIMIT",
        "OCCUPANCY_PURPOSE",
        "PROPERTY_TYPE",
        "FICO_LTV",
        "INVESTOR_OVERLAY"
    );

    private final EligibilityPersistRepository repository;

    public EligibilityExplanationService(EligibilityPersistRepository repository) {
        this.repository = repository;
    }

    public EligibilityExplanationResponse getExplanation(UUID tenantId, UUID quoteId, UUID quoteOptionId,
                                                         String actorId, Set<String> permissions, String correlationId) {
        if (!canRead(permissions)) {
            throw new AccessDeniedException();
        }

        EligibilityExplanationResponse stored = repository.findEligibilityExplanation(tenantId, quoteId, quoteOptionId)
            .orElseThrow(ExplanationNotFoundException::new);
        boolean sensitive = permissions.contains("audit:read_sensitive");
        EligibilityExplanationResponse response = redactAndOrder(stored, sensitive);
        if (sensitive) {
            repository.auditExplanationViewed(tenantId, quoteOptionId, actorId, correlationId, stored.audit().resultHash());
        }
        return response;
    }

    EligibilityExplanationResponse redactAndOrder(EligibilityExplanationResponse stored, boolean sensitive) {
        List<EligibilityExplanationResponse.Rule> ordered = stored.rules().stream()
            .sorted(Comparator.comparingInt(rule -> chainOrder(rule.ruleCode())))
            .map(rule -> sensitive ? rule : redact(rule))
            .toList();
        return new EligibilityExplanationResponse(
            stored.quoteId(),
            stored.quoteOptionId(),
            stored.scenarioId(),
            stored.scenarioVersion(),
            stored.productCode(),
            stored.investorCode(),
            stored.eligibilityStatus(),
            stored.summary(),
            ordered,
            stored.audit()
        );
    }

    private boolean canRead(Set<String> permissions) {
        return permissions.contains("quote:read")
            || permissions.contains("quote:read:any_branch")
            || permissions.contains("audit:read");
    }

    private EligibilityExplanationResponse.Rule redact(EligibilityExplanationResponse.Rule rule) {
        String actualDisplay = rule.actualDisplay();
        if (actualDisplay != null && rule.ruleCode() != null && rule.ruleCode().contains("FICO")) {
            actualDisplay = bucketFico(actualDisplay);
        }
        if (actualDisplay != null && actualDisplay.toLowerCase().contains("sensitive")) {
            actualDisplay = "REDACTED";
        }
        return new EligibilityExplanationResponse.Rule(
            rule.ruleCode(),
            rule.ruleName(),
            rule.status(),
            rule.severity(),
            rule.reasonCode(),
            rule.message(),
            actualDisplay,
            rule.thresholdDisplay(),
            rule.ruleVersionId(),
            rule.evidenceId(),
            rule.remediationHint()
        );
    }

    private String bucketFico(String value) {
        try {
            int fico = Integer.parseInt(value.replaceAll("[^0-9]", ""));
            int min = (fico / 20) * 20;
            return min + "-" + (min + 19);
        } catch (NumberFormatException ex) {
            return "REDACTED";
        }
    }

    private int chainOrder(String ruleCode) {
        if (ruleCode == null) {
            return DECISION_CHAIN.size();
        }
        for (int i = 0; i < DECISION_CHAIN.size(); i++) {
            if (ruleCode.contains(DECISION_CHAIN.get(i))) {
                return i;
            }
        }
        return DECISION_CHAIN.size();
    }

    public static class AccessDeniedException extends RuntimeException {}
    public static class ExplanationNotFoundException extends RuntimeException {}
}
