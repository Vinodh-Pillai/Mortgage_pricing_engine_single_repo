package com.wcpe.eligibility.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.eligibility.nonqm.NonQmEligibilityModels.EligibilityRule;
import com.wcpe.eligibility.nonqm.NonQmEligibilityModels.NonQmEligibilityRuleSet;
import com.wcpe.eligibility.nonqm.NonQmEligibilityModels.RuleSetSource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class NonQmRuleSetRepository implements NonQmRuleSetStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final JavaType rulesType;

    public NonQmRuleSetRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.rulesType = objectMapper.getTypeFactory().constructCollectionType(List.class, EligibilityRule.class);
    }

    @Override
    public void save(NonQmEligibilityRuleSet ruleSet) {
        jdbc.update(
            "INSERT INTO eligibility.non_qm_rule_set " +
                "(rule_set_id, product_code, non_qm_type, investor_code, channel_code, version, effective_start, effective_end, rule_document, source_system, source_ref, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, 'PUBLISHED') " +
                "ON CONFLICT (rule_set_id) DO UPDATE SET " +
                "product_code = EXCLUDED.product_code, non_qm_type = EXCLUDED.non_qm_type, investor_code = EXCLUDED.investor_code, " +
                "channel_code = EXCLUDED.channel_code, version = EXCLUDED.version, effective_start = EXCLUDED.effective_start, " +
                "effective_end = EXCLUDED.effective_end, rule_document = EXCLUDED.rule_document, source_system = EXCLUDED.source_system, " +
                "source_ref = EXCLUDED.source_ref, status = EXCLUDED.status",
            ruleSet.ruleSetId(),
            ruleSet.productCode(),
            ruleSet.productType(),
            ruleSet.investorCode(),
            ruleSet.channelCode(),
            ruleSet.version(),
            timestamp(ruleSet.effectiveStart()),
            timestamp(ruleSet.effectiveEnd()),
            writeRules(ruleSet.rules()),
            ruleSet.source().name(),
            ruleSet.sourceSystemRef()
        );
    }

    @Override
    public Optional<NonQmEligibilityRuleSet> findById(String ruleSetId) {
        List<NonQmEligibilityRuleSet> matches = jdbc.query(
            "SELECT rule_set_id, product_code, non_qm_type, investor_code, channel_code, version, effective_start, effective_end, rule_document, source_system, source_ref " +
                "FROM eligibility.non_qm_rule_set WHERE rule_set_id = ?",
            mapper(),
            ruleSetId
        );
        return matches.stream().findFirst();
    }

    @Override
    public Optional<NonQmEligibilityRuleSet> resolve(String productCode, String investorCode, String channelCode, Instant asOf) {
        List<NonQmEligibilityRuleSet> matches = jdbc.query(
            "SELECT rule_set_id, product_code, non_qm_type, investor_code, channel_code, version, effective_start, effective_end, rule_document, source_system, source_ref " +
                "FROM eligibility.non_qm_rule_set " +
                "WHERE status = 'PUBLISHED' " +
                "AND (product_code = ? OR product_code = '') " +
                "AND (investor_code = ? OR investor_code = '') " +
                "AND (channel_code = ? OR channel_code = '') " +
                "AND (effective_start IS NULL OR effective_start <= ?) " +
                "AND (effective_end IS NULL OR effective_end > ?) " +
                "ORDER BY version DESC LIMIT 1",
            mapper(),
            productCode,
            investorCode,
            channelCode,
            timestamp(asOf),
            timestamp(asOf)
        );
        return matches.stream().findFirst();
    }

    private RowMapper<NonQmEligibilityRuleSet> mapper() {
        return (rs, rowNum) -> new NonQmEligibilityRuleSet(
            rs.getString("rule_set_id"),
            rs.getString("product_code"),
            rs.getString("non_qm_type"),
            rs.getString("investor_code"),
            rs.getString("channel_code"),
            rs.getInt("version"),
            instant(rs, "effective_start"),
            instant(rs, "effective_end"),
            readRules(rs.getString("rule_document")),
            RuleSetSource.valueOf(rs.getString("source_system")),
            rs.getString("source_ref")
        );
    }

    private String writeRules(List<EligibilityRule> rules) {
        try {
            return objectMapper.writeValueAsString(rules == null ? List.of() : rules);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Non-QM rule document could not be serialized", ex);
        }
    }

    private List<EligibilityRule> readRules(String ruleDocument) throws SQLException {
        try {
            return ruleDocument == null || ruleDocument.isBlank() ? List.of() : objectMapper.readValue(ruleDocument, rulesType);
        } catch (JsonProcessingException ex) {
            throw new SQLException("Non-QM rule document could not be deserialized", ex);
        }
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }
}
