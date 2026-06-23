package com.wcpe.quote.los;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.quote.InMemoryQuoteJobRepository;
import com.wcpe.quote.QuoteJob;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LosQuoteIntegrationServicePersistenceTest {
  private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-22T12:00:00Z"), ZoneOffset.UTC);
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void persistsLoanPassCatalogAuthorizationAndLockTermMetadataDurably() throws Exception {
    InMemoryQuoteJobRepository repository = new InMemoryQuoteJobRepository();
    LosQuoteIntegrationService firstService = new LosQuoteIntegrationService(objectMapper, repository, CLOCK);

    LosQuoteModels.LosQuoteRequest request = objectMapper.readValue(productSpecificPayloadWithMetadata(), LosQuoteModels.LosQuoteRequest.class);
    LosQuoteModels.LosQuoteResponse accepted = firstService.start(request, "quote-product-auth-accepted-001", "corr-los");

    LosQuoteIntegrationService restartedService = new LosQuoteIntegrationService(objectMapper, repository, CLOCK);
    assertThat(restartedService.get(accepted.jobId())).contains(accepted);

    QuoteJob stored = repository.findByJobId(UUID.fromString(accepted.jobId())).orElseThrow();
    assertThat(stored.requestPayload())
        .containsEntry("productId", "lp-product-001")
        .containsEntry("selectedProgramId", "lp-program-001")
        .containsEntry("priceGroupId", "lp-price-group-001")
        .containsEntry("productCatalogRef", "loanpass-product-catalog:v1")
        .containsEntry("productAuthorizationMetadataRef", "loanpass-product-auth:tenant-los:v1")
        .containsEntry("ruleCatalogRef", "loanpass-rule-catalog:v1")
        .containsEntry("stipulationCatalogRef", "loanpass-stipulation-catalog:v1")
        .containsEntry("rateCatalogRef", "loanpass-rate-catalog:v1")
        .containsEntry("lockTermCatalogRef", "loanpass-lock-term-catalog:v1")
        .containsEntry("lockPeriodType", "30-day");
    assertThat(stored.progress())
        .containsEntry("productAuthorizationPolicy", "fail-closed-unless-product-catalog-and-authorization-metadata-refs-exist")
        .containsEntry("productCatalogRef", "loanpass-product-catalog:v1")
        .containsEntry("productAuthorizationMetadataRef", "loanpass-product-auth:tenant-los:v1")
        .containsEntry("ruleCatalogRef", "loanpass-rule-catalog:v1")
        .containsEntry("stipulationCatalogRef", "loanpass-stipulation-catalog:v1")
        .containsEntry("rateCatalogRef", "loanpass-rate-catalog:v1")
        .containsEntry("lockTermCatalogRef", "loanpass-lock-term-catalog:v1");
  }

  @Test
  void productSpecificRequestRejectsWhenRequiredMetadataRefsAreMissing() throws Exception {
    InMemoryQuoteJobRepository repository = new InMemoryQuoteJobRepository();
    LosQuoteIntegrationService service = new LosQuoteIntegrationService(objectMapper, repository, CLOCK);
    LosQuoteModels.LosQuoteRequest request = objectMapper.readValue(productSpecificPayloadMissingMetadataRefs(), LosQuoteModels.LosQuoteRequest.class);

    LosQuoteValidationException exception = org.junit.jupiter.api.Assertions.assertThrows(LosQuoteValidationException.class,
        () -> service.start(request, "quote-product-auth-missing-refs-001", "corr-los"));

    assertThat(exception.code()).isEqualTo("LOS_QUOTE_PRODUCT_AUTHORIZATION_UNAVAILABLE");
    assertThat(exception.getMessage())
        .contains("missing=productAuthorizationMetadataRef,ruleCatalogRef,stipulationCatalogRef,rateCatalogRef,lockTermCatalogRef");
  }

  @Test
  void numericLoanPassCodesStillFailClosedWithoutMappingMetadata() throws Exception {
    InMemoryQuoteJobRepository repository = new InMemoryQuoteJobRepository();
    LosQuoteIntegrationService service = new LosQuoteIntegrationService(objectMapper, repository, CLOCK);
    LosQuoteModels.LosQuoteRequest request = objectMapper.readValue(validPayload().replace("\"propertyInformationType\":\"single-family\"", "\"propertyInformationType\":\"101\""), LosQuoteModels.LosQuoteRequest.class);

    org.junit.jupiter.api.Assertions.assertThrows(LosQuoteValidationException.class,
        () -> service.start(request, "quote-numeric-enum-001", "corr-los"));
  }

  private String productSpecificPayloadWithMetadata() {
    return """
        {"tenantId":"tenant-los","requestId":"quote-product-auth-accepted-001","scenarioId":"scenario-los-001","scenarioVersion":1,"requestedLockPeriods":[30],"clientContext":{"source":"LOS","mappingConfigRef":"los-mapping-config:tenant-los","productCatalogRef":"loanpass-product-catalog:v1","productAuthorizationMetadataRef":"loanpass-product-auth:tenant-los:v1","ruleCatalogRef":"loanpass-rule-catalog:v1","stipulationCatalogRef":"loanpass-stipulation-catalog:v1","rateCatalogRef":"loanpass-rate-catalog:v1","lockTermCatalogRef":"loanpass-lock-term-catalog:v1"},"actorId":"los:request","idempotencyKey":"idem-los","correlationId":"corr-los","preferAsync":true,"productId":"lp-product-001","selectedProgramId":"lp-program-001","priceGroupId":"lp-price-group-001","quoteBorrowerInfo":{"borrowerLastName":"Rivera","loanNumber":"LN-001","numberOfBorrowers":1},"quoteAddressDTO":{"state":"TX","zip":"78701"},"requestedLoanAmount":450000,"transactionType":"purchase","propertyInformationType":"single-family","occupancyType":"primary-residence","numberOfUnits":1,"incomeDocumentationType":"full-documentation","creditScore":745,"mortgageType":"conventional","amortizationType":"fixed","loanTermType":"30-year","desiredRateLockPeriod":30,"lockPeriodType":"30-day","creditApplicationFields":[{"fieldId":"field@base-loan-amount","value":{"type":"number","value":450000}},{"fieldId":"field@decision-credit-score","value":{"type":"number","value":745}}]}
        """;
  }

  private String productSpecificPayloadMissingMetadataRefs() {
    return """
        {"tenantId":"tenant-los","requestId":"quote-product-auth-missing-refs-001","scenarioId":"scenario-los-001","scenarioVersion":1,"requestedLockPeriods":[30],"clientContext":{"source":"LOS","mappingConfigRef":"los-mapping-config:tenant-los","productCatalogRef":"loanpass-product-catalog:v1"},"actorId":"los:request","idempotencyKey":"idem-los","correlationId":"corr-los","preferAsync":true,"productId":"lp-product-001","selectedProgramId":"lp-program-001","priceGroupId":"lp-price-group-001","quoteBorrowerInfo":{"borrowerLastName":"Rivera","loanNumber":"LN-001","numberOfBorrowers":1},"quoteAddressDTO":{"state":"TX","zip":"78701"},"requestedLoanAmount":450000,"transactionType":"purchase","propertyInformationType":"single-family","occupancyType":"primary-residence","numberOfUnits":1,"incomeDocumentationType":"full-documentation","creditScore":745,"mortgageType":"conventional","amortizationType":"fixed","loanTermType":"30-year","desiredRateLockPeriod":30,"lockPeriodType":"30-day","creditApplicationFields":[{"fieldId":"field@base-loan-amount","value":{"type":"number","value":450000}},{"fieldId":"field@decision-credit-score","value":{"type":"number","value":745}}]}
        """;
  }

  private String validPayload() {
    return """
        {"tenantId":"tenant-los","requestId":"quote-001","scenarioId":"scenario-los-001","scenarioVersion":1,"requestedLockPeriods":[30],"clientContext":{"source":"LOS"},"actorId":"los:request","idempotencyKey":"idem-los","correlationId":"corr-los","preferAsync":true,"quoteBorrowerInfo":{"borrowerLastName":"Rivera","loanNumber":"LN-001","numberOfBorrowers":1},"quoteAddressDTO":{"state":"TX","zip":"78701"},"requestedLoanAmount":450000,"transactionType":"purchase","propertyInformationType":"single-family","occupancyType":"primary-residence","numberOfUnits":1,"incomeDocumentationType":"full-documentation","creditScore":745,"mortgageType":"conventional","amortizationType":"fixed","loanTermType":"30-year","desiredRateLockPeriod":30,"lockPeriodType":"30-day","creditApplicationFields":[{"fieldId":"field@base-loan-amount","value":{"type":"number","value":450000}},{"fieldId":"field@decision-credit-score","value":{"type":"number","value":745}}]}
        """;
  }
}
