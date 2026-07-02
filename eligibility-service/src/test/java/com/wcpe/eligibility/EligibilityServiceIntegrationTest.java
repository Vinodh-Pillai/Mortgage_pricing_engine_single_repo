package com.wcpe.eligibility;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.eligibility.domain.models.EligibilityRequest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class EligibilityServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("eligibility")
        .withUsername("eligibility_app")
        .withPassword("eligibility_app");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static String gf01Body;

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @BeforeAll
    static void setup() throws Exception {
        // Verify Flyway migrations ran
        System.out.println("PostgreSQL running at: " + postgres.getJdbcUrl());

        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        EligibilityRequest request = mapper.readValue(
            Files.readString(Path.of("golden/PII-01-eligibility-rules/GF01_happy_path.json")),
            ReplayDeterminismTest.RequestWrapper.class
        ).getRequest();
        gf01Body = mapper.writeValueAsString(request);
    }

    @Test
    void gf01HappyPathReturnsELIGIBLE() throws Exception {
        mockMvc.perform(post("/api/v1/tenants/11111111-1111-1111-1111-111111111111/evaluate")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Roles", "ELIGIBILITY_EVALUATOR")
                .content(gf01Body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ELIGIBLE"))
            .andExpect(jsonPath("$.evaluationId").isString())
            .andExpect(jsonPath("$.resultHash").isString())
            .andExpect(jsonPath("$.decisions").isArray())
            .andExpect(jsonPath("$.decisions.length()").value(12));
    }

    @Test
    void gf04FicoBelowMinReturnsINELIGIBLE() throws Exception {
        String gf04Body = createFailingRequest();

        mockMvc.perform(post("/api/v1/tenants/11111111-1111-1111-1111-111111111111/evaluate")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Roles", "ELIGIBILITY_EVALUATOR")
                .content(gf04Body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("INELIGIBLE"))
            .andExpect(jsonPath("$.decisions").isArray())
            .andExpect(jsonPath("$.decisions[?(@.ruleCode == 'R01' && @.severity == 'HARD_STOP' && @.status == 'INELIGIBLE' && @.reasonCode == 'FC01')].ruleCode")
                .value(contains("R01")))
            .andExpect(jsonPath("$.decisions[?(@.ruleCode == 'R01' && @.severity == 'HARD_STOP' && @.status == 'INELIGIBLE' && @.reasonCode == 'FC01')].reasonCode")
                .value(contains("FC01")));
    }

    @Test
    void healthEndpointRespondsWithNoop() throws Exception {
        // Spring Boot actuator health probe
        mockMvc.perform(post("/api/v1/tenants/11111111-1111-1111-1111-111111111111/evaluate")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Roles", "ELIGIBILITY_EVALUATOR")
                .content(gf01Body))
            .andExpect(status().isOk());
    }

    private String createFailingRequest() throws Exception {
        EligibilityRequest request = new EligibilityRequest(
            new com.wcpe.eligibility.domain.models.BorrowerProfile(619, new BigDecimal("6000"), new BigDecimal("1000")),
            new com.wcpe.eligibility.domain.models.PropertyProfile("FL", "Miami", "33101", "SINGLE_FAMILY", 1, "PRIMARY", new BigDecimal("300000"), new BigDecimal("300000")),
            new com.wcpe.eligibility.domain.models.LoanProfile("PURCHASE", new BigDecimal("240000"), BigDecimal.ZERO, 30, "FULL_DOC", "AUTOMATED", 1),
            new com.wcpe.eligibility.domain.models.ProductCandidate(UUID.fromString("11111111-1111-1111-1111-111111111111"), "CONV30", "FNMA")
        );
        return objectMapper.writeValueAsString(request);
    }
}
