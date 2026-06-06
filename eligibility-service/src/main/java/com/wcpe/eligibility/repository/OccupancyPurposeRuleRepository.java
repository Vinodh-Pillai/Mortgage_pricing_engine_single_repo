package com.wcpe.eligibility.repository;

import com.wcpe.eligibility.domain.models.OccupancyPurposeRuleRow;
import com.wcpe.eligibility.domain.models.OccupancyPurposeRuleSetConfig;
import com.wcpe.eligibility.domain.models.OccupancyPurposeEvaluationRequest;
import com.wcpe.eligibility.domain.models.OccupancyPurposeEvaluationResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public class OccupancyPurposeRuleRepository {

    private static final int SPECIFICITY_VERSION_MULTIPLIER = 1_000_000;

    private final JdbcTemplate jdbc;

    public OccupancyPurposeRuleRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<OccupancyPurposeRuleSetConfig> resolve(UUID tenantId, String productCode, String investorCode,
                                                         String channel, Date effectiveDate) {
        if (jdbc == null) {
            return List.of();
        }
        String effectiveDateStr = effectiveDate != null ? effectiveDate.toString() : LocalDate.now().toString();

        List<UUID> ruleSetIds = jdbc.query(
            "SELECT rule_set_id FROM eligibility.occupancy_purpose_rule_set " +
            "WHERE tenant_id = ? AND status = 'PUBLISHED' AND " +
            "(product_code IS NULL OR product_code = ?) AND " +
            "(investor_code IS NULL OR investor_code = ?) AND " +
            "(channel IS NULL OR channel = ?) AND " +
            "effective_from <= ? AND (effective_to IS NULL OR effective_to >= ?) " +
            "ORDER BY version DESC",
            (rs, rowNum) -> rs.getObject("rule_set_id", UUID.class),
            tenantId, productCode, investorCode, channel, effectiveDateStr, effectiveDateStr
        );

        if (ruleSetIds.isEmpty()) {
            return List.of();
        }

        List<OccupancyPurposeRuleSetConfig> configs = new java.util.ArrayList<>();
        for (UUID ruleSetId : ruleSetIds) {
            RuleSetScope scope = jdbc.queryForObject(
                "SELECT product_family, product_code, product_version_id, investor_code, investor_id, channel, version " +
                "FROM eligibility.occupancy_purpose_rule_set WHERE rule_set_id = ?",
                (rs, rowNum) -> new RuleSetScope(
                    rs.getString("product_family"),
                    rs.getString("product_code"),
                    rs.getObject("product_version_id", UUID.class),
                    rs.getString("investor_code"),
                    rs.getObject("investor_id", UUID.class),
                    rs.getString("channel"),
                    rs.getInt("version")
                ),
                ruleSetId
            );

            List<OccupancyPurposeRuleRow> rows = jdbc.query(
                "SELECT rule_id, rule_set_id, loan_purpose, occupancy_type, property_type, " +
                "units_min, units_max, decision, severity, reason_code, message_template, priority " +
                "FROM eligibility.occupancy_purpose_rule " +
                "WHERE tenant_id = ? AND rule_set_id = ? " +
                "ORDER BY priority, loan_purpose, occupancy_type",
                (rs, rowNum) -> new OccupancyPurposeRuleRow(
                    rs.getObject("rule_id", UUID.class),
                    rs.getObject("rule_set_id", UUID.class),
                    rs.getString("loan_purpose"),
                    rs.getString("occupancy_type"),
                    rs.getString("property_type"),
                    rs.getInt("units_min"),
                    rs.getInt("units_max"),
                    rs.getString("decision"),
                    rs.getString("severity"),
                    rs.getString("reason_code"),
                    rs.getString("message_template"),
                    rs.getInt("priority")
                ),
                tenantId, ruleSetId
            );

            int precedence = specificityPrecedence(
                scope.productFamily(), scope.productCode(), scope.productVersionId(),
                scope.investorCode(), scope.investorId(), scope.channel(), scope.version()
            );

            configs.add(new OccupancyPurposeRuleSetConfig(
                ruleSetId, scope.productFamily(), scope.investorCode() == null ? "" : scope.investorCode(),
                scope.channel(), precedence, rows
            ));
        }

        return configs;
    }

    public void persistEvaluation(UUID tenantId, OccupancyPurposeEvaluationRequest request,
                                  OccupancyPurposeEvaluationResult result) {
        if (jdbc == null || tenantId == null || request == null || request.facts() == null || result == null) {
            return;
        }
        UUID scenarioId = request.scenarioId() != null ? request.scenarioId() : result.evaluationId();
        String matchedRuleId = result.decision().matchedRuleId();
        String matchedRuleSetId = result.decision().matchedRuleSetId();
        jdbc.update(
            "INSERT INTO eligibility.eligibility_decision_occupancy_purpose " +
            "(tenant_id, decision_id, scenario_id, scenario_version, loan_purpose, occupancy_type, property_type, units, " +
            "matched_rule_id, rule_set_id, eligibility_status, severity, reason_code, result_hash) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::uuid, ?::uuid, ?, ?, ?, ?) " +
            "ON CONFLICT (tenant_id, decision_id) DO NOTHING",
            tenantId,
            result.evaluationId(),
            scenarioId,
            request.scenarioVersion(),
            request.facts().loanPurpose(),
            request.facts().occupancyType(),
            request.facts().propertyType(),
            request.facts().units(),
            matchedRuleId,
            matchedRuleSetId,
            result.decision().eligibilityStatus(),
            result.decision().severity(),
            result.decision().reasonCode(),
            result.resultHash()
        );
        jdbc.update(
            "INSERT INTO eligibility.occupancy_purpose_outbox_event " +
            "(tenant_id, aggregate_id, event_type, payload) VALUES (?, ?, 'occupancy_purpose_rules.completed.v1', ?::jsonb)",
            tenantId,
            scenarioId,
            eventPayload(result)
        );
        jdbc.update(
            "INSERT INTO eligibility.occupancy_purpose_metrics (tenant_id, metric_name, status, reason_code) VALUES (?, 'occupancy_purpose_decision_total', ?, ?)",
            tenantId,
            result.decision().eligibilityStatus(),
            result.decision().reasonCode()
        );
    }

    private String eventPayload(OccupancyPurposeEvaluationResult result) {
        return "{\"evaluationId\":\"" + result.evaluationId() + "\","
            + "\"status\":\"" + safe(result.decision().eligibilityStatus()) + "\","
            + "\"reasonCode\":\"" + safe(result.decision().reasonCode()) + "\","
            + "\"resultHash\":\"" + safe(result.resultHash()) + "\"}";
    }

    private String safe(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public static int specificityPrecedence(String productFamily, String productCode, UUID productVersionId,
                                            String investorCode, UUID investorId, String channel, int version) {
        int specificity = 0;
        if (present(productCode) || productVersionId != null) {
            specificity += 16;
        }
        if (present(investorCode) || investorId != null) {
            specificity += 8;
        }
        if (present(channel)) {
            specificity += 4;
        }
        if (present(productFamily)) {
            specificity += 2;
        }
        return specificity * SPECIFICITY_VERSION_MULTIPLIER + Math.max(version, 0);
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private record RuleSetScope(String productFamily, String productCode, UUID productVersionId,
                                String investorCode, UUID investorId, String channel, int version) {}
}
