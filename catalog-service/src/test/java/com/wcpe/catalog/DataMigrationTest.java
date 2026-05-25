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
class DataMigrationTest {

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
    void investorProgramRows_copiedToNewInvestorTable() {
        jdbc.update(
                "INSERT INTO catalog.investor_program " +
                "(tenant_id, investor_id, catalog_id, investor_code, investor_name, channels, product_codes, status, effective_from) " +
                "VALUES (?, ?, ?, 'MIG_INV_1', 'Migration Investor 1', '[]'::jsonb, '[]'::jsonb, 'ACTIVE', '2025-01-01')",
                tenantId, UUID.randomUUID(), UUID.randomUUID());
        jdbc.update(
                "INSERT INTO catalog.investor_program " +
                "(tenant_id, investor_id, catalog_id, investor_code, investor_name, channels, product_codes, status, effective_from) " +
                "VALUES (?, ?, ?, 'MIG_INV_2', 'Migration Investor 2', '[]'::jsonb, '[]'::jsonb, 'ACTIVE', '2025-01-01')",
                tenantId, UUID.randomUUID(), UUID.randomUUID());

        jdbc.update(
                "INSERT INTO catalog.investor (tenant_id, code, name, status) " +
                "SELECT tenant_id, investor_code, investor_name, status " +
                "FROM catalog.investor_program WHERE tenant_id = ? " +
                "ON CONFLICT (tenant_id, code) DO NOTHING",
                tenantId);

        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM catalog.investor WHERE tenant_id = ?",
                Integer.class, tenantId);
        assertThat(count).isEqualTo(2);

        String name = jdbc.queryForObject(
                "SELECT name FROM catalog.investor WHERE code = 'MIG_INV_1'",
                String.class);
        assertThat(name).isEqualTo("Migration Investor 1");
    }

    @Test
    void productDefinitionRows_copiedToNewProductTable() {
        jdbc.update(
                "INSERT INTO catalog.product_definition " +
                "(tenant_id, product_id, catalog_id, product_code, product_name, product_family, allowed_channels, allowed_states, effective_from) " +
                "VALUES (?, ?, ?, 'MIG_PROD_1', 'Migration Product 1', 'CONVENTIONAL', '[]'::jsonb, '[]'::jsonb, '2025-01-01')",
                tenantId, UUID.randomUUID(), UUID.randomUUID());
        jdbc.update(
                "INSERT INTO catalog.product_definition " +
                "(tenant_id, product_id, catalog_id, product_code, product_name, product_family, allowed_channels, allowed_states, effective_from) " +
                "VALUES (?, ?, ?, 'MIG_PROD_2', 'Migration Product 2', 'JUMBO', '[]'::jsonb, '[]'::jsonb, '2025-01-01')",
                tenantId, UUID.randomUUID(), UUID.randomUUID());

        jdbc.update(
                "INSERT INTO catalog.product (tenant_id, code, name, type, status) " +
                "SELECT tenant_id, product_code, product_name, product_family, 'ACTIVE' " +
                "FROM catalog.product_definition WHERE tenant_id = ? " +
                "ON CONFLICT (tenant_id, code) DO NOTHING",
                tenantId);

        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM catalog.product WHERE tenant_id = ?",
                Integer.class, tenantId);
        assertThat(count).isEqualTo(2);

        String name = jdbc.queryForObject(
                "SELECT name FROM catalog.product WHERE code = 'MIG_PROD_2'",
                String.class);
        assertThat(name).isEqualTo("Migration Product 2");
    }

    @Test
    void channelReferences_copiedToNewChannelTable() {
        jdbc.update(
                "INSERT INTO catalog.reference_entry " +
                "(tenant_id, entry_id, catalog_id, catalog_type, code, label, category, attributes, effective_from) " +
                "VALUES (?, ?, ?, 'CHANNEL', 'MIG_CH_1', 'Migration Channel 1', 'CHANNEL', '{}'::jsonb, '2025-01-01')",
                tenantId, UUID.randomUUID(), UUID.randomUUID());
        jdbc.update(
                "INSERT INTO catalog.reference_entry " +
                "(tenant_id, entry_id, catalog_id, catalog_type, code, label, category, attributes, effective_from) " +
                "VALUES (?, ?, ?, 'CHANNEL', 'MIG_CH_2', 'Migration Channel 2', 'CHANNEL', '{}'::jsonb, '2025-01-01')",
                tenantId, UUID.randomUUID(), UUID.randomUUID());

        jdbc.update(
                "INSERT INTO catalog.channel (tenant_id, code, name, type, status) " +
                "SELECT tenant_id, code, label, 'RETAIL', 'ACTIVE' " +
                "FROM catalog.reference_entry WHERE tenant_id = ? AND catalog_type = 'CHANNEL' " +
                "ON CONFLICT (tenant_id, code) DO NOTHING",
                tenantId);

        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM catalog.channel WHERE tenant_id = ?",
                Integer.class, tenantId);
        assertThat(count).isEqualTo(2);

        String name = jdbc.queryForObject(
                "SELECT name FROM catalog.channel WHERE code = 'MIG_CH_1'",
                String.class);
        assertThat(name).isEqualTo("Migration Channel 1");
    }
}
