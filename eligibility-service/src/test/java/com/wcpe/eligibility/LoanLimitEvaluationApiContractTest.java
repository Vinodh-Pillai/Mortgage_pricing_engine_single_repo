package com.wcpe.eligibility;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class LoanLimitEvaluationApiContractTest {
    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("eligibility")
        .withUsername("eligibility_app")
        .withPassword("eligibility_app");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @BeforeEach
    void clearPersistedEvaluationArtifacts() {
        jdbcTemplate.update("delete from eligibility.audit_record where tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("delete from eligibility.eligibility_decision where tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("delete from eligibility.eligibility_evaluation where tenant_id = ?", TENANT_ID);
    }

    @Test
    void evaluateV1ReturnsHardStopAndPersistsAuditEvidence() throws Exception {
        mockMvc.perform(post("/api/v1/tenants/{tenantId}/eligibility/evaluations/loan-limit", TENANT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Correlation-Id", "corr-pii03-s02")
                .content(objectMapper.writeValueAsString(sampleRequest("850000.00", "Travis", 1))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.decisions[0].ruleCode").value("CONF_LOAN_LIMIT"))
            .andExpect(jsonPath("$.decisions[0].eligibilityStatus").value("INELIGIBLE"))
            .andExpect(jsonPath("$.decisions[0].severity").value("HARD_STOP"))
            .andExpect(jsonPath("$.decisions[0].reasonCode").value("LOAN_AMOUNT_EXCEEDS_CONFORMING_LIMIT"))
            .andExpect(jsonPath("$.decisions[0].limitAmount").value(806500.00));

        assertEquals(1, jdbcTemplate.queryForObject("select count(*) from eligibility.eligibility_evaluation", Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject("select count(*) from eligibility.eligibility_decision where rule_code = 'CONF_LOAN_LIMIT'", Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject("select count(*) from eligibility.audit_record where action = 'LOAN_LIMIT_CHECK_COMPLETED'", Integer.class));
    }

    @Test
    void missingCountyReturnsMachineReadable422() throws Exception {
        mockMvc.perform(post("/api/v1/tenants/{tenantId}/eligibility/evaluations/loan-limit", TENANT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(sampleRequest("400000.00", null, 1))))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.decisions[0].eligibilityStatus").value("INSUFFICIENT_DATA"))
            .andExpect(jsonPath("$.decisions[0].reasonCode").value("MISSING_COUNTY"));
    }

    @Test
    void invalidUnitCountReturnsMachineReadable422() throws Exception {
        mockMvc.perform(post("/api/v1/tenants/{tenantId}/eligibility/evaluations/loan-limit", TENANT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(sampleRequest("400000.00", "Travis", 5))))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.decisions[0].reasonCode").value("INVALID_UNIT_COUNT"));
    }

    private Map<String, Object> sampleRequest(String loanAmount, String county, int units) {
        return Map.of(
            "scenarioId", "cf6e0657-e55d-4485-8e7a-968cc8758fc0",
            "scenarioVersion", 1,
            "asOfDate", "2026-05-13",
            "productCandidates", java.util.List.of(Map.of(
                "productVersionId", "11111111-1111-1111-1111-111111111111",
                "productCode", "CONF30",
                "investorCode", "FNMA"
            )),
            "facts", Map.of(
                "loanAmount", loanAmount,
                "propertyState", "TX",
                "propertyCounty", county == null ? "" : county,
                "units", units,
                "loanPurpose", "PURCHASE"
            )
        );
    }
}
