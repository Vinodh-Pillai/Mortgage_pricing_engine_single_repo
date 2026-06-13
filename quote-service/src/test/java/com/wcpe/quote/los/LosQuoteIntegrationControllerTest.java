package com.wcpe.quote.los;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class LosQuoteIntegrationControllerTest {
  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;

  @Test
  void acceptsLosQuoteRequestAndReplaysIdempotently() throws Exception {
    String payload = """
        {"tenantId":"tenant-los","scenarioId":"scenario-los-001","scenarioVersion":1,"requestedLockPeriods":[30],"clientContext":{"source":"LOS"},"actorId":"los:request","idempotencyKey":"idem-los","correlationId":"corr-los","effectiveDate":"2026-06-12","preferAsync":true}
        """;
    MvcResult first = mockMvc.perform(post("/api/v1/los/quote-requests")
            .header("X-Request-ID", "quote-req-001")
            .header("X-Correlation-ID", "corr-los")
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.status").value("QUEUED"))
        .andReturn();

    MvcResult second = mockMvc.perform(post("/api/v1/los/quote-requests")
            .header("X-Request-ID", "quote-req-001")
            .header("X-Correlation-ID", "corr-los")
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
        .andExpect(status().isAccepted())
        .andReturn();

    JsonNode firstBody = objectMapper.readTree(first.getResponse().getContentAsString());
    JsonNode secondBody = objectMapper.readTree(second.getResponse().getContentAsString());
    assertThat(secondBody.path("jobId").asText()).isEqualTo(firstBody.path("jobId").asText());

    mockMvc.perform(get("/api/v1/los/quote-requests/{id}", firstBody.path("jobId").asText()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("QUEUED"));
  }
}
