package com.wcpe.eligibility.domain.rules;

import com.wcpe.eligibility.domain.models.EligibilityRequest;
import com.wcpe.eligibility.domain.models.RuleDecision;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class DocumentationTypeRule implements EligibilityRule {
    private static final Set<String> ALLOWED_DOC_TYPES = Set.of("FULL_DOC", "LOW_DOC");

    @Override
    public RuleType getRuleType() {
        return RuleType.R12;
    }

    @Override
    public RuleDecision evaluate(EligibilityRequest request, String productCode, String investorCode) {
        String docType = request.loanProfile().documentationType();

        if (docType == null || docType.isBlank()) {
            return new RuleDecision(
                UUID.randomUUID(), productCode, investorCode,
                "R12", "DOCUMENTATION_TYPE", "WARNING", "INSUFFICIENT_DATA",
                "DC01", "Documentation type is missing.",
                null, "FULL_DOC,LOW_DOC",
                Map.of("ruleSetVersion", 3, "deterministic", true)
            );
        }

        boolean pass = ALLOWED_DOC_TYPES.contains(docType);

        return new RuleDecision(
            UUID.randomUUID(), productCode, investorCode,
            "R12", "DOCUMENTATION_TYPE",
            pass ? "PASS" : "HARD_STOP",
            pass ? "ELIGIBLE" : "INELIGIBLE",
            pass ? null : "DC01",
            pass ? "Documentation type is acceptable." : "Documentation type is not acceptable.",
            docType, "FULL_DOC,LOW_DOC",
            Map.of("ruleSetVersion", 3, "deterministic", true)
        );
    }
}
