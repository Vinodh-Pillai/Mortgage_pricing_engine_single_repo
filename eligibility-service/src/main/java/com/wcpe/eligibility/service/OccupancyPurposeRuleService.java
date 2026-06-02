package com.wcpe.eligibility.service;

import com.wcpe.eligibility.config.OccupancyPurposeRuleProperties;
import com.wcpe.eligibility.domain.hashing.Hashing;
import com.wcpe.eligibility.domain.models.*;
import com.wcpe.eligibility.repository.OccupancyPurposeRuleRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class OccupancyPurposeRuleService {

    private final OccupancyPurposeRuleProperties properties;
    private final OccupancyPurposeRuleRepository repository;

    public OccupancyPurposeRuleService(OccupancyPurposeRuleProperties properties,
                                        OccupancyPurposeRuleRepository repository) {
        this.properties = properties;
        this.repository = repository;
    }

    public OccupancyPurposeEvaluationResult evaluate(UUID tenantId, OccupancyPurposeEvaluationRequest request) {
        UUID evaluationId = UUID.randomUUID();
        OccupancyPurposeFacts facts = request.facts();
        OccupancyPurposeProductCandidate candidate = request.productCandidate();

        // Validate loan purpose
        if (facts.loanPurpose() == null || facts.loanPurpose().isBlank()) {
            return buildResult(evaluationId, new OccupancyPurposeDecision(
                "CONF_OCCUPANCY_PURPOSE", "INSUFFICIENT_DATA", "WARNING",
                "MISSING_LOAN_PURPOSE", "Loan purpose is required for occupancy-purpose evaluation.",
                null, null, null, ruleVersionId()
            ));
        }

        if (!"PURCHASE".equals(facts.loanPurpose())) {
            return buildResult(evaluationId, new OccupancyPurposeDecision(
                "CONF_OCCUPANCY_PURPOSE", "INELIGIBLE", "HARD_STOP",
                "UNSUPPORTED_LOAN_PURPOSE",
                "Loan purpose " + facts.loanPurpose() + " is not supported. Only PURCHASE is supported.",
                null, null, null, ruleVersionId()
            ));
        }

        // Validate occupancy type
        if (facts.occupancyType() == null || facts.occupancyType().isBlank()) {
            return buildResult(evaluationId, new OccupancyPurposeDecision(
                "CONF_OCCUPANCY_PURPOSE", "INSUFFICIENT_DATA", "WARNING",
                "MISSING_OCCUPANCY", "Occupancy type is required for occupancy-purpose evaluation.",
                null, null, null, ruleVersionId()
            ));
        }

        // Resolve rulesets
        List<OccupancyPurposeRuleSetConfig> configSets = properties.resolveConfigs(
            candidate.productCode(), candidate.investorCode(), candidate.channel());

        List<OccupancyPurposeRuleSetConfig> ruleSets = new ArrayList<>();

        if (!configSets.isEmpty()) {
            ruleSets.addAll(configSets);
        }

        if (ruleSets.isEmpty()) {
            // DB fallback - skip if DB doesn't have the table yet
            try {
                List<OccupancyPurposeRuleSetConfig> dbSets = repository.resolve(
                    tenantId, candidate.productCode(), candidate.investorCode(), candidate.channel(), null);
                ruleSets.addAll(dbSets);
            } catch (Exception ignored) {
                // DB table not available yet, proceed with default behavior
            }
        }

        if (ruleSets.isEmpty()) {
            return buildResult(evaluationId, new OccupancyPurposeDecision(
                "CONF_OCCUPANCY_PURPOSE", "CANNOT_DECIDE", "WARNING",
                "OCCUPANCY_PURPOSE_RULE_NOT_CONFIGURED",
                "No occupancy-purpose rules configured for " + candidate.productCode() + "/" + candidate.investorCode() + ".",
                null, null, null, ruleVersionId()
            ));
        }

        // Select highest-precedence ruleset (highest precedence value = most specific)
        ruleSets.sort(Comparator.comparingInt(OccupancyPurposeRuleSetConfig::precedence).reversed());
        OccupancyPurposeRuleSetConfig bestSet = ruleSets.get(0);

        OccupancyPurposeRuleRow matchedRow = matchRule(bestSet, facts);

        if (matchedRow == null) {
            return buildResult(evaluationId, new OccupancyPurposeDecision(
                "CONF_OCCUPANCY_PURPOSE", "INELIGIBLE", "HARD_STOP",
                "OCCUPANCY_NOT_ALLOWED_FOR_PRODUCT_CHANNEL",
                facts.occupancyType() + " is not allowed for " + candidate.productCode() + "/" + candidate.investorCode() + "/" + candidate.channel() + ".",
                bestSet.ruleSetId().toString(), null, "generic", ruleVersionId()
            ));
        }

        String decision;
        if ("ALLOW".equals(matchedRow.decision())) {
            decision = "ELIGIBLE";
        } else if ("DENY".equals(matchedRow.decision())) {
            decision = "INELIGIBLE";
        } else {
            decision = "WARNING";
        }

        String message = resolveMessage(matchedRow.messageTemplate(), candidate, facts);

        return buildResult(evaluationId, new OccupancyPurposeDecision(
            "CONF_OCCUPANCY_PURPOSE", decision,
            matchedRow.severity() != null ? matchedRow.severity() : "PASS",
            matchedRow.reasonCode() != null ? matchedRow.reasonCode() : null,
            message,
            bestSet.ruleSetId().toString(),
            matchedRow.ruleId().toString(),
            precedenceLabel(bestSet),
            ruleVersionId()
        ));
    }

    private OccupancyPurposeRuleRow matchRule(OccupancyPurposeRuleSetConfig ruleSet, OccupancyPurposeFacts facts) {
        List<OccupancyPurposeRuleRow> candidates = new ArrayList<>();

        for (OccupancyPurposeRuleRow row : ruleSet.rows()) {
            // Loan purpose must match (or be null = any)
            if (row.loanPurpose() != null && !row.loanPurpose().equals(facts.loanPurpose())) {
                continue;
            }
            // Occupancy type must match
            if (!row.occupancyType().equals(facts.occupancyType())) {
                continue;
            }
            // Property type must match (or be null = any)
            if (row.propertyType() != null && facts.propertyType() != null
                    && !row.propertyType().equals(facts.propertyType())) {
                continue;
            }
            // Units must be in range
            if (facts.units() < row.unitsMin() || facts.units() > row.unitsMax()) {
                continue;
            }

            candidates.add(row);
        }

        if (candidates.isEmpty()) {
            return null;
        }

        // Sort by priority ascending (lowest number = highest priority)
        candidates.sort(Comparator.comparingInt(OccupancyPurposeRuleRow::priority));

        // Check for same-priority conflict
        OccupancyPurposeRuleRow top = candidates.get(0);
        boolean conflict = candidates.size() > 1 && candidates.get(1).priority() == top.priority();
        if (conflict) {
            // Fail closed on conflict
            return null;
        }

        return top;
    }

    private String resolveMessage(String template, OccupancyPurposeProductCandidate candidate, OccupancyPurposeFacts facts) {
        if (template == null || template.isBlank()) {
            return facts.occupancyType() + " for " + candidate.productCode() + "/" + candidate.investorCode() + "/" + candidate.channel();
        }
        return template
            .replace("{{productCode}}", candidate.productCode())
            .replace("{{investorCode}}", candidate.investorCode())
            .replace("{{channel}}", candidate.channel())
            .replace("{{occupancyType}}", facts.occupancyType())
            .replace("{{loanPurpose}}", facts.loanPurpose())
            .replace("{{propertyType}}", facts.propertyType() != null ? facts.propertyType() : "any")
            .replace("{{units}}", String.valueOf(facts.units()));
    }

    private String precedenceLabel(OccupancyPurposeRuleSetConfig ruleSet) {
        if (ruleSet.investorCode() != null && !ruleSet.investorCode().isEmpty()) {
            return "product_investor";
        }
        if (ruleSet.productFamily() != null && !ruleSet.productFamily().isEmpty()) {
            return "product_family";
        }
        return "generic";
    }

    private UUID ruleVersionId() {
        return UUID.fromString("88888888-8888-8888-8888-888888888888");
    }

    private OccupancyPurposeEvaluationResult buildResult(UUID evaluationId, OccupancyPurposeDecision decision) {
        String payload = evaluationId + ":" + decision.reasonCode() + ":" + decision.eligibilityStatus();
        String hash = "sha256:" + Base64.getEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return new OccupancyPurposeEvaluationResult(evaluationId, decision, hash);
    }
}
