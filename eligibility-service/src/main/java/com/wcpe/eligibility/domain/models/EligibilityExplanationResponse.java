package com.wcpe.eligibility.domain.models;

import java.util.List;
import java.util.UUID;

public record EligibilityExplanationResponse(
    UUID quoteId,
    UUID quoteOptionId,
    UUID scenarioId,
    int scenarioVersion,
    String productCode,
    String investorCode,
    String eligibilityStatus,
    Summary summary,
    List<Rule> rules,
    Audit audit
) {
    public record Summary(int passed, int failed, int warnings, int insufficientData) {}

    public record Rule(
        String ruleCode,
        String ruleName,
        String status,
        String severity,
        String reasonCode,
        String message,
        String actualDisplay,
        String thresholdDisplay,
        String ruleVersionId,
        String evidenceId,
        String remediationHint
    ) {}

    public record Audit(UUID auditPackageId, String resultHash, String ruleVersionGraphHash) {}
}
