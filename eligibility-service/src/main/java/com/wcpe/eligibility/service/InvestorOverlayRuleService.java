package com.wcpe.eligibility.service;

import com.wcpe.eligibility.config.InvestorOverlayRuleProperties;
import com.wcpe.eligibility.domain.hashing.Hashing;
import com.wcpe.eligibility.domain.models.*;
import com.wcpe.eligibility.repository.InvestorOverlayRuleRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class InvestorOverlayRuleService {
    private static final Set<String> ALLOWED_FACT_PATHS = Set.of(
        "representativeFico", "ltv", "loanAmount", "propertyState", "occupancyType",
        "propertyType", "documentationType", "ausType"
    );
    private static final Set<String> ALLOWED_OPERATORS = Set.of(
        "EQ", "IN", "NOT_IN", "GTE", "LTE", "BETWEEN", "PERCENT_LTE", "MONEY_LTE", "PRESENT"
    );

    private final InvestorOverlayRuleProperties properties;
    private final InvestorOverlayRuleRepository repository;

    public InvestorOverlayRuleService(InvestorOverlayRuleProperties properties, InvestorOverlayRuleRepository repository) {
        this.properties = properties;
        this.repository = repository;
    }

    public InvestorOverlayEvaluationResult evaluate(UUID tenantId, InvestorOverlayEvaluationRequest request) {
        UUID evaluationId = UUID.randomUUID();
        if (request == null || request.productCandidate() == null || request.facts() == null) {
            return result(evaluationId, List.of(systemDecision("INVALID_OVERLAY_REQUEST", "Investor overlay request is incomplete.")));
        }
        if (!"ELIGIBLE".equalsIgnoreCase(request.baseDecisionStatus()) && !"WARNING".equalsIgnoreCase(request.baseDecisionStatus())) {
            return result(evaluationId, List.of(systemDecision("BASE_DECISION_NOT_ELIGIBLE", "Investor overlays cannot loosen a base agency ineligible decision.")));
        }

        InvestorOverlayProductCandidate candidate = request.productCandidate();
        List<InvestorOverlayRuleSetConfig> ruleSets = new ArrayList<>();
        if (properties.isEnabled()) {
            ruleSets.addAll(properties.resolveConfigs(string(candidate.investorId()), string(candidate.productVersionId()), candidate.channel()));
        }
        try {
            ruleSets.addAll(repository.resolve(tenantId, string(candidate.investorId()), string(candidate.productVersionId()), candidate.channel(), request.asOfDate()));
        } catch (Exception ignored) {
            // Database-backed overlays are optional in local tests; fail closed below if no config exists.
        }
        if (ruleSets.isEmpty()) {
            return result(evaluationId, List.of(systemDecision("OVERLAY_SET_NOT_CONFIGURED", "No investor overlay set is configured for the requested tenant/investor/product/channel.")));
        }

        ruleSets.sort(Comparator.comparingInt(InvestorOverlayRuleSetConfig::precedence).reversed());
        InvestorOverlayRuleSetConfig bestSet = ruleSets.get(0);
        if (ruleSets.size() > 1 && ruleSets.get(1).precedence() == bestSet.precedence()) {
            return result(evaluationId, List.of(systemDecision("OVERLAY_CONFLICT", "Multiple investor overlay sets matched the same precedence.")));
        }

        List<InvestorOverlayDecision> decisions = new ArrayList<>();
        List<InvestorOverlayRuleRow> rows = bestSet.rows().stream()
            .sorted(Comparator.comparingInt(InvestorOverlayRuleRow::priority).thenComparing(InvestorOverlayRuleRow::ruleCode))
            .toList();
        for (int i = 0; i < rows.size(); i++) {
            InvestorOverlayRuleRow row = rows.get(i);
            if (i + 1 < rows.size() && rows.get(i + 1).priority() == row.priority()
                    && Objects.equals(rows.get(i + 1).factPath(), row.factPath())) {
                return result(evaluationId, List.of(systemDecision("OVERLAY_CONFLICT", "Contradictory investor overlay rules share the same precedence and priority.")));
            }
            InvestorOverlayDecision validation = validateRow(row);
            if (validation != null) return result(evaluationId, List.of(validation));
            decisions.add(evaluateRow(row, request.facts()));
        }
        InvestorOverlayEvaluationResult output = result(evaluationId, decisions);
        for (InvestorOverlayDecision decision : decisions) {
            repository.persistDecision(tenantId, request.scenarioId(), evaluationId, decision, output.resultHash());
        }
        return output;
    }

    private InvestorOverlayDecision validateRow(InvestorOverlayRuleRow row) {
        if (!ALLOWED_FACT_PATHS.contains(row.factPath())) {
            return systemDecision("UNSUPPORTED_OVERLAY_FACT_PATH", "Unsupported investor overlay fact path: " + row.factPath());
        }
        if (!ALLOWED_OPERATORS.contains(upper(row.operator()))) {
            return systemDecision("UNSUPPORTED_OVERLAY_OPERATOR", "Unsupported investor overlay operator: " + row.operator());
        }
        for (InvestorOverlayRuleRow.Condition condition : row.conditions()) {
            if (!ALLOWED_FACT_PATHS.contains(condition.factPath())) {
                return systemDecision("UNSUPPORTED_OVERLAY_FACT_PATH", "Unsupported investor overlay condition fact path: " + condition.factPath());
            }
            if (!ALLOWED_OPERATORS.contains(upper(condition.operator()))) {
                return systemDecision("UNSUPPORTED_OVERLAY_OPERATOR", "Unsupported investor overlay condition operator: " + condition.operator());
            }
        }
        return null;
    }

    private InvestorOverlayDecision evaluateRow(InvestorOverlayRuleRow row, Map<String, Object> facts) {
        Object actual = facts.get(row.factPath());
        if (!conditionsMatch(row, facts)) {
            return new InvestorOverlayDecision(
                row.overlayRuleId(), row.ruleCode(), "ELIGIBLE", "PASS", "OVERLAY_CONDITION_NOT_APPLICABLE",
                row.ruleCode() + " investor overlay condition did not match.", normalize(actual), threshold(row), versionId(row)
            );
        }
        boolean passes = compare(actual, row.operator(), row.comparisonValue(), row.secondaryValue());
        String status = passes ? "ELIGIBLE" : "INELIGIBLE";
        String severity = passes ? "PASS" : defaultString(row.severity(), "HARD_STOP");
        return new InvestorOverlayDecision(
            row.overlayRuleId(), row.ruleCode(), status, severity, row.reasonCode(),
            message(row, actual), normalize(actual), threshold(row), versionId(row)
        );
    }

    private boolean conditionsMatch(InvestorOverlayRuleRow row, Map<String, Object> facts) {
        for (InvestorOverlayRuleRow.Condition condition : row.conditions()) {
            if (!compare(facts.get(condition.factPath()), condition.operator(), condition.comparisonValue(), condition.secondaryValue())) {
                return false;
            }
        }
        return true;
    }

    private boolean compare(Object actual, String operator, String comparisonValue, String secondaryValue) {
        return switch (upper(operator)) {
            case "EQ" -> Objects.equals(normalize(actual), normalize(comparisonValue));
            case "IN" -> split(comparisonValue).contains(normalize(actual));
            case "NOT_IN" -> !split(comparisonValue).contains(normalize(actual));
            case "GTE" -> number(actual).compareTo(number(comparisonValue)) >= 0;
            case "LTE", "PERCENT_LTE", "MONEY_LTE" -> number(actual).compareTo(number(comparisonValue)) <= 0;
            case "BETWEEN" -> number(actual).compareTo(number(comparisonValue)) >= 0
                && number(actual).compareTo(number(secondaryValue)) <= 0;
            case "PRESENT" -> actual != null && !normalize(actual).isBlank();
            default -> false;
        };
    }

    private InvestorOverlayEvaluationResult result(UUID evaluationId, List<InvestorOverlayDecision> decisions) {
        String summary = decisions.stream().anyMatch(d -> "INELIGIBLE".equals(d.eligibilityStatus())) ? "INELIGIBLE"
            : decisions.stream().anyMatch(d -> "WARNING".equals(d.eligibilityStatus())) ? "WARNING" : "ELIGIBLE";
        String canonical = decisions.stream()
            .map(d -> d.ruleCode() + ":" + d.eligibilityStatus() + ":" + d.reasonCode() + ":" + d.actualValue() + ":" + d.thresholdValue())
            .reduce(summary, (a, b) -> a + "|" + b);
        return new InvestorOverlayEvaluationResult(evaluationId, summary, decisions, Hashing.sha256(canonical));
    }

    private InvestorOverlayDecision systemDecision(String reasonCode, String message) {
        return new InvestorOverlayDecision(null, "INVESTOR_OVERLAY", "INELIGIBLE", "HARD_STOP", reasonCode, message, null, null, null);
    }

    private BigDecimal number(Object value) {
        if (value == null) return BigDecimal.ZERO;
        return new BigDecimal(normalize(value));
    }

    private List<String> split(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split(",")).map(this::normalize).toList();
    }

    private String message(InvestorOverlayRuleRow row, Object actual) {
        return defaultString(row.messageTemplate(), row.ruleCode() + " investor overlay evaluated.")
            .replace("{{actualValue}}", normalize(actual))
            .replace("{{thresholdValue}}", threshold(row));
    }

    private String threshold(InvestorOverlayRuleRow row) {
        return row.secondaryValue() == null ? row.comparisonValue() : row.comparisonValue() + ":" + row.secondaryValue();
    }

    private UUID versionId(InvestorOverlayRuleRow row) {
        return UUID.nameUUIDFromBytes((row.overlaySetId() + ":" + row.version()).getBytes(StandardCharsets.UTF_8));
    }

    private String normalize(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private String upper(String value) {
        return normalize(value).toUpperCase(Locale.ROOT);
    }

    private String string(UUID value) {
        return value == null ? null : value.toString();
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
