package com.wcpe.quote.los;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.quote.InMemoryQuoteJobRepository;
import com.wcpe.quote.InMemoryQuoteRepository;
import com.wcpe.quote.InMemoryQuoteSnapshotRepository;
import com.wcpe.quote.QuoteJobRepository;
import com.wcpe.quote.QuoteRepository;
import com.wcpe.quote.QuoteSnapshotRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = "quote.persistence.mode=in-memory")
@AutoConfigureMockMvc
class LosQuoteIntegrationControllerTest {
  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;

  @Test
  void acceptsLosQuoteRequestAndReplaysIdempotently() throws Exception {
    String payload = """
        {"tenantId":"tenant-los","requestId":"quote-001","scenarioId":"scenario-los-001","scenarioVersion":1,"requestedLockPeriods":[30],"clientContext":{"source":"LOS"},"actorId":"los:request","idempotencyKey":"idem-los","correlationId":"corr-los","preferAsync":true,"quoteBorrowerInfo":{"borrowerLastName":"Rivera","loanNumber":"LN-001","numberOfBorrowers":1},"quoteAddressDTO":{"state":"TX","zip":"78701"},"requestedLoanAmount":450000,"transactionType":"purchase","propertyInformationType":"single-family","occupancyType":"primary-residence","numberOfUnits":1,"incomeDocumentationType":"full-documentation","creditScore":745,"mortgageType":"conventional","amortizationType":"fixed","loanTermType":"30-year","desiredRateLockPeriod":30,"lockPeriodType":"30-day","creditApplicationFields":[{"fieldId":"field@base-loan-amount","value":{"type":"number","value":450000}},{"fieldId":"field@decision-credit-score","value":{"type":"number","value":745}}]}
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
        .andExpect(jsonPath("$.status").value("QUEUED"))
        .andExpect(jsonPath("$.progress.loanPassPublicApiDocsUrl").value("https://docs.loanpass.io/public-api/index.html"))
        .andExpect(jsonPath("$.progress.loanPassPublicApiSchemaUrl").value("https://api.loanpass.io/v1/swagger/schema.json"))
        .andExpect(jsonPath("$.progress.loanPassPublicApiOperationConcepts").value("execute-summary,execute-product"))
        .andExpect(jsonPath("$.progress.productAuthorizationPolicy").value("fail-closed-unless-product-catalog-and-authorization-metadata-refs-exist"));
  }

  @Test
  void acceptsLosQuoteRequestWithoutPipelineScenarioFieldsAndRecordsAuditRefs() throws Exception {
    String payload = """
        {"tenantId":"tenant-los","requestId":"quote-no-scenario-001","requestedLockPeriods":[30],"clientContext":{"source":"LOS","requestSnapshotRef":"los-request-snapshot:tenant-los:quote-no-scenario-001","mappingConfigRef":"los-mapping-config:tenant-los"},"actorId":"los:request","idempotencyKey":"idem-los-no-scenario","correlationId":"corr-los-no-scenario","preferAsync":true,"quoteBorrowerInfo":{"borrowerLastName":"Rivera","loanNumber":"LN-001","numberOfBorrowers":1},"quoteAddressDTO":{"state":"TX","zip":"78701"},"requestedLoanAmount":450000,"transactionType":"purchase","propertyInformationType":"single-family","occupancyType":"primary-residence","numberOfUnits":1,"incomeDocumentationType":"full-documentation","creditScore":745,"mortgageType":"conventional","amortizationType":"fixed","loanTermType":"30-year","desiredRateLockPeriod":30,"lockPeriodType":"30-day","creditApplicationFields":[{"fieldId":"field@base-loan-amount","value":{"type":"number","value":450000}},{"fieldId":"field@decision-credit-score","value":{"type":"number","value":745}}]}
        """;

    MvcResult accepted = mockMvc.perform(post("/api/v1/los/quote-requests")
            .header("X-Request-ID", "quote-no-scenario-001")
            .header("X-Correlation-ID", "corr-los-no-scenario")
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.status").value("QUEUED"))
        .andExpect(jsonPath("$.progress.pipelineScenarioLinked").value("false"))
        .andExpect(jsonPath("$.progress.requestSnapshotRef").value("los-request-snapshot:tenant-los:quote-no-scenario-001"))
        .andExpect(jsonPath("$.progress.mappingConfigRef").value("los-mapping-config:tenant-los"))
        .andReturn();

    JsonNode body = objectMapper.readTree(accepted.getResponse().getContentAsString());
    mockMvc.perform(get("/api/v1/los/quote-requests/{id}", body.path("jobId").asText()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.progress.pipelineScenarioLinked").value("false"));
  }

  @Test
  void numericEnumValuesFailClosedWithoutConfiguredMapping() throws Exception {
    String payload = validPayload().replace("\"propertyInformationType\":\"single-family\"", "\"propertyInformationType\":\"101\"");

    mockMvc.perform(post("/api/v1/los/quote-requests")
            .header("X-Request-ID", "quote-numeric-enum-001")
            .header("X-Correlation-ID", "corr-los")
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("LOS_QUOTE_ENUM_MAPPING_REQUIRED"));
  }

  @Test
  void productSpecificFieldsFailClosedWithoutTenantAuthorizationMetadata() throws Exception {
    String payload = validPayload().replace("\"scenarioVersion\":1,", "\"scenarioVersion\":1,\"priceGroupId\":\"pg-unauthorized\",");

    mockMvc.perform(post("/api/v1/los/quote-requests")
            .header("X-Request-ID", "quote-product-auth-001")
            .header("X-Correlation-ID", "corr-los")
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("LOS_QUOTE_PRODUCT_AUTHORIZATION_UNAVAILABLE"));
  }

  private String validPayload() {
    return """
        {"tenantId":"tenant-los","requestId":"quote-001","scenarioId":"scenario-los-001","scenarioVersion":1,"requestedLockPeriods":[30],"clientContext":{"source":"LOS"},"actorId":"los:request","idempotencyKey":"idem-los","correlationId":"corr-los","preferAsync":true,"quoteBorrowerInfo":{"borrowerLastName":"Rivera","loanNumber":"LN-001","numberOfBorrowers":1},"quoteAddressDTO":{"state":"TX","zip":"78701"},"requestedLoanAmount":450000,"transactionType":"purchase","propertyInformationType":"single-family","occupancyType":"primary-residence","numberOfUnits":1,"incomeDocumentationType":"full-documentation","creditScore":745,"mortgageType":"conventional","amortizationType":"fixed","loanTermType":"30-year","desiredRateLockPeriod":30,"lockPeriodType":"30-day","creditApplicationFields":[{"fieldId":"field@base-loan-amount","value":{"type":"number","value":450000}},{"fieldId":"field@decision-credit-score","value":{"type":"number","value":745}}]}
        """;
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class TestOnlyInMemoryPersistenceConfiguration {
    @Bean
    QuoteRepository testQuoteRepository() {
      return new InMemoryQuoteRepository();
    }

    @Bean
    QuoteJobRepository testQuoteJobRepository() {
      return new InMemoryQuoteJobRepository();
    }

    @Bean
    QuoteSnapshotRepository testQuoteSnapshotRepository() {
      return new InMemoryQuoteSnapshotRepository();
    }
  }
}
