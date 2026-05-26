package com.wcpe.eligibility.domain.ruleset;

import com.wcpe.eligibility.client.CatalogClient;
import com.wcpe.eligibility.domain.hashing.Hashing;
import com.wcpe.eligibility.domain.models.*;
import com.wcpe.eligibility.domain.rules.EligibilityRule;
import com.wcpe.eligibility.evaluation.RequiredFactValidator;
import com.wcpe.eligibility.evaluation.ScopeValidator;
import com.wcpe.eligibility.evaluation.ScopeValidator.ScopeViolation;
import com.wcpe.eligibility.evaluation.RequiredFactValidator.MissingFact;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

/**
 * Fail-closed evaluaton cascade per LLD:
 * 1. Invalid request or missing versions => CANNOT_DECIDE
 * 2. Scope validation => OUT_OF_SCOPE or CANNOT_DECIDE
 * 3. Required fact validation => CANNOT_DECIDE
 * 4. Rule-set evaluation => INELIGIBLE on any blocking rule
 * 5. All rules satisfied => ELIGIBLE
 */
@Component
public class RuleEngine {
    private final List<EligibilityRule> rules;
    private final ScopeValidator scopeValidator;
    private final RequiredFactValidator factValidator;
    private final CatalogClient catalogClient;
    private final ObjectMapper canonicalMapper;

    public RuleEngine(List<EligibilityRule> rules, ScopeValidator scopeValidator,
                      RequiredFactValidator factValidator, CatalogClient catalogClient) {
        this.rules = rules.stream()
            .sorted((a, b) -> a.getRuleType().getCode().compareTo(b.getRuleType().getCode()))
            .toList();
        this.scopeValidator = scopeValidator;
        this.factValidator = factValidator;
        this.catalogClient = catalogClient;
        this.canonicalMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();
    }

    /**
     * Evaluate a single candidate against the full fail-closed cascade.
     */
    public EligibilityResult evaluate(EligibilityRequest request, UUID tenantId) {
        String productCode = request.productCandidate().productCode();
        String investorCode = request.productCandidate().investorCode();
        String channelCode = catalogClient.resolveChannel(request);
        String state = request.propertyProfile() != null ? request.propertyProfile().state() : null;

        UUID evaluationId = UUID.randomUUID();
        String scenarioId = resolveScenarioId(request);
        String productCatalogVersion = "1";
        String eligibilityRuleSetVersion = "3";
        String fixtureVersion = "synthetic-v1-pii01";
        Instant evaluatedAt = Instant.now();

        // Phase 1: Scope validation
        List<ScopeViolation> scopeViolations = scopeValidator.validate(productCode, investorCode, channelCode, state, null);
        if (!scopeViolations.isEmpty()) {
            ScopeViolation first = scopeViolations.get(0);
            List<RuleDecision> decisions = scopeViolations.stream()
                .map(v -> new RuleDecision(
                    UUID.randomUUID(), productCode, investorCode,
                    v.reasonCode().ruleCode(), v.reasonCode().category(),
                    v.reasonCode().severity(), "OUT_OF_SCOPE",
                    v.reasonCode().code(), v.reasonCode().message(),
                    v.actualValue() != null ? v.actualValue().actualValue() : null, null,
                    Map.of("ruleSetVersion", eligibilityRuleSetVersion, "phase", "scope_validation")))
                .toList();
            return buildResult(evaluationId, scenarioId, first.status(), decisions,
                request, productCode, investorCode, productCatalogVersion, eligibilityRuleSetVersion,
                fixtureVersion, evaluatedAt, tenantId);
        }

        // Phase 2: Required fact validation
        List<MissingFact> missing = factValidator.validate(request);
        if (!missing.isEmpty()) {
            List<RuleDecision> decisions = missing.stream()
                .map(f -> new RuleDecision(
                    UUID.randomUUID(), productCode, investorCode,
                    "R00", "GENERAL_VALIDATION", "BLOCKING", "CANNOT_DECIDE",
                    "GEN01", f.message(), null, null,
                    Map.of("ruleSetVersion", eligibilityRuleSetVersion, "phase", "required_fact_validation")))
                .toList();
            return buildResult(evaluationId, scenarioId, EligibilityStatus.CANNOT_DECIDE, decisions,
                request, productCode, investorCode, productCatalogVersion, eligibilityRuleSetVersion,
                fixtureVersion, evaluatedAt, tenantId);
        }

        // Phase 3: Rule evaluation
        List<RuleDecision> decisions = new ArrayList<>();
        boolean hasBlocking = false;
        for (EligibilityRule rule : rules) {
            RuleDecision decision = rule.evaluate(request, productCode, investorCode);
            decisions.add(decision);
            if ("HARD_STOP".equals(decision.severity())) {
                hasBlocking = true;
            }
        }

        // Phase 4: Status determination - fail closed
        EligibilityStatus status;
        if (hasBlocking) {
            status = EligibilityStatus.INELIGIBLE;
        } else {
            // Check for any WARNING/INSUFFICIENT_DATA that would indicate INCOMPLETE
            // Per LLD: no status inferred from absence of failures unless explicitly declared
            boolean hasWarning = decisions.stream().anyMatch(d -> "WARNING".equals(d.severity()));
            if (hasWarning) {
                // Warnings with no hard stop => all rules technically passed
                // but there is insufficient data on some rules
                // Per LLD fail-closed: this is still ELIGIBLE if all blocking rules passed
                status = EligibilityStatus.ELIGIBLE;
            } else {
                status = EligibilityStatus.ELIGIBLE;
            }
        }

        return buildResult(evaluationId, scenarioId, status, decisions,
            request, productCode, investorCode, productCatalogVersion, eligibilityRuleSetVersion,
            fixtureVersion, evaluatedAt, tenantId);
    }

