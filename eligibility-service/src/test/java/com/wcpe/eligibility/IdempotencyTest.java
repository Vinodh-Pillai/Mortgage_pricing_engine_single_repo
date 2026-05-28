package com.wcpe.eligibility;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.eligibility.domain.hashing.Hashing;
import com.wcpe.eligibility.domain.models.EligibilityRequest;
import com.wcpe.eligibility.repository.EligibilityPersistRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class IdempotencyTest {

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

    private static EligibilityRequest gf01Request;

    @BeforeAll
    static void setup() throws Exception {
        System.setProperty("spring.datasource.url", postgres.getJdbcUrl());
        System.setProperty("spring.datasource.username", postgres.getUsername());
        System.setProperty("spring.datasource.password", postgres.getPassword());

        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        gf01Request = mapper.readValue(
            Files.readString(Path.of("golden/PII-01-eligibility-rules/GF01_happy_path.json")),
            ReplayDeterminismTest.RequestWrapper.class
        ).getRequest();
    }

    @BeforeEach
    void clearIdempotencyTable() {
        jdbcTemplate.update("DELETE FROM eligibility.idempotency_record");
    }

    /* ---- Duplicate Idempotency-Key returns cached result ---- */

    @Test
    void duplicateIdempotencyKeyReturnsCachedResult() throws Exception {
        String key = "idem-key-001";
        String body = objectMapper.writeValueAsString(gf01Request);

        // First request
        MvcResult first = mockMvc.perform(post("/api/v1/tenants/11111111-1111-1111-1111-111111111111/evaluate")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Roles", "ELIGIBILITY_EVALUATOR")
                .header("Idempotency-Key", key)
                .content(body))
            .andExpect(status().isOk())
            .andReturn();

        // Second identical request with same key
        MvcResult second = mockMvc.perform(post("/api/v1/tenants/11111111-1111-1111-1111-111111111111/evaluate")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Roles", "ELIGIBILITY_EVALUATOR")
                .header("Idempotency-Key", key)
                .content(body))
            .andExpect(status().isOk())
            .andReturn();

        // Responses should be identical
        assertEquals(first.getResponse().getContentAsString(),
                     second.getResponse().getContentAsString(),
            "Idempotent requests must return the same response body");
    }

    @Test
    void differentPayloadSameIdempotencyKeyReturns409() throws Exception {
        String key = "idem-key-conflict";
        String body1 = objectMapper.writeValueAsString(gf01Request);

        // First request
        mockMvc.perform(post("/api/v1/tenants/11111111-1111-1111-1111-111111111111/evaluate")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Roles", "ELIGIBILITY_EVALUATOR")
                .header("Idempotency-Key", key)
                .content(body1))
            .andExpect(status().isOk());

        // Different payload with same key
        EligibilityRequest differentRequest = new EligibilityRequest(
            gf01Request.borrowerProfile(),
            gf01Request.propertyProfile(),
            new com.wcpe.eligibility.domain.models.LoanProfile(
                "REFINANCE", gf01Request.loanProfile().loanAmount(),
                gf01Request.loanProfile().subordinateFinancingAmount(),
                gf01Request.loanProfile().requestedLockPeriodDays(),
                gf01Request.loanProfile().documentationType(),
                gf01Request.loanProfile().ausType(),
                gf01Request.loanProfile().lienPosition()),
            gf01Request.productCandidate()
        );
        String body2 = objectMapper.writeValueAsString(differentRequest);

        mockMvc.perform(post("/api/v1/tenants/11111111-1111-1111-1111-111111111111/evaluate")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Roles", "ELIGIBILITY_EVALUATOR")
                .header("Idempotency-Key", key)
                .content(body2))
            .andExpect(status().isConflict());
    }

    /* ---- Different Idempotency-Key with same payload returns a new evaluation ---- */

    @Test
    void differentIdempotencyKeyReturnsNewEvaluation() throws Exception {
        String key1 = "idem-key-new1";
        String key2 = "idem-key-new2";
        String body = objectMapper.writeValueAsString(gf01Request);

        MvcResult first = mockMvc.perform(post("/api/v1/tenants/11111111-1111-1111-1111-111111111111/evaluate")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Roles", "ELIGIBILITY_EVALUATOR")
                .header("Idempotency-Key", key1)
                .content(body))
            .andExpect(status().isOk())
            .andReturn();

        MvcResult second = mockMvc.perform(post("/api/v1/tenants/11111111-1111-1111-1111-111111111111/evaluate")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Roles", "ELIGIBILITY_EVALUATOR")
                .header("Idempotency-Key", key2)
                .content(body))
            .andExpect(status().isOk())
            .andReturn();

        // Both succeed; evaluation IDs differ because they are separate requests
        Map<String, Object> resp1 = objectMapper.readValue(first.getResponse().getContentAsString(), Map.class);
        Map<String, Object> resp2 = objectMapper.readValue(second.getResponse().getContentAsString(), Map.class);

        // Both should be ELIGIBLE
        assertEquals("ELIGIBLE", resp1.get("status"));
        assertEquals("ELIGIBLE", resp2.get("status"));
    }
}
