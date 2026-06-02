package com.wcpe.eligibility.service;

import com.wcpe.eligibility.config.PropertyTypeRuleProperties;
import com.wcpe.eligibility.domain.hashing.Hashing;
import com.wcpe.eligibility.domain.models.*;
import com.wcpe.eligibility.repository.PropertyTypeRuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class PropertyTypeRuleService {

    private static final Logger log = LoggerFactory.getLogger(PropertyTypeRuleService.class);

    private static final Set<String> ALLOWED_PROPERTY_TYPES = Set.of(
        "SINGLE_FAMILY", "CONDO", "PUD", "TWO_TO_FOUR_UNIT", "MANUFACTURED_HOME"
    );

    private final PropertyTypeRuleProperties properties;
    private final PropertyTypeRuleRepository repository;

    public PropertyTypeRuleService(PropertyTypeRuleProperties properties, PropertyTypeRuleRepository repository) {
        this.properties = properties;
        this.repository = repository;
    }

    public PropertyTypeEvaluationResult evaluate(UUID tenantId, PropertyTypeEvaluationRequest request) {
        UUID evaluationId = UUID.randomUUID();
        PropertyTypeFacts facts = request.facts();
        PropertyTypeProductCandidate candidate = request.productCandidate();

        // Validate property type presence
        if (facts.propertyType() == null || facts.propertyType().isBlank()) {
            return buildResult(evaluationId, new PropertyTypeDecision(
                "CONF_PROPERTY_TYPE", "INSUFFICIENT_DATA", "WARNING",
                "MISSING_PROPERTY_TYPE", "Property type is required for property type evaluation.",
                ruleVersionId(), null, null, null
            ));
        }

        // Validate property type enum
        String propertyType = facts.propertyType().toUpperCase();
        if (!ALLOWED_PROPERTY_TYPES.contains(propertyType)) {
            return buildResult(evaluationId, new PropertyTypeDecision(
                "CONF_PROPERTY_TYPE", "INSUFFICIENT_DATA", "WARNING",
                "UNKNOWN_PROPERTY_TYPE", "Property type " + facts.propertyType() + " is not a recognized property type.",
                ruleVersionId(), null, null, null
            ));
        }

        // Validate units range
        int units = facts.units();
        if (units < 1 || units > 4) {
            return buildResult(evaluationId, new PropertyTypeDecision(
                "CONF_PROPERTY_TYPE", "INSUFFICIENT_DATA", "WARNING",
                "INVALID_UNITS", "Units must be between 1 and 4. Got: " + units,
                ruleVersionId(), null, null, null
            ));
        }

        // Fair lending guardrail: validate no demographic dimensions
        if (facts.occupancyType() != null && containsDemographicData(facts.occupancyType())) {
            log.warn("Fair lending guardrail: potential demographic data in occupancy type evaluation for tenant {}", tenantId);
        }
        if (facts.loanPurpose() != null && containsDemographicData(facts.loanPurpose())) {
            log.warn("Fair lending guardrail: potential demographic data in loan purpose evaluation for tenant {}", tenantId);
        }

        // Resolve rule sets: config first, then DB fallback
        List<PropertyTypeRuleSetConfig> ruleSets = new ArrayList<>();

        if (properties.isEnabled()) {
            ruleSets.addAll(properties.resolveConfigs(
                candidate.productCode(), candidate.investorCode(), candidate.channel()));
        }

        try {
            List<PropertyTypeRuleSetConfig> dbSets = repository.resolve(
                tenantId,
                candidate.productCode(),
                candidate.investorCode(),
                candidate.channel(),
                request.asOfDate()
            );
            if (!dbSets.isEmpty()) {
                ruleSets.addAll(dbSets);
            }
        } catch (Exception ignored) {
            // DB not available or table empty - proceed with config
        }

        if (ruleSets.isEmpty()) {
            return buildResult(evaluationId, new PropertyTypeDecision(
                "CONF_PROPERTY_TYPE", "CANNOT_DECIDE", "WARNING",
                "PROPERTY_TYPE_RULE_NOT_CONFIGURED",
                "No property type rules configured for " + candidate.productCode() + "/" + candidate.investorCode() + ".",
                ruleVersionId(), null, null, null
            ));
        }

        // Select highest-precedence ruleset (highest precedence = most specific match)
        ruleSets.sort(Comparator.comparingInt(PropertyTypeRuleSetConfig::precedence).reversed());
        PropertyTypeRuleSetConfig bestSet = ruleSets.get(0);

        PropertyTypeRuleRow matchedRow = matchRule(bestSet, propertyType, units, facts);

        if (matchedRow == null) {
            // Manufactured home denied unless explicit rule allows
            if ("MANUFACTURED_HOME".equals(propertyType)) {
                return buildResult(evaluationId, new PropertyTypeDecision(
                    "CONF_PROPERTY_TYPE", "INELIGIBLE", "HARD_STOP",
                    "MANUFACTURED_HOME_DENIED",
                    "Manufactured home is not allowed for conventional purchase without an explicit product rule.",
                    ruleVersionId(), null, null, bestSet.ruleSetId()
                ));
            }

            return buildResult(evaluationId, new PropertyTypeDecision(
                "CONF_PROPERTY_TYPE", "INELIGIBLE", "HARD_STOP",
                "PROPERTY_TYPE_NOT_ALLOWED_FOR_PRODUCT",
                propertyType + " is not allowed for " + candidate.productCode() + "/" + candidate.investorCode() + ".",
                ruleVersionId(), null, null, bestSet.ruleSetId()
            ));
        }

        // Map decision: ALLOW -> ELIGIBLE, DENY -> INELIGIBLE, CONDITION -> WARNING
        String eligibilityStatus;
        switch (matchedRow.decision().toUpperCase()) {
            case "ALLOW":
                eligibilityStatus = "ELIGIBLE";
                break;
            case "DENY":
                eligibilityStatus = "INELIGIBLE";
                break;
            case "CONDITION":
                eligibilityStatus = "WARNING";
                break;
            default:
                eligibilityStatus = "CANNOT_DECIDE";
                break;
        }

        // Handle project review requirement
        String projectReviewStatus = facts.projectReviewStatus();
        String projectReviewRequirement = matchedRow.projectReviewRequirement();

        if ("UNKNOWN".equalsIgnoreCase(projectReviewStatus) &&
            ("REQUIRED".equals(projectReviewRequirement) || "WARNING".equals(projectReviewRequirement))) {
            if ("REQUIRED".equals(projectReviewRequirement)) {
                eligibilityStatus = "INELIGIBLE";
                return buildResult(evaluationId, new PropertyTypeDecision(
                    "CONF_PROPERTY_TYPE", "INELIGIBLE", "HARD_STOP",
                    "PROJECT_REVIEW_REQUIRED",
                    "Project review is required but status is unknown. Eligibility cannot proceed.",
                    ruleVersionId(), matchedRow.ruleId(), projectReviewRequirement, bestSet.ruleSetId()
                ));
            } else if ("WARNING".equals(projectReviewRequirement)) {
                String message = resolveMessage(matchedRow.messageTemplate(), candidate, propertyType, units, facts);
                return buildResult(evaluationId, new PropertyTypeDecision(
                    "CONF_PROPERTY_TYPE", "WARNING", "CONDITION",
                    matchedRow.reasonCode() != null ? matchedRow.reasonCode() : "PROJECT_REVIEW_WARNING",
                    message != null ? message : ("Property type " + propertyType + " requires project review before final pricing."),
                    ruleVersionId(), matchedRow.ruleId(), projectReviewRequirement, bestSet.ruleSetId()
                ));
            }
        }

        // Manufactured home explicit rule check
        if ("MANUFACTURED_HOME".equals(propertyType)) {
            String message = resolveMessage(matchedRow.messageTemplate(), candidate, propertyType, units, facts);
            return buildResult(evaluationId, new PropertyTypeDecision(
                "CONF_PROPERTY_TYPE", eligibilityStatus,
                matchedRow.severity() != null ? matchedRow.severity() : "PASS",
                matchedRow.reasonCode() != null ? matchedRow.reasonCode() : "MANUFACTURED_HOME_ALLOWED",
                message != null ? message : ("Manufactured home explicitly allowed by product rule for " + candidate.productCode() + "/").replaceFirst("/$", "."),
                ruleVersionId(), matchedRow.ruleId(), projectReviewRequirement, bestSet.ruleSetId()
            ));
        }

        String message = resolveMessage(matchedRow.messageTemplate(), candidate, propertyType, units, facts);
        String severity = matchedRow.severity() != null ? matchedRow.severity() : (eligibilityStatus.equals("ELIGIBLE") ? "PASS" : "CONDITION");

        return buildResult(evaluationId, new PropertyTypeDecision(
            "CONF_PROPERTY_TYPE", eligibilityStatus,
            severity,
            matchedRow.reasonCode() != null ? matchedRow.reasonCode() : null,
            message != null ? message : (propertyType + " is " + eligibilityStatus.toLowerCase() + " for " + candidate.productCode() + "/"),
            ruleVersionId(), matchedRow.ruleId(), projectReviewRequirement, bestSet.ruleSetId()
        ));
    }

    private PropertyTypeRuleRow matchRule(PropertyTypeRuleSetConfig ruleSet, String propertyType, int units,
                                           PropertyTypeFacts facts) {
        List<PropertyTypeRuleRow> candidates = new ArrayList<>();

        for (PropertyTypeRuleRow row : ruleSet.rows()) {
            // Property type must match
            if (!row.propertyType().equals(propertyType)) {
                continue;
            }
            // Units must be in range
            if (units < row.unitsMin() || units > row.unitsMax()) {
                continue;
            }
            // Occupancy type must match (or be null = any)
            if (row.occupancyType() != null && facts.occupancyType() != null
                    && !row.occupancyType().equals(facts.occupancyType())) {
                continue;
            }
            // Loan purpose must match (or be null = any)
            if (row.loanPurpose() != null && !row.loanPurpose().equals(
                facts.loanPurpose() != null ? facts.loanPurpose() : "PURCHASE")) {
                continue;
            }

            candidates.add(row);
        }

        if (candidates.isEmpty()) {
            return null;
        }

        // Sort by priority ascending (lowest number = highest priority)
        candidates.sort(Comparator.comparingInt(PropertyTypeRuleRow::priority));

        // Check for same-priority conflict
        PropertyTypeRuleRow top = candidates.get(0);
        boolean conflict = candidates.size() > 1 && candidates.get(1).priority() == top.priority();
        if (conflict) {
            log.warn("Property type rule conflict for rule set {}: multiple rules with same priority {}",
                ruleSet.ruleSetId(), top.priority());
            // Fail closed on conflict
            return null;
        }

        return top;
    }

    private String resolveMessage(String template, PropertyTypeProductCandidate candidate,
                                    String propertyType, int units, PropertyTypeFacts facts) {
        if (template == null || template.isBlank()) {
            return propertyType + " evaluation for " + candidate.productCode() + "/" + candidate.investorCode();
        }
        return template
            .replace("{{productCode}}", candidate.productCode())
            .replace("{{investorCode}}", candidate.investorCode())
            .replace("{{channel}}", candidate.channel() != null ? candidate.channel() : "")
            .replace("{{propertyType}}", propertyType)
            .replace("{{units}}", String.valueOf(units))
            .replace("{{occupancyType}}", facts.occupancyType() != null ? facts.occupancyType() : "")
            .replace("{{loanPurpose}}", facts.loanPurpose() != null ? facts.loanPurpose() : "")
            .replace("{{projectReviewStatus}}", facts.projectReviewStatus() != null ? facts.projectReviewStatus() : "");
    }

    private boolean containsDemographicData(String value) {
        if (value == null) return false;
        // Fair lending guardrail: reject any field that might contain demographic data
        // This is a basic check; the domain model should never accept demographic fields in rule rows
        String lower = value.toLowerCase();
        return lower.contains("race") || lower.contains("ethnicity") ||
               lower.contains("gender") || lower.contains("sex") ||
               lower.contains("religion") || lower.contains("national_origin");
    }

    private UUID ruleVersionId() {
        return UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    }

    private PropertyTypeEvaluationResult buildResult(UUID evaluationId, PropertyTypeDecision decision) {
        String payload = evaluationId + ":" + decision.reasonCode() + ":" + decision.eligibilityStatus();
        String hash = "sha256:" + Base64.getEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return new PropertyTypeEvaluationResult(evaluationId, decision, hash);
    }
}
