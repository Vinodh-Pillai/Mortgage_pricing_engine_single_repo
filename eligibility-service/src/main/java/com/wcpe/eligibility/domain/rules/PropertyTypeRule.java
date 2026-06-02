package com.wcpe.eligibility.domain.rules;

import com.wcpe.eligibility.domain.models.EligibilityRequest;
import com.wcpe.eligibility.domain.models.PropertyTypeEvaluationRequest;
import com.wcpe.eligibility.domain.models.PropertyTypeFacts;
import com.wcpe.eligibility.domain.models.PropertyTypeProductCandidate;
import com.wcpe.eligibility.domain.models.RuleDecision;
import com.wcpe.eligibility.service.PropertyTypeRuleService;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
public class PropertyTypeRule implements EligibilityRule {

    private final PropertyTypeRuleService propertyTypeRuleService;

    public PropertyTypeRule(PropertyTypeRuleService propertyTypeRuleService) {
        this.propertyTypeRuleService = propertyTypeRuleService;
    }

    @Override
    public RuleType getRuleType() {
        return RuleType.R04;
    }

    @Override
    public RuleDecision evaluate(EligibilityRequest request, String productCode, String investorCode) {
        String propertyType = request.propertyProfile().propertyType();
        int units = request.propertyProfile().units();
        String occupancyType = request.propertyProfile().occupancyType();

        UUID tenantId = UUID.fromString(tenantIdFromRequest(request));
        String channel = resolveChannel(request);
        String loanPurpose = "PURCHASE";
        String projectReviewStatus = "UNKNOWN";

        // Validate required property type
        if (propertyType == null || propertyType.isBlank()) {
            return new RuleDecision(
                UUID.randomUUID(), productCode, investorCode,
                "R04", "PROPERTY_TYPE", "WARNING", "INSUFFICIENT_DATA",
                "PT02", "Property type is missing.",
                null, null,
                Map.of("ruleSetVersion", 4, "deterministic", true)
            );
        }

        if (units < 1 || units > 4) {
            return new RuleDecision(
                UUID.randomUUID(), productCode, investorCode,
                "R04", "PROPERTY_TYPE", "WARNING", "INSUFFICIENT_DATA",
                "INVALID_UNITS", "Units must be between 1 and 4.",
                String.valueOf(units), "1-4",
                Map.of("ruleSetVersion", 4, "deterministic", true)
            );
        }

        try {
            PropertyTypeFacts facts = new PropertyTypeFacts(
                propertyType,
                units,
                occupancyType,
                loanPurpose,
                projectReviewStatus
            );

            PropertyTypeProductCandidate candidate = new PropertyTypeProductCandidate(
                request.productCandidate().productVersionId(),
                productCode,
                investorCode,
                channel
            );

            PropertyTypeEvaluationRequest evalRequest = new PropertyTypeEvaluationRequest(
                UUID.fromString("scenario-placeholder"),
                1,
                null,
                candidate,
                facts
            );

            var result = propertyTypeRuleService.evaluate(tenantId, evalRequest);

            String severity = mapToSeverity(result.decision().eligibilityStatus());

            return new RuleDecision(
                UUID.randomUUID(), productCode, investorCode,
                "R04", "PROPERTY_TYPE", severity, result.decision().eligibilityStatus(),
                result.decision().reasonCode(), result.decision().message(),
                propertyType, null,
                Map.of(
                    "ruleSetVersion", 4,
                    "matchedRuleId", result.decision().matchedRuleId() != null ? result.decision().matchedRuleId().toString() : null,
                    "projectReviewRequirement", result.decision().projectReviewRequirement(),
                    "deterministic", true
                )
            );
        } catch (Exception e) {
            return new RuleDecision(
                UUID.randomUUID(), productCode, investorCode,
                "R04", "PROPERTY_TYPE", "WARNING", "CANNOT_DECIDE",
                "RULE_RESOLUTION_ERROR", "Property type rule resolution failed: " + e.getMessage(),
                propertyType, null,
                Map.of("ruleSetVersion", 4, "deterministic", false)
            );
        }
    }

    private String tenantIdFromRequest(EligibilityRequest request) {
        return request != null ? request.productCandidate().productVersionId().toString() : "00000000-0000-0000-0000-000000000000";
    }

    private String resolveChannel(EligibilityRequest request) {
        return "RETAIL";
    }

    private String mapToSeverity(String eligibilityStatus) {
        switch (eligibilityStatus) {
            case "ELIGIBLE":
                return "PASS";
            case "INELIGIBLE":
                return "HARD_STOP";
            case "WARNING":
                return "WARNING";
            default:
                return "WARNING";
        }
    }
}
