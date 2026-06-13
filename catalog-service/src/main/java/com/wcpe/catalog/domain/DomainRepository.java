package com.wcpe.catalog.domain;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Date;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
class DomainRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    static final RowMapper<InvestorResponse> INVESTOR_MAPPER = new RowMapper<>() {
        @Override
        public InvestorResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new InvestorResponse(
                rs.getObject("investor_id", UUID.class),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("status"),
                rs.getTimestamp("created_at").toInstant(),
                stringOrDefault(rs, "investor_code", rs.getString("code")),
                stringOrNull(rs, "type"),
                stringOrNull(rs, "delivery_type"),
                integerOrNull(rs, "settlement_days"),
                stringOrNull(rs, "api_endpoint")
            );
        }
    };

    private final RowMapper<InvestorEligibilityMatrixRow> ELIGIBILITY_MAPPER = (rs, rowNum) -> new InvestorEligibilityMatrixRow(
        rs.getObject("id", UUID.class),
        rs.getObject("investor_id", UUID.class),
        rs.getString("loan_purpose"),
        rs.getString("property_type"),
        rs.getString("occupancy_type"),
        integerOrNull(rs, "min_fico"),
        integerOrNull(rs, "max_fico"),
        rs.getBigDecimal("max_ltv"),
        rs.getBigDecimal("max_cltv"),
        rs.getBigDecimal("max_dti"),
        rs.getBigDecimal("min_loan_amount"),
        rs.getBigDecimal("max_loan_amount"),
        parseStringList(rs.getString("allowed_states")),
        parseStringList(rs.getString("excluded_counties")),
        parseObjectMap(rs.getString("overlays")),
        rs.getDate("effective_date").toLocalDate(),
        rs.getDate("expiration_date") == null ? null : rs.getDate("expiration_date").toLocalDate(),
        rs.getBoolean("is_active")
    );

    static final RowMapper<ProductResponse> PRODUCT_MAPPER = new RowMapper<>() {
        @Override
        public ProductResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new ProductResponse(
                rs.getObject("product_id", UUID.class),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("type"),
                rs.getString("status"),
                rs.getTimestamp("created_at").toInstant()
            );
        }
    };

    static final RowMapper<ChannelResponse> CHANNEL_MAPPER = new RowMapper<>() {
        @Override
        public ChannelResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new ChannelResponse(
                rs.getObject("channel_id", UUID.class),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("type"),
                rs.getString("status"),
                rs.getTimestamp("created_at").toInstant()
            );
        }
    };

    DomainRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    List<InvestorResponse> listInvestors(UUID tenantId, String statusFilter) {
        String sql = statusFilter != null && !statusFilter.isBlank()
            ? "select investor_id, code, name, status, created_at, investor_code, type, delivery_type, settlement_days, api_endpoint from catalog.investor where tenant_id = ? and status = ? order by code"
            : "select investor_id, code, name, status, created_at, investor_code, type, delivery_type, settlement_days, api_endpoint from catalog.investor where tenant_id = ? order by code";
        return statusFilter != null && !statusFilter.isBlank()
            ? jdbc.query(sql, INVESTOR_MAPPER, tenantId, statusFilter)
            : jdbc.query(sql, INVESTOR_MAPPER, tenantId);
    }

    Optional<InvestorResponse> getInvestorByCode(UUID tenantId, String code) {
        List<InvestorResponse> results = jdbc.query(
            "select investor_id, code, name, status, created_at, investor_code, type, delivery_type, settlement_days, api_endpoint from catalog.investor where tenant_id = ? and code = ? limit 1",
            INVESTOR_MAPPER, tenantId, code);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    Optional<InvestorResponse> getInvestorById(UUID investorId) {
        List<InvestorResponse> results = jdbc.query(
            "select investor_id, code, name, status, created_at, investor_code, type, delivery_type, settlement_days, api_endpoint from catalog.investor where investor_id = ? limit 1",
            INVESTOR_MAPPER, investorId);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    boolean createInvestor(UUID tenantId, String code, String name, String status) {
        String s = status != null ? status : "ACTIVE";
        int updated = jdbc.update(
            "insert into catalog.investor (tenant_id, code, name, status) values (?, ?, ?, ?) " +
            "on conflict (tenant_id, code) do nothing",
            tenantId, code, name, s);
        return updated > 0;
    }

    InvestorResponse upsertInvestor(UUID tenantId, InvestorUpsertRequest request) {
        String code = firstNonBlank(request.code(), request.investorCode());
        if (code == null) throw new CatalogException("INVESTOR_CODE_REQUIRED");
        String name = firstNonBlank(request.name(), code);
        UUID investorId = UUID.randomUUID();
        jdbc.update("""
            insert into catalog.investor (tenant_id, investor_id, code, name, investor_code, type, delivery_type, settlement_days, api_endpoint, status)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            on conflict (tenant_id, code) do update set
              name = excluded.name,
              investor_code = excluded.investor_code,
              type = excluded.type,
              delivery_type = excluded.delivery_type,
              settlement_days = excluded.settlement_days,
              api_endpoint = excluded.api_endpoint,
              status = excluded.status,
              updated_at = now()
            """,
            tenantId, investorId, code, name, firstNonBlank(request.investorCode(), code), defaultString(request.type(), "PORTFOLIO"),
            defaultString(request.deliveryType(), "FLOW"), request.settlementDays() == null ? 30 : request.settlementDays(),
            request.apiEndpoint(), defaultString(request.status(), "ACTIVE"));
        return getInvestorByCode(tenantId, code).orElseThrow(() -> new CatalogException("INVESTOR_NOT_FOUND"));
    }

    List<InvestorEligibilityMatrixRow> investorEligibility(UUID tenantId, UUID investorId) {
        return jdbc.query("""
            select id, investor_id, loan_purpose, property_type, occupancy_type, min_fico, max_fico, max_ltv, max_cltv, max_dti,
                   min_loan_amount, max_loan_amount, allowed_states::text, excluded_counties::text, overlays::text,
                   effective_date, expiration_date, is_active
            from catalog.investor_eligibility
            where tenant_id = ? and investor_id = ?
            order by loan_purpose, property_type, occupancy_type, effective_date desc
            """, ELIGIBILITY_MAPPER, tenantId, investorId);
    }

    InvestorEligibilityMatrixResponse upsertInvestorEligibility(UUID tenantId, UUID investorId, InvestorEligibilityMatrixRequest request) {
        if (request == null || request.rows() == null) throw new CatalogException("ELIGIBILITY_MATRIX_REQUIRED");
        for (InvestorEligibilityMatrixRow row : request.rows()) {
            UUID id = row.id() == null ? UUID.randomUUID() : row.id();
            jdbc.update("""
                insert into catalog.investor_eligibility
                  (tenant_id, id, investor_id, loan_purpose, property_type, occupancy_type, min_fico, max_fico, max_ltv, max_cltv, max_dti,
                   min_loan_amount, max_loan_amount, allowed_states, excluded_counties, overlays, effective_date, expiration_date, is_active)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?, ?)
                on conflict (tenant_id, id) do update set
                  loan_purpose = excluded.loan_purpose, property_type = excluded.property_type, occupancy_type = excluded.occupancy_type,
                  min_fico = excluded.min_fico, max_fico = excluded.max_fico, max_ltv = excluded.max_ltv, max_cltv = excluded.max_cltv,
                  max_dti = excluded.max_dti, min_loan_amount = excluded.min_loan_amount, max_loan_amount = excluded.max_loan_amount,
                  allowed_states = excluded.allowed_states, excluded_counties = excluded.excluded_counties, overlays = excluded.overlays,
                  effective_date = excluded.effective_date, expiration_date = excluded.expiration_date, is_active = excluded.is_active
                """,
                tenantId, id, investorId, required(row.loanPurpose(), "LOAN_PURPOSE_REQUIRED"), required(row.propertyType(), "PROPERTY_TYPE_REQUIRED"),
                required(row.occupancyType(), "OCCUPANCY_TYPE_REQUIRED"), row.minFico(), row.maxFico(), row.maxLtv(), row.maxCltv(), row.maxDti(),
                row.minLoanAmount(), row.maxLoanAmount(), json(row.allowedStates()), json(row.excludedCounties()), json(row.overlays()),
                Date.valueOf(row.effectiveDate() == null ? LocalDate.now() : row.effectiveDate()), row.expirationDate() == null ? null : Date.valueOf(row.expirationDate()), row.active());
        }
        return new InvestorEligibilityMatrixResponse(investorId, investorEligibility(tenantId, investorId));
    }

    List<ProductResponse> listProducts(UUID tenantId, String statusFilter) {
        String sql = statusFilter != null && !statusFilter.isBlank()
            ? "select product_id, code, name, type, status, created_at from catalog.product where tenant_id = ? and status = ? order by code"
            : "select product_id, code, name, type, status, created_at from catalog.product where tenant_id = ? order by code";
        return statusFilter != null && !statusFilter.isBlank()
            ? jdbc.query(sql, PRODUCT_MAPPER, tenantId, statusFilter)
            : jdbc.query(sql, PRODUCT_MAPPER, tenantId);
    }

    Optional<ProductResponse> getProductByCode(UUID tenantId, String code) {
        List<ProductResponse> results = jdbc.query(
            "select product_id, code, name, type, status, created_at from catalog.product where tenant_id = ? and code = ? limit 1",
            PRODUCT_MAPPER, tenantId, code);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    Optional<ProductResponse> getProductById(UUID productId) {
        List<ProductResponse> results = jdbc.query(
            "select product_id, code, name, type, status, created_at from catalog.product where product_id = ? limit 1",
            PRODUCT_MAPPER, productId);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    List<ChannelResponse> listChannels(UUID tenantId, String statusFilter) {
        String sql = statusFilter != null && !statusFilter.isBlank()
            ? "select channel_id, code, name, type, status, created_at from catalog.channel where tenant_id = ? and status = ? order by code"
            : "select channel_id, code, name, type, status, created_at from catalog.channel where tenant_id = ? order by code";
        return statusFilter != null && !statusFilter.isBlank()
            ? jdbc.query(sql, CHANNEL_MAPPER, tenantId, statusFilter)
            : jdbc.query(sql, CHANNEL_MAPPER, tenantId);
    }

    Optional<ChannelResponse> getChannelByCode(UUID tenantId, String code) {
        List<ChannelResponse> results = jdbc.query(
            "select channel_id, code, name, type, status, created_at from catalog.channel where tenant_id = ? and code = ? limit 1",
            CHANNEL_MAPPER, tenantId, code);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    Optional<ChannelResponse> getChannelById(UUID channelId) {
        List<ChannelResponse> results = jdbc.query(
            "select channel_id, code, name, type, status, created_at from catalog.channel where channel_id = ? limit 1",
            CHANNEL_MAPPER, channelId);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    boolean createProduct(UUID tenantId, String code, String name, String type, String status) {
        String t = type != null ? type : "CONVENTIONAL";
        String s = status != null ? status : "ACTIVE";
        int updated = jdbc.update(
            "insert into catalog.product (tenant_id, code, name, type, status) values (?, ?, ?, ?, ?) " +
            "on conflict (tenant_id, code) do nothing",
            tenantId, code, name, t, s);
        return updated > 0;
    }

    boolean createChannel(UUID tenantId, String code, String name, String type, String status) {
        String t = type != null ? type : "RETAIL";
        String s = status != null ? status : "ACTIVE";
        int updated = jdbc.update(
            "insert into catalog.channel (tenant_id, code, name, type, status) values (?, ?, ?, ?, ?) " +
            "on conflict (tenant_id, code) do nothing",
            tenantId, code, name, t, s);
        return updated > 0;
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) return first;
        return second != null && !second.isBlank() ? second : null;
    }

    private static String defaultString(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private static String required(String value, String code) { if (value == null || value.isBlank()) throw new CatalogException(code); return value; }
    private String json(Object value) { try { return objectMapper.writeValueAsString(value == null ? List.of() : value); } catch (Exception ex) { throw new CatalogException("INVALID_JSON"); } }
    private List<String> parseStringList(String json) { try { return json == null ? List.of() : objectMapper.readValue(json, new TypeReference<List<String>>() {}); } catch (Exception ex) { return List.of(); } }
    private Map<String, Object> parseObjectMap(String json) { try { return json == null ? Map.of() : objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {}); } catch (Exception ex) { return Map.of(); } }
    private static String stringOrNull(ResultSet rs, String column) throws SQLException { return rs.getString(column); }
    private static String stringOrDefault(ResultSet rs, String column, String fallback) throws SQLException { String value = rs.getString(column); return value == null ? fallback : value; }
    private static Integer integerOrNull(ResultSet rs, String column) throws SQLException { int value = rs.getInt(column); return rs.wasNull() ? null : value; }
}
