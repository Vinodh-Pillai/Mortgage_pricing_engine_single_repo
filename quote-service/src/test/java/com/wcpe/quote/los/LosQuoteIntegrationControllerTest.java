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
import com.wcpe.quote.LoanPassQuoteCatalogRepository;
import com.wcpe.quote.LoanPassQuoteModels.CatalogProduct;
import com.wcpe.quote.LoanPassQuoteModels.CatalogSnapshot;
import com.wcpe.quote.QuoteJobRepository;
import com.wcpe.quote.QuoteRepository;
import com.wcpe.quote.QuoteSnapshotRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = "quote.persistence.mode=in-memory")
@AutoConfigureMockMvc
class LosQuoteIntegrationControllerTest {
  private static final UUID LOANPASS_TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");

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

  @Test
  void unauthenticatedLosExecuteAliasIsNotExposedByQuoteService() throws Exception {
    mockMvc.perform(post("/api/v1/los/execute-summary")
            .header("X-Tenant-ID", "11111111-1111-1111-1111-111111111111")
            .header("X-Correlation-ID", "corr-los-alias")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void loanPassExecuteSummaryRejectsHeaderBodyTenantMismatchWithCleanBadRequest() throws Exception {
    mockMvc.perform(post("/api/v1/loanpass/execute-summary")
            .header("X-Tenant-ID", "11111111-1111-1111-1111-111111111111")
            .header("X-Request-ID", "quote-tenant-mismatch-001")
            .header("X-Correlation-ID", "corr-tenant-mismatch")
            .contentType(MediaType.APPLICATION_JSON)
            .content(validPayload().replace("\"tenantId\":\"tenant-los\"", "\"tenantId\":\"22222222-2222-2222-2222-222222222222\"")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("LOANPASS_TENANT_MISMATCH"))
        .andExpect(jsonPath("$.message").value("X-Tenant-ID header must match request tenantId"));
  }

  @Test
  void loanPassExecuteProductRejectsHeaderBodyTenantMismatchWithCleanBadRequest() throws Exception {
    mockMvc.perform(post("/api/v1/loanpass/execute-product")
            .header("X-Tenant-ID", "11111111-1111-1111-1111-111111111111")
            .header("X-Request-ID", "quote-product-tenant-mismatch-001")
            .header("X-Correlation-ID", "corr-product-tenant-mismatch")
            .contentType(MediaType.APPLICATION_JSON)
            .content(validPayload().replace("\"tenantId\":\"tenant-los\"", "\"tenantId\":\"22222222-2222-2222-2222-222222222222\"")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("LOANPASS_TENANT_MISMATCH"));
  }

  @Test
  void loanPassExecuteSummaryAcceptsBodyOnlyTenantAndReturnsRuntimeStatus() throws Exception {
    mockMvc.perform(post("/api/v1/loanpass/execute-summary")
            .header("X-Request-ID", "quote-body-tenant-001")
            .header("X-Correlation-ID", "corr-body-tenant")
            .contentType(MediaType.APPLICATION_JSON)
            .content(validLoanPassPayload()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.operation").value("execute-summary"))
        .andExpect(jsonPath("$.tenantId").value(LOANPASS_TENANT.toString()))
        .andExpect(jsonPath("$.correlationId").value("corr-body-tenant"));
  }

  @Test
  void loanPassExecuteProductReturnsOkAndCamelCaseAliasesRemainDocumentedCompatibilityRoutes() throws Exception {
    mockMvc.perform(post("/api/v1/loanpass/execute-product")
            .header("X-Tenant-ID", LOANPASS_TENANT.toString())
            .header("X-Correlation-ID", "corr-product-ok")
            .contentType(MediaType.APPLICATION_JSON)
            .content(validLoanPassProductPayload()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.operation").value("execute-product"))
        .andExpect(jsonPath("$.productId").value("lp-product-1"))
        .andExpect(jsonPath("$.correlationId").value("corr-product-ok"));

    mockMvc.perform(post("/api/v1/loanpass/executeSummary")
            .header("X-Tenant-ID", LOANPASS_TENANT.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .content(validLoanPassPayload()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.operation").value("execute-summary"));

    mockMvc.perform(post("/api/v1/loanpass/executeProduct")
            .header("X-Tenant-ID", LOANPASS_TENANT.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .content(validLoanPassProductPayload()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.operation").value("execute-product"));
  }

  @Test
  void loanPassExecuteSummaryRejectsMalformedTenantHeaderWithCorrelationId() throws Exception {
    mockMvc.perform(post("/api/v1/loanpass/execute-summary")
            .header("X-Tenant-ID", "not-a-uuid")
            .header("X-Correlation-ID", "corr-bad-header")
            .contentType(MediaType.APPLICATION_JSON)
            .content(validLoanPassPayload()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("LOANPASS_TENANT_INVALID"))
        .andExpect(jsonPath("$.message").value("X-Tenant-ID header must be a UUID when supplied"))
        .andExpect(jsonPath("$.correlationId").value("corr-bad-header"));
  }

  @Test
  void loanPassOpenApiContractDocumentsExecuteRoutesAndTenantHeader() throws Exception {
    ClassPathResource contract = new ClassPathResource("openapi/loanpass-quote-api.yml");
    assertThat(contract.exists()).isTrue();

    String yaml = new String(contract.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    assertThat(yaml)
        .contains("/loanpass/execute-summary:")
        .contains("/loanpass/executeSummary:")
        .contains("/loanpass/execute-product:")
        .contains("/loanpass/executeProduct:")
        .contains("'200':")
        .contains("name: X-Tenant-ID")
        .contains("required: false")
        .contains("LoanPassQuoteRequest:")
        .contains("LoanPassSummaryResponse:")
        .contains("LoanPassProductResponse:")
        .contains("tenantId")
        .contains("quoteBorrowerInfo")
        .contains("quoteAddressDTO")
        .contains("creditApplicationFields")
        .contains("correlationId")
        .contains("LOANPASS_TENANT_MISMATCH");
  }

  private String validLoanPassPayload() {
    return validPayload().replace("\"tenantId\":\"tenant-los\"", "\"tenantId\":\"" + LOANPASS_TENANT + "\"");
  }

  private String validLoanPassProductPayload() {
    return validLoanPassPayload().replace("\"scenarioVersion\":1,", "\"scenarioVersion\":1,\"productId\":\"lp-product-1\",");
  }

  private String validPayload() {
    return """
        {"tenantId":"tenant-los","requestId":"quote-001","scenarioId":"scenario-los-001","scenarioVersion":1,"requestedLockPeriods":[30],"clientContext":{"source":"LOS"},"actorId":"los:request","idempotencyKey":"idem-los","correlationId":"corr-los","preferAsync":true,"quoteBorrowerInfo":{"borrowerLastName":"Rivera","loanNumber":"LN-001","numberOfBorrowers":1},"quoteAddressDTO":{"state":"TX","zip":"78701"},"requestedLoanAmount":450000,"transactionType":"purchase","propertyInformationType":"single-family","occupancyType":"primary-residence","numberOfUnits":1,"incomeDocumentationType":"full-documentation","creditScore":745,"mortgageType":"conventional","amortizationType":"fixed","loanTermType":"30-year","desiredRateLockPeriod":30,"lockPeriodType":"30-day","creditApplicationFields":[{"fieldId":"field@base-loan-amount","value":{"type":"number","value":450000}},{"fieldId":"field@decision-credit-score","value":{"type":"number","value":745}}]}
        """;
  }

  private static CatalogSnapshot loanPassSnapshot() {
    CatalogProduct product = new CatalogProduct(
        "lp-product-1",
        "LP Product 1",
        "Investor A",
        "Conforming",
        "approved",
        List.of(30, 60),
        new BigDecimal("6.12500"),
        new BigDecimal("10025.0000"),
        Map.of("syntheticRule", "dev-only"),
        List.of("dev-only-income-documentation"),
        List.of(),
        Map.of("source", "controller-test"));
    return new CatalogSnapshot(
        LOANPASS_TENANT,
        "snapshot-controller-test",
        "synthetic-dev-only-loanpass-shape",
        true,
        "loanpass-synthetic-dev-v1",
        "controller-test",
        "loanpass-public-concept-aligned-v1",
        Instant.parse("2026-06-23T00:00:00Z"),
        "hash-controller-test",
        List.of(product),
        Map.of("devOnly", "true"));
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

    @Bean
    LoanPassQuoteCatalogRepository testLoanPassQuoteCatalogRepository() {
      return new LoanPassQuoteCatalogRepository() {
        @Override
        public Optional<CatalogSnapshot> activeSnapshot(UUID tenantId) {
          CatalogSnapshot snapshot = loanPassSnapshot();
          return snapshot.tenantId().equals(tenantId) ? Optional.of(snapshot) : Optional.empty();
        }

        @Override
        public CatalogSnapshot saveSnapshot(CatalogSnapshot snapshot) {
          return snapshot;
        }
      };
    }
  }
}
