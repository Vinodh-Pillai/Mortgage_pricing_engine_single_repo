package com.wcpe.eligibility.repository;

import com.wcpe.eligibility.domain.models.FicoLtvMatrixConfig;
import com.wcpe.eligibility.domain.models.FicoLtvMatrixRow;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public class FicoLtvMatrixRepository {

    private final JdbcTemplate jdbc;

    public FicoLtvMatrixRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public FicoLtvMatrixConfig resolve(UUID tenantId, String productFamily, String investorCode,
                                        String channel, String loanPurpose, String occupancyType,
                                        String propertyType, Date effectiveDate) {
        Date effectiveDateParam = effectiveDate != null ? effectiveDate : Date.valueOf(LocalDate.now());

        UUID matrixSetId = jdbc.queryForObject(
            "SELECT matrix_set_id FROM eligibility.fico_ltv_matrix_set " +
            "WHERE tenant_id = ? AND product_family = ? AND " +
            "(investor_code IS NULL OR investor_code = ?) AND " +
            "(channel IS NULL OR channel = ?) AND " +
            "status = 'PUBLISHED' AND " +
            "effective_from <= ? AND (effective_to IS NULL OR effective_to >= ?) " +
            "ORDER BY effective_from DESC LIMIT 1",
            UUID.class,
            tenantId, productFamily, investorCode, channel, effectiveDateParam, effectiveDateParam
        );

        if (matrixSetId == null) {
            return null;
        }

        List<FicoLtvMatrixRow> rows = jdbc.query(
            "SELECT matrix_row_id, matrix_set_id, fico_min, fico_max, max_ltv, max_cltv, " +
            "loan_purpose, occupancy_type, property_type, units_min, units_max, " +
            "documentation_type, aus_type, severity_if_missing_fico, reason_code, row_hash " +
            "FROM eligibility.fico_ltv_matrix_row " +
            "WHERE tenant_id = ? AND matrix_set_id = ? AND " +
            "(loan_purpose IS NULL OR loan_purpose = ?) AND " +
            "(occupancy_type IS NULL OR occupancy_type = ?) AND " +
            "(property_type IS NULL OR property_type = ?) " +
            "ORDER BY fico_min, fico_max",
            (rs, rowNum) -> new FicoLtvMatrixRow(
                rs.getObject("matrix_row_id", UUID.class),
                rs.getObject("matrix_set_id", UUID.class),
                rs.getInt("fico_min"),
                rs.getInt("fico_max"),
                rs.getBigDecimal("max_ltv"),
                rs.getBigDecimal("max_cltv"),
                rs.getString("loan_purpose"),
                rs.getString("occupancy_type"),
                rs.getString("property_type"),
                rs.getInt("units_min"),
                rs.getInt("units_max"),
                rs.getString("documentation_type"),
                rs.getString("aus_type"),
                rs.getString("severity_if_missing_fico"),
                rs.getString("reason_code"),
                rs.getString("row_hash")
            ),
            tenantId, matrixSetId, loanPurpose, occupancyType, propertyType
        );

        String status = jdbc.queryForObject(
            "SELECT status FROM eligibility.fico_ltv_matrix_set WHERE matrix_set_id = ?",
            String.class, matrixSetId
        );

        int version = jdbc.queryForObject(
            "SELECT version FROM eligibility.fico_ltv_matrix_set WHERE matrix_set_id = ?",
            Integer.class, matrixSetId
        );

        return new FicoLtvMatrixConfig(matrixSetId.toString(), productFamily, investorCode, channel, status, version, rows);
    }
}
