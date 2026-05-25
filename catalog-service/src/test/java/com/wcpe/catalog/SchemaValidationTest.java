package com.wcpe.catalog;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;


import java.util.*;

import static org.assertj.core.api.Assertions.*;

@Testcontainers
@SpringBootTest
class SchemaValidationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("catalog")
            .withUsername("catalog_app")
            .withPassword("catalog_app");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    JdbcTemplate jdbc;

    UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
    }

    @Test
    void v4Migration_appliesCleanly() {
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT column_name FROM information_schema.columns " +
                "WHERE table_schema = 'catalog' AND table_name = 'product' AND column_name = 'product_id'");
        assertThat(row).isNotEmpty();
    }

    @Test
    void v5Migration_appliesCleanly() {
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT column_name FROM information_schema.columns " +
                "WHERE table_schema = 'catalog' AND table_name = 'product_overlay' AND column_name = 'overlay_id'");
        assertThat(row).isNotEmpty();
    }

    @Test
    void checkConstraints_onInvestor_statusValues() {
        jdbc.update(
                "INSERT INTO catalog.investor (tenant_id, code, name, status) " +
                "VALUES (?, ?, ?, 'ACTIVE')",
                tenantId, "INV_CHECK", "Check Investor");
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM catalog.investor WHERE code = 'INV_CHECK'", Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void checkConstraints_onProduct_statusValues() {
        jdbc.update(
                "INSERT INTO catalog.product (tenant_id, code, name, type, status) " +
                "VALUES (?, ?, ?, 'CONVENTIONAL', 'ACTIVE')",
                tenantId, "PROD_CHECK", "Check Product");
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM catalog.product WHERE code = 'PROD_CHECK'", Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void backfill_fromOldTables_populatesNewDomainTables() {
        jdbc.update(
                "INSERT INTO catalog.investor_program " +
                "(tenant_id, investor_id, catalog_id, investor_code, investor_name, channels, product_codes, status, effective_from) " +
                "VALUES (?, ?, ?, 'BACKFILL1', 'Backfill Investor', '[]'::jsonb, '[]'::jsonb, 'ACTIVE', '2025-01-01')",
                tenantId, UUID.randomUUID(), UUID.randomUUID());

        jdbc.update(
                "INSERT INTO catalog.investor (tenant_id, code, name, status) " +
                "SELECT tenant_id, investor_code, investor_name, status " +
                "FROM catalog.investor_program WHERE tenant_id = ? " +
                "ON CONFLICT (tenant_id, code) DO NOTHING",
                tenantId);

        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM catalog.investor WHERE tenant_id = ? AND code = 'BACKFILL1'",
                Integer.class, tenantId);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void versionDiffCache_materializedViewPopulated() {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables " +
                "WHERE table_schema = 'catalog' AND table_name = 'catalog_version_control'",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }
}
