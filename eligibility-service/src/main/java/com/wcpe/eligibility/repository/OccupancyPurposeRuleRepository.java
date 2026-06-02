package com.wcpe.eligibility.repository;

import com.wcpe.eligibility.domain.models.OccupancyPurposeRuleRow;
import com.wcpe.eligibility.domain.models.OccupancyPurposeRuleSetConfig;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public class OccupancyPurposeRuleRepository {

    private final JdbcTemplate jdbc;

    public OccupancyPurposeRuleRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<OccupancyPurposeRuleSetConfig> resolve(UUID tenantId, String productCode, String investorCode,
                                                        String channel, Date effectiveDate) {
        String effectiveDateStr = effectiveDate != null ? effectiveDate.toString() : LocalDate.now().toString();

        List<UUID> ruleSetIds = jdbc.query(
            "SELECT rule_set_id FROM eligibility.occupancy_purpose_rule_set " +
            "WHERE tenant_id = ? AND status = 'PUBLISHED' AND " +
            "(product_code IS NULL OR product_code = ?) AND " +
            "(investor_id IS NOT NULL AND investor_id::text = ? OR investor_id IS NULL) AND " +
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
            String productFamily = jdbc.queryForObject(
                "SELECT product_family FROM eligibility.occupancy_purpose_rule_set WHERE rule_set_id = ?",
                String.class, ruleSetId
            );
            String ch = jdbc.queryForObject(
                "SELECT channel FROM eligibility.occupancy_purpose_rule_set WHERE rule_set_id = ?",
                String.class, ruleSetId
            );
            String inv = jdbc.queryForObject(
                "SELECT COALESCE(investor_id::text, '') FROM eligibility.occupancy_purpose_rule_set WHERE rule_set_id = ?",
                String.class, ruleSetId
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

            int precedence = jdbc.queryForObject(
                "SELECT version FROM eligibility.occupancy_purpose_rule_set WHERE rule_set_id = ?",
                Integer.class, ruleSetId
            );

            configs.add(new OccupancyPurposeRuleSetConfig(
                ruleSetId, productFamily, inv, ch, precedence, rows
            ));
        }

        return configs;
    }
}
