package com.wcpe.scenario.domain;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class ScenarioApiIdempotencyContractTest {
  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
      .withDatabaseName("scenario")
      .withUsername("scenario_app")
      .withPassword("scenario_app");

  @DynamicPropertySource
  static void postgresProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;

  private final UUID tenantId = UUID.fromString("018fa4f0-1a4f-7e99-a02d-1b0100010101");

  @Test
  void requiresIdempotencyKeyForMutations() throws Exception {
    mvc.perform(post("/api/v1/tenants/{tenantId}/scenarios", tenantId)
        .header("X-Roles", "SCENARIO_WRITER")
        .contentType(MediaType.APPLICATION_JSON)
        .content(createRequest("Smith purchase")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void replayedMutationReturnsReplayHeader() throws Exception {
    mvc.perform(post("/api/v1/tenants/{tenantId}/scenarios", tenantId)
        .header("X-Roles", "SCENARIO_WRITER")
        .header("Idempotency-Key", "api-replay-key")
        .contentType(MediaType.APPLICATION_JSON)
        .content(createRequest("Smith purchase")))
        .andExpect(status().isCreated())
        .andExpect(header().doesNotExist("Idempotency-Replayed"));

    MvcResult replay = mvc.perform(post("/api/v1/tenants/{tenantId}/scenarios", tenantId)
        .header("X-Roles", "SCENARIO_WRITER")
        .header("Idempotency-Key", "api-replay-key")
        .contentType(MediaType.APPLICATION_JSON)
        .content(createRequest("Smith purchase")))
        .andExpect(status().isCreated())
        .andExpect(header().string("Idempotency-Replayed", "true"))
        .andReturn();

    assertThat(replay.getResponse().getContentAsString()).contains("scenarioId");
  }

  private String createRequest(String scenarioName) throws Exception {
    return mapper.writeValueAsString(Map.of(
        "quoteIntent", "PURCHASE",
        "channel", "RETAIL",
        "scenarioName", scenarioName,
        "externalLoanId", "LOS-API-1",
        "sourceSystem", "PRICING_WORKBENCH",
        "initialFacts", Map.of("propertyState", "TX")));
  }
}
