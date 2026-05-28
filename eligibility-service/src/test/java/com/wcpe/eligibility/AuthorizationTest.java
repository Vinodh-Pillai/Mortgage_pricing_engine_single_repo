package com.wcpe.eligibility;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.eligibility.domain.models.EligibilityRequest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class AuthorizationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("eligibility")
        .withUsername("eligibility_app")
        .withPassword("eligibility_app");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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

    /* ---- Missing X-Roles header returns 403 on POST /evaluate ---- */

    @Test
    void evaluateMissingRolesHeaderReturns403() throws Exception {
        String body = objectMapper.writeValueAsString(gf01Request);

        mockMvc.perform(post("/api/v1/tenants/11111111-1111-1111-1111-111111111111/evaluate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isForbidden());
    }

    @Test
    void replayMissingRolesHeaderReturns403() throws Exception {
        String body = objectMapper.writeValueAsString(
            java.util.Map.of("requestJson", objectMapper.writeValueAsString(gf01Request),
                             "policyVersion", 1, "ruleSetVersion", 3)
        );

        mockMvc.perform(post("/api/v1/tenants/11111111-1111-1111-1111-111111111111/replay")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isForbidden());
    }

    /* ---- Wrong role returns 403 ---- */

    @Test
    void evaluateWrongRoleReturns403() throws Exception {
        String body = objectMapper.writeValueAsString(gf01Request);

        mockMvc.perform(post("/api/v1/tenants/11111111-1111-1111-1111-111111111111/evaluate")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Roles", "UNKNOWN_ROLE")
                .content(body))
            .andExpect(status().isForbidden());
    }

    @Test
    void replayWrongRoleReturns403() throws Exception {
        String body = objectMapper.writeValueAsString(
            java.util.Map.of("requestJson", objectMapper.writeValueAsString(gf01Request),
                             "policyVersion", 1, "ruleSetVersion", 3)
        );

        mockMvc.perform(post("/api/v1/tenants/11111111-1111-1111-1111-111111111111/replay")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Roles", "UNKNOWN_ROLE")
                .content(body))
            .andExpect(status().isForbidden());
    }

    /* ---- Correct role proceeds to evaluation ---- */

    @Test
    void evaluateCorrectRoleProceedsToEvaluation() throws Exception {
        String body = objectMapper.writeValueAsString(gf01Request);

        mockMvc.perform(post("/api/v1/tenants/11111111-1111-1111-1111-111111111111/evaluate")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Roles", "ELIGIBILITY_EVALUATOR")
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ELIGIBLE"));
    }

    @Test
    void replayCorrectRoleProceedsToReplay() throws Exception {
        String body = objectMapper.writeValueAsString(
            java.util.Map.of("requestJson", objectMapper.writeValueAsString(gf01Request),
                             "policyVersion", 1, "ruleSetVersion", 3)
        );

        mockMvc.perform(post("/api/v1/tenants/11111111-1111-1111-1111-111111111111/replay")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Roles", "ELIGIBILITY_EVALUATOR")
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").exists());
    }
}
