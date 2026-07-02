package com.wcpe.eligibility;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.eligibility.domain.models.QuoteSubmissionRequest;
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
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class Pii03S01QuoteSubmissionTest {
    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("eligibility")
        .withUsername("eligibility_app")
        .withPassword("eligibility_app");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @BeforeEach
    void clearQuoteSubmissionArtifacts() {
        jdbcTemplate.update("delete from eligibility.outbox_event where tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("delete from eligibility.audit_record where tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("delete from eligibility.quote_option where tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("delete from eligibility.quote where tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("delete from eligibility.audit_package where tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("delete from eligibility.scenario_fact_issue where tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("delete from eligibility.scenario where tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("delete from eligibility.idempotency_record where tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("delete from eligibility.eligibility_decision where tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("delete from eligibility.eligibility_evaluation where tenant_id = ?", TENANT_ID);
    }

    @Test
    void submitsConventionalEligibilityCoreAndPersistsQuoteArtifacts() throws Exception {
        String body = objectMapper.writeValueAsString(validRequest());

        mockMvc.perform(post("/api/v1/tenants/{tenantId}/conventional-eligibility-core", TENANT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "pii03-s01-happy")
                .header("X-Correlation-Id", "corr-pii03-s01")
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.quoteStatus").value("ELIGIBILITY_ONLY"))
            .andExpect(jsonPath("$.scenarioVersion").value(1))
            .andExpect(jsonPath("$.resultHash").isString())
            .andExpect(jsonPath("$.options").isArray());

        assertEquals(1, jdbcTemplate.queryForObject("select count(*) from eligibility.scenario where tenant_id = ?", Integer.class, TENANT_ID));
        assertEquals(1, jdbcTemplate.queryForObject("select count(*) from eligibility.quote where tenant_id = ?", Integer.class, TENANT_ID));
        assertTrue(jdbcTemplate.queryForObject("select count(*) from eligibility.quote_option where tenant_id = ?", Integer.class, TENANT_ID) > 0);
        assertEquals(4, jdbcTemplate.queryForObject("select count(*) from eligibility.outbox_event where tenant_id = ?", Integer.class, TENANT_ID));
    }

    @Test
    void duplicateIdempotencyKeyReplaysOriginalQuoteResponse() throws Exception {
        String body = objectMapper.writeValueAsString(validRequest());
        MvcResult first = mockMvc.perform(post("/api/v1/tenants/{tenantId}/conventional-eligibility-core", TENANT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "pii03-s01-replay")
                .content(body))
            .andExpect(status().isCreated())
            .andReturn();

        MvcResult second = mockMvc.perform(post("/api/v1/tenants/{tenantId}/conventional-eligibility-core", TENANT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "pii03-s01-replay")
                .content(body))
            .andExpect(status().isCreated())
            .andReturn();

        Map<String, Object> firstBody = objectMapper.readValue(first.getResponse().getContentAsString(), Map.class);
        Map<String, Object> secondBody = objectMapper.readValue(second.getResponse().getContentAsString(), Map.class);
        assertEquals(firstBody.get("quoteId"), secondBody.get("quoteId"));
        assertEquals(firstBody.get("resultHash"), secondBody.get("resultHash"));
    }

    @Test
    void invalidScenarioFailsClosedWithProblemDetails() throws Exception {
        QuoteSubmissionRequest invalid = new QuoteSubmissionRequest(
            "CONVENTIONAL_PURCHASE",
            "RETAIL",
            new QuoteSubmissionRequest.BorrowerInput(900, new BigDecimal("12500.00"), new BigDecimal("1850.00")),
            new QuoteSubmissionRequest.PropertyInput("TX", "Travis", "78701", "SINGLE_FAMILY", 1, "PRIMARY_RESIDENCE", new BigDecimal("500000.00"), null),
            new QuoteSubmissionRequest.LoanInput("PURCHASE", BigDecimal.ZERO, BigDecimal.ZERO, 30, "DU", "FULL_DOC"),
            "22222222-2222-2222-2222-222222222222"
        );

        mockMvc.perform(post("/api/v1/tenants/{tenantId}/conventional-eligibility-core", TENANT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "pii03-s01-invalid")
                .content(objectMapper.writeValueAsString(invalid)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.fieldErrors").isArray());
    }

    private QuoteSubmissionRequest validRequest() {
        return new QuoteSubmissionRequest(
            "CONVENTIONAL_PURCHASE",
            "RETAIL",
            new QuoteSubmissionRequest.BorrowerInput(742, new BigDecimal("12500.00"), new BigDecimal("1850.00")),
            new QuoteSubmissionRequest.PropertyInput("TX", "Travis", "78701", "SINGLE_FAMILY", 1, "PRIMARY_RESIDENCE", new BigDecimal("500000.00"), null),
            new QuoteSubmissionRequest.LoanInput("PURCHASE", new BigDecimal("400000.00"), BigDecimal.ZERO, 30, "DU", "FULL_DOC"),
            "22222222-2222-2222-2222-222222222222"
        );
    }
}
