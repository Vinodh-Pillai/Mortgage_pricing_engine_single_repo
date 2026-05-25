package com.wcpe.catalog.domain;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
class DomainRepository {

    private final JdbcTemplate jdbc;

    static final RowMapper<InvestorResponse> INVESTOR_MAPPER = new RowMapper<>() {
        @Override
        public InvestorResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new InvestorResponse(
                rs.getObject("investor_id", UUID.class),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("status"),
                rs.getTimestamp("created_at").toInstant()
            );
        }
    };

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
            ? "select investor_id, code, name, status, created_at from catalog.investor where tenant_id = ? and status = ? order by code"
            : "select investor_id, code, name, status, created_at from catalog.investor where tenant_id = ? order by code";
        return statusFilter != null && !statusFilter.isBlank()
            ? jdbc.query(sql, INVESTOR_MAPPER, tenantId, statusFilter)
            : jdbc.query(sql, INVESTOR_MAPPER, tenantId);
    }

    Optional<InvestorResponse> getInvestorByCode(UUID tenantId, String code) {
        List<InvestorResponse> results = jdbc.query(
            "select investor_id, code, name, status, created_at from catalog.investor where tenant_id = ? and code = ? limit 1",
            INVESTOR_MAPPER, tenantId, code);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    Optional<InvestorResponse> getInvestorById(UUID investorId) {
        List<InvestorResponse> results = jdbc.query(
            "select investor_id, code, name, status, created_at from catalog.investor where investor_id = ? limit 1",
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
}