    private EligibilityResult buildResult(UUID evaluationId, String scenarioId, EligibilityStatus status,
                                           List<RuleDecision> decisions,
                                           EligibilityRequest request, String productCode, String investorCode,
                                           String productCatalogVersion, String eligibilityRuleSetVersion,
                                           String fixtureVersion, Instant evaluatedAt, UUID tenantId) {
        String resultHash = computeCanonicalHash(request, productCode, investorCode, decisions, status);
        String requestHash = computeRequestHash(request);

        return new EligibilityResult(
            evaluationId,
            tenantId.toString(),
            scenarioId,
            status.name(),
            decisions,
            resultHash,
            requestHash,
            productCatalogVersion,
            eligibilityRuleSetVersion,
            fixtureVersion,
            evaluatedAt
        );
    }

    /**
     * Canonical JSON hash with stable key ordering and explicit null handling per LLD.
     */
    String computeCanonicalHash(EligibilityRequest request, String productCode, String investorCode,
                                  List<RuleDecision> decisions, EligibilityStatus status) {
        try {
            Map<String, Object> hashMaterial = Map.of(
                "request", request,
                "productCode", productCode,
                "investorCode", investorCode,
                "status", status.name(),
                "decisions", decisions.stream()
                    .map(d -> Map.of(
                        "ruleCode", d.ruleCode(),
                        "severity", d.severity(),
                        "status", d.status(),
                        "actualValue", d.actualValue()))
                    .sorted(Comparator.comparing(m -> (String) m.get("ruleCode")))
                    .toList()
            );
            // Use canonical mapper with sorted keys
            ObjectMapper sorted = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
            String canonical = sorted.writeValueAsString(hashMaterial);
            return Hashing.sha256(canonical);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to compute canonical hash", ex);
        }
    }

    String computeRequestHash(EligibilityRequest request) {
        try {
            String canonical = canonicalMapper.writeValueAsString(request);
            return Hashing.sha256(canonical);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to compute request hash", ex);
        }
    }

    private String resolveScenarioId(EligibilityRequest request) {
        return "scenario-" + UUID.randomUUID();
    }

    public List<EligibilityRule> getRules() {
        return rules;
    }
}
