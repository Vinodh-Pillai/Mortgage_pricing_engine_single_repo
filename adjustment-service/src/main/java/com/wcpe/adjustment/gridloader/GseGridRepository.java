package com.wcpe.adjustment.gridloader;

import com.wcpe.adjustment.CashOutLlpaEvaluator.CashOutLlpaRule;
import com.wcpe.adjustment.FicoLtvLlpaEvaluator.GridCell;
import com.wcpe.adjustment.PropertyOccupancyLlpaEvaluator.PropertyOccupancyRule;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class GseGridRepository {
    private final JdbcTemplate jdbc;

    public GseGridRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void publish(GseGridMappedRules rules, String sourceUrl, int warningCount) {
        Instant effectiveStart = firstEffectiveStart(rules);
        supersede(rules.investorCode(), effectiveStart);
        rules.ficoLtvCells().forEach(this::insertFico);
        rules.cashOutRules().forEach(rule -> insertCashOut(rule, rules.ruleBookHash()));
        rules.propertyOccupancyRules().forEach(rule -> insertProperty(rule, rules.ruleBookHash()));
        insertAudit(rules, sourceUrl, warningCount, null);
    }

    public void recordFailure(String investorCode, String version, String sourceUrl, String errorMessage) {
        jdbc.update("insert into gse_grid_load_audit (grid_load_id, investor_code, rule_book_version, source_url, status, error_message) values (?, ?, ?, ?, ?, ?)",
            UUID.randomUUID(), investorCode, version, sourceUrl, "FAILED", abbreviate(errorMessage));
    }

    public List<GseGridLoadStatus> latestStatuses() {
        return jdbc.query("""
            select investor_code, rule_book_version, coalesce(fico_ltv_cell_count, 0) + coalesce(cash_out_rule_count, 0) + coalesce(property_occupancy_rule_count, 0) as cell_count,
                   status, loaded_at, rule_book_hash, warning_count, error_message
            from gse_grid_load_audit
            where loaded_at in (select max(loaded_at) from gse_grid_load_audit group by investor_code)
            order by investor_code
            """, (rs, rowNum) -> new GseGridLoadStatus(rs.getString("investor_code"), rs.getString("rule_book_version"), rs.getInt("cell_count"),
            rs.getString("status"), rs.getTimestamp("loaded_at").toInstant(), rs.getString("rule_book_hash"), rs.getInt("warning_count"), rs.getString("error_message")));
    }

    public List<GridCell> findFicoLtvByTenantInvestorChannelProductDate(UUID tenantId, String investor, String channel, String product, Instant quoteDate) {
        return jdbc.query("""
            select * from fico_ltv_grid_cell
            where tenant_id = ? and investor_code = ? and channel_code = ? and product_code = ? and enabled = true
              and effective_start <= ? and (effective_end is null or effective_end > ?)
            order by priority
            """, (rs, rowNum) -> new GridCell(
            (UUID) rs.getObject("tenant_id"), (UUID) rs.getObject("rule_book_id"), (UUID) rs.getObject("rule_id"), rs.getString("rule_book_version"), rs.getString("rule_book_hash"),
            rs.getString("product_code"), rs.getString("investor_code"), rs.getString("channel_code"), rs.getString("fico_band_key"), rs.getInt("fico_min"), rs.getInt("fico_max"),
            com.wcpe.adjustment.FicoLtvLlpaEvaluator.LtvMetric.valueOf(rs.getString("ltv_metric")), rs.getString("ltv_band_key"), rs.getBigDecimal("ltv_min"), rs.getBigDecimal("ltv_max"),
            com.wcpe.adjustment.FicoLtvLlpaEvaluator.BoundaryPolicy.valueOf(rs.getString("boundary_policy")), rs.getBigDecimal("points_delta"), rs.getString("reason_code"),
            rs.getInt("priority"), rs.getTimestamp("effective_start").toInstant(), timestampOrNull(rs.getTimestamp("effective_end")), rs.getString("content_hash"), rs.getBoolean("enabled")),
            tenantId, investor, channel, product, Timestamp.from(quoteDate), Timestamp.from(quoteDate));
    }

    public List<CashOutLlpaRule> findCashOutByTenantInvestorChannelProductDate(UUID tenantId, String investor, String channel, String product, Instant quoteDate) {
        return jdbc.query("""
            select * from cash_out_llpa_rule
            where tenant_id = ? and investor_code = ? and channel_code = ? and product_code = ? and enabled = true
              and effective_start <= ? and (effective_end is null or effective_end > ?)
            order by priority
            """, (rs, rowNum) -> new CashOutLlpaRule(
            (UUID) rs.getObject("tenant_id"), (UUID) rs.getObject("rule_book_id"), (UUID) rs.getObject("rule_id"), rs.getString("rule_book_version"), rs.getString("product_code"),
            rs.getString("investor_code"), rs.getString("channel_code"), rs.getString("classification_code"), com.wcpe.adjustment.CashOutLlpaEvaluator.LtvMetric.valueOf(rs.getString("ltv_metric")),
            rs.getString("ltv_band_key"), rs.getBigDecimal("ltv_min"), rs.getBigDecimal("ltv_max"), com.wcpe.adjustment.CashOutLlpaEvaluator.BoundaryPolicy.valueOf(rs.getString("boundary_policy")),
            rs.getString("loan_amount_band_key"), rs.getBigDecimal("loan_amount_min"), rs.getBigDecimal("loan_amount_max"), rs.getString("occupancy_code"), rs.getString("property_type_code"),
            rs.getString("state_code"), rs.getBigDecimal("points_delta"), rs.getString("reason_code"), rs.getInt("priority"), rs.getTimestamp("effective_start").toInstant(),
            timestampOrNull(rs.getTimestamp("effective_end")), rs.getString("content_hash"), rs.getBoolean("enabled")), tenantId, investor, channel, product, Timestamp.from(quoteDate), Timestamp.from(quoteDate));
    }

    public List<PropertyOccupancyRule> findPropertyOccupancyByTenantInvestorChannelProductDate(UUID tenantId, String investor, String channel, String product, Instant quoteDate) {
        return jdbc.query("""
            select * from property_occupancy_llpa_rule
            where tenant_id = ? and investor_code = ? and channel_code = ? and product_code = ? and enabled = true
              and effective_start <= ? and (effective_end is null or effective_end > ?)
            order by priority
            """, (rs, rowNum) -> new PropertyOccupancyRule(
            (UUID) rs.getObject("tenant_id"), (UUID) rs.getObject("rule_book_id"), (UUID) rs.getObject("rule_id"), rs.getString("rule_book_version"), rs.getString("product_code"),
            rs.getString("investor_code"), rs.getString("channel_code"), rs.getString("occupancy_code"), rs.getString("property_type_code"), rs.getInt("unit_min"), rs.getInt("unit_max"),
            rs.getString("project_type_code"), booleanOrNull(rs.getObject("manufactured_housing_flag")), rs.getString("state_code"), rs.getString("county_code"),
            stringOrNull(rs.getString("ltv_metric")) == null ? null : com.wcpe.adjustment.PropertyOccupancyLlpaEvaluator.LtvMetric.valueOf(rs.getString("ltv_metric")), rs.getString("ltv_band_key"),
            rs.getBigDecimal("ltv_min"), rs.getBigDecimal("ltv_max"), com.wcpe.adjustment.PropertyOccupancyLlpaEvaluator.BoundaryPolicy.valueOf(rs.getString("boundary_policy")),
            rs.getString("loan_amount_band_key"), rs.getBigDecimal("loan_amount_min"), rs.getBigDecimal("loan_amount_max"), booleanOrNull(rs.getObject("first_time_homebuyer_flag")), qualifiers(rs.getString("qualifiers")),
            rs.getBigDecimal("points_delta"), rs.getString("reason_code"), rs.getInt("priority"), rs.getString("exclusivity_group"), rs.getTimestamp("effective_start").toInstant(),
            timestampOrNull(rs.getTimestamp("effective_end")), rs.getString("content_hash"), rs.getBoolean("enabled")), tenantId, investor, channel, product, Timestamp.from(quoteDate), Timestamp.from(quoteDate));
    }

    private void supersede(String investorCode, Instant newEffectiveStart) {
        Timestamp supersededEnd = Timestamp.from(newEffectiveStart.minusSeconds(86_400));
        jdbc.update("update fico_ltv_grid_cell set effective_end = ?, enabled = false where investor_code = ? and enabled = true and (effective_end is null or effective_end >= ?)", supersededEnd, investorCode, Timestamp.from(newEffectiveStart));
        jdbc.update("update cash_out_llpa_rule set effective_end = ?, enabled = false where investor_code = ? and enabled = true and (effective_end is null or effective_end >= ?)", supersededEnd, investorCode, Timestamp.from(newEffectiveStart));
        jdbc.update("update property_occupancy_llpa_rule set effective_end = ?, enabled = false where investor_code = ? and enabled = true and (effective_end is null or effective_end >= ?)", supersededEnd, investorCode, Timestamp.from(newEffectiveStart));
    }

    private Instant firstEffectiveStart(GseGridMappedRules rules) {
        return rules.ficoLtvCells().stream().findFirst().map(GridCell::effectiveStart)
            .or(() -> rules.cashOutRules().stream().findFirst().map(CashOutLlpaRule::effectiveStart))
            .or(() -> rules.propertyOccupancyRules().stream().findFirst().map(PropertyOccupancyRule::effectiveStart))
            .orElse(Instant.now());
    }

    private void insertFico(GridCell cell) {
        jdbc.update("""
            insert into fico_ltv_grid_cell (grid_cell_id, tenant_id, rule_book_id, rule_id, rule_book_version, rule_book_hash, product_code, investor_code, channel_code,
              fico_band_key, fico_min, fico_max, ltv_metric, ltv_band_key, ltv_min, ltv_max, boundary_policy, points_delta, reason_code, priority, effective_start, effective_end, content_hash, enabled)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, UUID.randomUUID(), cell.tenantId(), cell.ruleBookId(), cell.ruleId(), cell.ruleBookVersion(), cell.ruleBookHash(), cell.productId(), cell.investorId(), cell.channel(),
            cell.ficoBandKey(), cell.ficoMin(), cell.ficoMax(), cell.ltvMetric().name(), cell.ltvBandKey(), cell.ltvMin(), cell.ltvMax(), cell.boundaryPolicy().name(), cell.pointsDelta(),
            cell.reasonCode(), cell.priority(), Timestamp.from(cell.effectiveStart()), cell.effectiveEnd() == null ? null : Timestamp.from(cell.effectiveEnd()), cell.contentHash(), cell.enabled());
    }

    private void insertCashOut(CashOutLlpaRule rule, String ruleBookHash) {
        jdbc.update("""
            insert into cash_out_llpa_rule (cash_out_rule_id, tenant_id, rule_book_id, rule_id, rule_book_version, rule_book_hash, product_code, investor_code, channel_code,
              classification_code, ltv_metric, ltv_band_key, ltv_min, ltv_max, boundary_policy, loan_amount_band_key, loan_amount_min, loan_amount_max, occupancy_code, property_type_code,
              state_code, points_delta, reason_code, priority, effective_start, effective_end, content_hash, enabled)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, UUID.randomUUID(), rule.tenantId(), rule.ruleBookId(), rule.ruleId(), rule.ruleBookVersion(), ruleBookHash, rule.productId(), rule.investorId(), rule.channel(),
            rule.classificationCode(), rule.ltvMetric().name(), rule.ltvBandKey(), rule.ltvMin(), rule.ltvMax(), rule.boundaryPolicy().name(), rule.loanAmountBandKey(), rule.loanAmountMin(),
            rule.loanAmountMax(), rule.occupancyCode(), rule.propertyTypeCode(), rule.stateCode(), rule.pointsDelta(), rule.reasonCode(), rule.priority(), Timestamp.from(rule.effectiveStart()),
            rule.effectiveEnd() == null ? null : Timestamp.from(rule.effectiveEnd()), rule.contentHash(), rule.enabled());
    }

    private void insertProperty(PropertyOccupancyRule rule, String ruleBookHash) {
        jdbc.update("""
            insert into property_occupancy_llpa_rule (property_occupancy_rule_id, tenant_id, rule_book_id, rule_id, rule_book_version, rule_book_hash, product_code, investor_code, channel_code,
              occupancy_code, property_type_code, unit_min, unit_max, project_type_code, manufactured_housing_flag, state_code, county_code, ltv_metric, ltv_band_key, ltv_min, ltv_max,
              boundary_policy, loan_amount_band_key, loan_amount_min, loan_amount_max, first_time_homebuyer_flag, qualifiers, points_delta, reason_code, priority, exclusivity_group,
              effective_start, effective_end, content_hash, enabled)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, UUID.randomUUID(), rule.tenantId(), rule.ruleBookId(), rule.ruleId(), rule.ruleBookVersion(), ruleBookHash, rule.productId(), rule.investorId(), rule.channel(),
            rule.occupancyCode(), rule.propertyTypeCode(), rule.unitMin(), rule.unitMax(), rule.projectTypeCode(), rule.manufacturedHousingFlag(), rule.stateCode(), rule.countyCode(),
            rule.ltvMetric() == null ? null : rule.ltvMetric().name(), rule.ltvBandKey(), rule.ltvMin(), rule.ltvMax(), rule.boundaryPolicy().name(), rule.loanAmountBandKey(),
            rule.loanAmountMin(), rule.loanAmountMax(), rule.firstTimeHomebuyerFlag(), String.join(",", rule.requiredQualifierCodes()), rule.pointsDelta(), rule.reasonCode(), rule.priority(),
            rule.exclusivityGroup(), Timestamp.from(rule.effectiveStart()), rule.effectiveEnd() == null ? null : Timestamp.from(rule.effectiveEnd()), rule.contentHash(), rule.enabled());
    }

    private void insertAudit(GseGridMappedRules rules, String sourceUrl, int warningCount, String error) {
        jdbc.update("""
            insert into gse_grid_load_audit (grid_load_id, investor_code, rule_book_version, rule_book_hash, source_url, status, fico_ltv_cell_count, cash_out_rule_count,
              property_occupancy_rule_count, warning_count, error_message)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, UUID.randomUUID(), rules.investorCode(), rules.ruleBookVersion(), rules.ruleBookHash(), sourceUrl, "LOADED", rules.ficoLtvCells().size(), rules.cashOutRules().size(),
            rules.propertyOccupancyRules().size(), warningCount, abbreviate(error));
    }

    private static String abbreviate(String value) {
        return value == null || value.length() <= 512 ? value : value.substring(0, 512);
    }

    private static Instant timestampOrNull(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static Boolean booleanOrNull(Object value) {
        return value == null ? null : (Boolean) value;
    }

    private static String stringOrNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static List<String> qualifiers(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split(",")).map(String::trim).filter(part -> !part.isBlank()).toList();
    }
}
