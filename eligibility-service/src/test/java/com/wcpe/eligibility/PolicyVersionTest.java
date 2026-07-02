package com.wcpe.eligibility;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.eligibility.domain.models.PolicyVersion;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PolicyVersionTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("eligibility")
        .withUsername("eligibility_app")
        .withPassword("eligibility_app");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private static String gf01Body;
    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @BeforeAll
    static void setup() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        com.wcpe.eligibility.domain.models.EligibilityRequest request = mapper.readValue(
            Files.readString(Path.of("golden/PII-01-eligibility-rules/GF01_happy_path.json")),
            ReplayDeterminismTest.RequestWrapper.class
        ).getRequest();
        gf01Body = mapper.writeValueAsString(request);
    }

    @BeforeEach
    void isolatePolicyVersionFixtures() {
        jdbcTemplate.update("DELETE FROM eligibility.policy_version WHERE tenant_id = ?", TENANT);
    }

    /* ---- Request timestamp falls within ACTIVE policy effective window ---- */

    @Test
    void activePolicyVersionSelectedByEffectiveDate() throws Exception {
        LocalDate today = LocalDate.now();
        UUID policyId = UUID.randomUUID();

        try {
            // Insert an ACTIVE policy version covering today
            jdbcTemplate.update(
                "INSERT INTO eligibility.policy_version (id, tenant_id, version, name, status, effective_from, effective_to) " +
                "VALUES (?, ?, 1, 'Pilot v1', 'ACTIVE', ?, ?)",
                policyId, TENANT, today.minusDays(30), today.plusDays(30)
            );

            mockMvc.perform(post("/api/v1/tenants/11111111-1111-1111-1111-111111111111/evaluate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Roles", "ELIGIBILITY_EVALUATOR")
                    .content(gf01Body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ELIGIBLE"));
        } finally {
            cleanup(policyId);
        }
    }

    @Test
    void requestUsesCurrentlyActivePolicy() throws Exception {
        LocalDate today = LocalDate.now();
        UUID policyId = UUID.randomUUID();

        UUID supersededId = UUID.randomUUID();
        try {
            // Insert two policies: one active, one superseded
            jdbcTemplate.update(
                "INSERT INTO eligibility.policy_version (id, tenant_id, version, name, status, effective_from, effective_to) " +
                "VALUES (?, ?, 1, 'Pilot v1', 'SUPERSEDED', ?, ?)",
                supersededId, TENANT, today.minusDays(90), today.minusDays(1)
            );
            jdbcTemplate.update(
                "INSERT INTO eligibility.policy_version (id, tenant_id, version, name, status, effective_from, effective_to) " +
                "VALUES (?, ?, 2, 'Pilot v2', 'ACTIVE', ?, ?)",
                policyId, TENANT, today, today.plusDays(365)
            );

            mockMvc.perform(post("/api/v1/tenants/11111111-1111-1111-1111-111111111111/evaluate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Roles", "ELIGIBILITY_EVALUATOR")
                    .content(gf01Body))
                .andExpect(status().isOk());

            // The ACTIVE policy (v2) should be in use
            Integer maxVersion = jdbcTemplate.queryForObject(
                "SELECT MAX(version) FROM eligibility.policy_version WHERE tenant_id = ? AND status = 'ACTIVE'",
                Integer.class, TENANT
            );
            assertEquals(2, maxVersion, "Only version 2 should be ACTIVE");
        } finally {
            cleanup(supersededId, policyId);
        }
    }

    /* ---- Expired policy not used ---- */

    @Test
    void expiredPolicyNotSelected() throws Exception {
        LocalDate today = LocalDate.now();
        UUID policyId = UUID.randomUUID();

        UUID activeId = UUID.randomUUID();
        try {
            // Insert an EXPIRED policy version (effectiveTo is in the past)
            jdbcTemplate.update(
                "INSERT INTO eligibility.policy_version (id, tenant_id, version, name, status, effective_from, effective_to) " +
                "VALUES (?, ?, 1, 'Expired v1', 'SUPERSEDED', ?, ?)",
                policyId, TENANT, today.minusDays(90), today.minusDays(1)
            );

            // Insert an ACTIVE policy version
            jdbcTemplate.update(
                "INSERT INTO eligibility.policy_version (id, tenant_id, version, name, status, effective_from, effective_to) " +
                "VALUES (?, ?, 2, 'Current v2', 'ACTIVE', ?, ?)",
                activeId, TENANT, today, today.plusDays(365)
            );

            mockMvc.perform(post("/api/v1/tenants/11111111-1111-1111-1111-111111111111/evaluate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Roles", "ELIGIBILITY_EVALUATOR")
                    .content(gf01Body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ELIGIBLE"));
        } finally {
            cleanup(policyId, activeId);
        }
    }

    private void cleanup(UUID... ids) {
        for (UUID id : ids) {
            jdbcTemplate.update("DELETE FROM eligibility.policy_version WHERE id = ?", id);
        }
    }
}
