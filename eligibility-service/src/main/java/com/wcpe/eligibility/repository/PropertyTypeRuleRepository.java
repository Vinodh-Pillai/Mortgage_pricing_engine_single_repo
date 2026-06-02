package com.wcpe.eligibility.repository;

import com.wcpe.eligibility.domain.models.PropertyTypeRuleRow;
import com.wcpe.eligibility.domain.models.PropertyTypeRuleSetConfig;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public class PropertyTypeRuleRepository {

    private final JdbcTemplate jdbc;

    public PropertyTypeRuleRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<PropertyTypeRuleSetConfig> resolve(UUID tenantId, String productCode, String investorCode,
                                                    String channel, String asOfDate) {
        String effectiveDateStr = asOfDate != null && !asOfDate.isBlank() ? asOfDate : LocalDate.now().toString();

        List<UUID> ruleSetIds = jdbc.query(
            "SELECT rule_set_id FROM eligibility.property_type_rule_set " +
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

        List<PropertyTypeRuleSetConfig> configs = new java.util.ArrayList<>();
        for (UUID ruleSetId : ruleSetIds) {
            String productFamily = jdbc.queryForObject(
                "SELECT product_family FROM eligibility.property_type_rule_set WHERE rule_set_id = ?",
                String.class, ruleSetId
            );
            String ch = jdbc.queryForObject(
                "SELECT channel FROM eligibility.property_type_rule_set WHERE rule_set_id = ?",
                String.class, ruleSetId
            );
            String inv = jdbc.queryForObject(
                "SELECT COALESCE(investor_code, '') FROM eligibility.property_type_rule_set WHERE rule_set_id = ?",
                String.class, ruleSetId
            );
            String prodCode = jdbc.queryForObject(
                "SELECT COALESCE(product_code, '') FROM eligibility.property_type_rule_set WHERE rule_set_id = ?",
                String.class, ruleSetId
            );
            int version = jdbc.queryForObject(
                "SELECT version FROM eligibility.property_type_rule_set WHERE rule_set_id = ?",
                Integer.class, ruleSetId
            );

            List<PropertyTypeRuleRow> rows = jdbc.query(
                "SELECT rule_id, rule_set_id, property_type, units_min, units_max, " +
                "occupancy_type, loan_purpose, project_review_requirement, " +
                "decision, severity, reason_code, message_template, priority " +
                "FROM eligibility.property_type_rule " +
                "WHERE tenant_id = ? AND rule_set_id = ? " +
                "ORDER BY priority, property_type",
                (rs, rowNum) -> new PropertyTypeRuleRow(
                    rs.getObject("rule_id", UUID.class),
                    rs.getObject("rule_set_id", UUID.class),
                    rs.getString("property_type"),
                    rs.getInt("units_min"),
                    rs.getInt("units_max"),
                    rs.getString("occupancy_type"),
                    rs.getString("loan_purpose"),
                    rs.getString("project_review_requirement"),
                    rs.getString("decision"),
                    rs.getString("severity"),
                    rs.getString("reason_code"),
                    rs.getString("message_template"),
                    rs.getInt("priority")
                ),
                tenantId, ruleSetId
            );

            int precedence = computePrecedence(inv, ch, prodCode);

            configs.add(new PropertyTypeRuleSetConfig(
                ruleSetId, tenantId.toString(), productFamily, prodCode, inv, ch, version, precedence, rows
            ));
        }

        return configs;
    }

    private int computePrecedence(String investorCode, String channel, String productCode) {
        int p = 0;
        if (investorCode != null && !investorCode.isEmpty()) p += 4;
        if (channel != null && !channel.isEmpty()) p += 2;
        if (productCode != null && !productCode.isEmpty()) p += 1;
        return p;
    }

    public void persistDecision(UUID tenantId, UUID scenarioId, String propertyType, int units,
                                 String occupancyType, String loanPurpose, String projectReviewStatus,
                                 UUID matchedRuleId, UUID ruleSetId, String eligibilityStatus,
                                 String projectReviewRequirement) {
        jdbc.update(
            "INSERT INTO eligibility.eligibility_decision_property_type " +
            "(tenant_id, scenario_id, property_type, units, occupancy_type, loan_purpose, " +
            "project_review_status, matched_rule_id, rule_set_id, eligibility_status, " +
            "project_review_requirement, created_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())",
            tenantId, scenarioId, propertyType, units, occupancyType, loanPurpose,
            projectReviewStatus, matchedRuleId, ruleSetId, eligibilityStatus,
            projectReviewRequirement
        );
    }

    public List<PropertyTypeRuleSetConfig> findByTenantForCache(UUID tenantId, String asOfDate) {
        String effectiveDateStr = asOfDate != null && !asOfDate.isBlank() ? asOfDate : LocalDate.now().toString();

        List<UUID> ruleSetIds = jdbc.query(
            "SELECT rule_set_id FROM eligibility.property_type_rule_set " +
            "WHERE tenant_id = ? AND status = 'PUBLISHED' AND " +
            "effective_from <= ? AND (effective_to IS NULL OR effective_to >= ?) " +
            "ORDER BY version DESC",
            (rs, rowNum) -> rs.getObject("rule_set_id", UUID.class),
            tenantId, effectiveDateStr, effectiveDateStr
        );

        if (ruleSetIds.isEmpty()) {
            return List.of();
        }

        List<PropertyTypeRuleSetConfig> configs = new java.util.ArrayList<>();
        for (UUID ruleSetId : ruleSetIds) {
            String productFamily = jdbc.queryForObject(
                "SELECT product_family FROM eligibility.property_type_rule_set WHERE rule_set_id = ?",
                String.class, ruleSetId
            );
            String ch = jdbc.queryForObject(
                "SELECT channel FROM eligibility.property_type_rule_set WHERE rule_set_id = ?",
                String.class, ruleSetId
            );
            String inv = jdbc.queryForObject(
                "SELECT COALESCE(investor_code, '') FROM eligibility.property_type_rule_set WHERE rule_set_id = ?",
                String.class, ruleSetId
            );
            String prodCode = jdbc.queryForObject(
                "SELECT COALESCE(product_code, '') FROM eligibility.property_type_rule_set WHERE rule_set_id = ?",
                String.class, ruleSetId
            );
            int version = jdbc.queryForObject(
                "SELECT version FROM eligibility.property_type_rule_set WHERE rule_set_id = ?",
                Integer.class, ruleSetId
            );

            List<PropertyTypeRuleRow> rows = jdbc.query(
                "SELECT rule_id, rule_set_id, property_type, units_min, units_max, " +
                "occupancy_type, loan_purpose, project_review_requirement, " +
                "decision, severity, reason_code, message_template, priority " +
                "FROM eligibility.property_type_rule " +
                "WHERE tenant_id = ? AND rule_set_id = ? " +
                "ORDER BY priority, property_type",
                (rs, rowNum) -> new PropertyTypeRuleRow(
                    rs.getObject("rule_id", UUID.class),
                    rs.getObject("rule_set_id", UUID.class),
                    rs.getString("property_type"),
                    rs.getInt("units_min"),
                    rs.getInt("units_max"),
                    rs.getString("occupancy_type"),
                    rs.getString("loan_purpose"),
                    rs.getString("project_review_requirement"),
                    rs.getString("decision"),
                    rs.getString("severity"),
                    rs.getString("reason_code"),
                    rs.getString("message_template"),
                    rs.getInt("priority")
                ),
                tenantId, ruleSetId
            );

            int precedence = computePrecedence(inv, ch, prodCode);

            configs.add(new PropertyTypeRuleSetConfig(
                ruleSetId, tenantId.toString(), productFamily, prodCode, inv, ch, version, precedence, rows
            ));
        }

        return configs;
    }
}
