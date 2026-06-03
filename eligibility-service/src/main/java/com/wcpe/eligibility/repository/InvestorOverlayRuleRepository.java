package com.wcpe.eligibility.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.eligibility.domain.models.InvestorOverlayDecision;
import com.wcpe.eligibility.domain.models.InvestorOverlayRuleRow;
import com.wcpe.eligibility.domain.models.InvestorOverlayRuleSetConfig;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Repository
public class InvestorOverlayRuleRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public InvestorOverlayRuleRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<InvestorOverlayRuleSetConfig> resolve(UUID tenantId, String investorId, String productVersionId,
                                                       String channel, String asOfDate) {
        if (jdbc == null) return List.of();
        String effectiveDate = asOfDate != null && !asOfDate.isBlank() ? asOfDate : LocalDate.now().toString();
        List<UUID> setIds = jdbc.query(
            "SELECT overlay_set_id FROM eligibility.investor_overlay_set " +
                "WHERE tenant_id = ? AND status = 'PUBLISHED' AND investor_id = ?::uuid AND " +
                "(product_version_id IS NULL OR product_version_id = ?::uuid) AND " +
                "(channel IS NULL OR channel = ?) AND effective_from <= ? AND " +
                "(effective_to IS NULL OR effective_to >= ?) ORDER BY version DESC",
            (rs, rowNum) -> rs.getObject("overlay_set_id", UUID.class),
            tenantId, investorId, productVersionId, channel, effectiveDate, effectiveDate
        );
        List<InvestorOverlayRuleSetConfig> configs = new ArrayList<>();
        for (UUID setId : setIds) {
            String dbInvestorId = jdbc.queryForObject("SELECT investor_id::text FROM eligibility.investor_overlay_set WHERE overlay_set_id = ?", String.class, setId);
            String dbProductVersionId = jdbc.queryForObject("SELECT product_version_id::text FROM eligibility.investor_overlay_set WHERE overlay_set_id = ?", String.class, setId);
            String dbChannel = jdbc.queryForObject("SELECT channel FROM eligibility.investor_overlay_set WHERE overlay_set_id = ?", String.class, setId);
            int version = jdbc.queryForObject("SELECT version FROM eligibility.investor_overlay_set WHERE overlay_set_id = ?", Integer.class, setId);
            List<InvestorOverlayRuleRow> rows = jdbc.query(
                "SELECT overlay_rule_id, overlay_set_id, rule_code, fact_path, operator, comparison_value, " +
                    "secondary_value, value_type, condition_expression_json::text AS condition_expression_json, " +
                    "severity, reason_code, message_template, priority " +
                    "FROM eligibility.investor_overlay_rule WHERE tenant_id = ? AND overlay_set_id = ? ORDER BY priority, rule_code",
                (rs, rowNum) -> new InvestorOverlayRuleRow(
                    rs.getObject("overlay_rule_id", UUID.class),
                    rs.getObject("overlay_set_id", UUID.class),
                    rs.getString("rule_code"),
                    rs.getString("fact_path"),
                    rs.getString("operator"),
                    rs.getString("comparison_value"),
                    rs.getString("secondary_value"),
                    rs.getString("value_type"),
                    parseConditions(rs.getString("condition_expression_json")),
                    rs.getString("severity"),
                    rs.getString("reason_code"),
                    rs.getString("message_template"),
                    rs.getInt("priority"),
                    version
                ),
                tenantId, setId
            );
            configs.add(new InvestorOverlayRuleSetConfig(setId, dbInvestorId, dbProductVersionId, dbChannel,
                version, computePrecedence(dbProductVersionId, dbChannel, dbInvestorId), rows));
        }
        return configs;
    }

    public void persistDecision(UUID tenantId, UUID scenarioId, UUID evaluationId, InvestorOverlayDecision decision, String resultHash) {
        if (jdbc == null) return;
        jdbc.update(
            "INSERT INTO eligibility.eligibility_decision_investor_overlay " +
                "(tenant_id, scenario_id, evaluation_id, overlay_rule_id, rule_code, eligibility_status, severity, " +
                "reason_code, actual_value, threshold_value, rule_version_id, result_hash, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())",
            tenantId, scenarioId, evaluationId, decision.overlayRuleId(), decision.ruleCode(),
            decision.eligibilityStatus(), decision.severity(), decision.reasonCode(), decision.actualValue(),
            decision.thresholdValue(), decision.ruleVersionId(), resultHash
        );
    }

    public List<InvestorOverlayRuleRow.Condition> parseConditions(String json) {
        if (json == null || json.isBlank() || "null".equalsIgnoreCase(json)) return List.of();
        try {
            List<ConditionPayload> payloads;
            if (json.trim().startsWith("[")) {
                payloads = objectMapper.readValue(json, new TypeReference<List<ConditionPayload>>() {});
            } else {
                payloads = List.of(objectMapper.readValue(json, ConditionPayload.class));
            }
            return payloads.stream()
                .map(c -> new InvestorOverlayRuleRow.Condition(c.factPath, c.operator, c.comparisonValue, c.secondaryValue))
                .toList();
        } catch (Exception ex) {
            return List.of(new InvestorOverlayRuleRow.Condition("__unsupported_condition_json__", "UNSUPPORTED", null, null));
        }
    }

    private static class ConditionPayload {
        @JsonAlias("fact_path")
        public String factPath;
        @JsonAlias("operator")
        public String operator;
        @JsonAlias("comparison_value")
        public String comparisonValue;
        @JsonAlias("secondary_value")
        public String secondaryValue;
    }

    private int computePrecedence(String productVersionId, String channel, String investorId) {
        if (productVersionId != null && !productVersionId.isBlank()) return 3;
        if (investorId != null && !investorId.isBlank() && channel != null && !channel.isBlank()) return 2;
        if (investorId != null && !investorId.isBlank()) return 1;
        return 0;
    }
}
