package com.wcpe.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wcpe.integration.LosQuoteRequestService.BorrowerCreditSummary;
import com.wcpe.integration.LosQuoteRequestService.BorrowerSummary;
import com.wcpe.integration.LosQuoteRequestService.LoanTerms;
import com.wcpe.integration.LosQuoteRequestService.LosQuoteResponse;
import com.wcpe.integration.LosQuoteRequestService.LosQuoteResult;
import com.wcpe.integration.LosQuoteRequestService.LosQuoteStatus;
import com.wcpe.integration.LosQuoteRequestService.PriceScenarioCommand;
import com.wcpe.integration.LosQuoteRequestService.PricingResponseSnapshot;
import com.wcpe.integration.LosQuoteRequestService.PropertySummary;
import com.wcpe.integration.LosQuoteRequestService.SubmitLosQuoteCommand;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class LosQuoteRequestServiceTest {
  private static final String TENANT_ONE = "11111111-1111-1111-1111-111111111111";
  private static final String TENANT_TWO = "22222222-2222-2222-2222-222222222222";
  private static final String CHANNEL_ID = "los-main";
  private static final String PRODUCT_REF = "product-ref-conventional";
  private static final String CONFIG_REF = "pricing-config-v2026-06";

  @Test
  void submitsLosQuoteRequestWithPricingAuditAndOutboxEvidence() {
    LosQuoteRequestService service = service(command -> PricingResponseSnapshot.priced("quote-123", Map.of("priceSummaryRef", "summary-hash-1"), List.of("redacted-credit-score")));

    LosQuoteResult result = service.submit(command("idem-1", "loan-1"));

    assertTrue(result.valid());
    LosQuoteResponse response = result.value().orElseThrow();
    assertEquals(LosQuoteStatus.PRICED, response.status());
    assertEquals("quote-123", response.quoteId());
    assertEquals("corr-PII-16-S02", response.correlationId());
    assertEquals(1, service.requestsForTenant(TENANT_ONE).size());
    assertEquals(0, service.requestsForTenant(TENANT_TWO).size());
    assertEquals(LosQuoteRequestService.ACCEPTED_EVENT_TYPE, service.outboxEvents().get(0).eventType());
    assertEquals(LosQuoteRequestService.PRICED_EVENT_TYPE, service.outboxEvents().get(1).eventType());
    assertEquals(LosQuoteRequestService.AUDIT_ACTION, service.auditRecords().get(0).action());
    assertFalse(service.outboxEvents().get(1).payload().toString().contains("720"));
  }

  @Test
  void replaysSameIdempotencyKeyAndRejectsChangedBody() {
    LosQuoteRequestService service = service(command -> PricingResponseSnapshot.priced("quote-123", Map.of("priceSummaryRef", "summary-hash-1"), List.of()));

    LosQuoteResponse first = service.submit(command("idem-1", "loan-1")).value().orElseThrow();
    LosQuoteResponse replay = service.submit(command("idem-1", "loan-1")).value().orElseThrow();
    LosQuoteResult conflict = service.submit(command("idem-1", "loan-2"));

    assertEquals(first, replay);
    assertFalse(conflict.valid());
    assertEquals("IDEMPOTENCY_CONFLICT", conflict.error().orElseThrow().reason());
    assertEquals(2, service.outboxEvents().size());
  }

  @Test
  void failsClosedForRequiredInputsUnsupportedSchemaAndSensitiveBorrowerFields() {
    LosQuoteRequestService service = service(command -> PricingResponseSnapshot.priced("quote-123", Map.of(), List.of()));

    assertEquals("VALIDATION_FAILED", service.submit(commandWithCredit("idem-1", 299)).error().orElseThrow().reason());
    SubmitLosQuoteCommand unsupportedSchema =
        new SubmitLosQuoteCommand(
            TENANT_ONE,
            CHANNEL_ID,
            "idem-2",
            "los-client",
            "loan-1",
            new BorrowerSummary("borrower-token", false, false),
            new PropertySummary("VA", "SINGLE_FAMILY", new BigDecimal("450000")),
            new LoanTerms(new BigDecimal("300000"), new BigDecimal("6.750"), "PURCHASE", "FIRST"),
            new BorrowerCreditSummary(720, "credit-bucket-a"),
            "PRIMARY_RESIDENCE",
            "FLOAT",
            PRODUCT_REF,
            Instant.parse("2026-06-04T02:00:00Z"),
            "approved-los",
            "UNKNOWN",
            "corr-PII-16-S02");
    assertEquals("UNSUPPORTED_SCHEMA_VERSION", service.submit(unsupportedSchema).error().orElseThrow().reason());

    SubmitLosQuoteCommand pii =
        new SubmitLosQuoteCommand(
            TENANT_ONE,
            CHANNEL_ID,
            "idem-3",
            "los-client",
            "loan-1",
            new BorrowerSummary("borrower-token", true, false),
            new PropertySummary("VA", "SINGLE_FAMILY", new BigDecimal("450000")),
            new LoanTerms(new BigDecimal("300000"), new BigDecimal("6.750"), "PURCHASE", "FIRST"),
            new BorrowerCreditSummary(720, "credit-bucket-a"),
            "PRIMARY_RESIDENCE",
            "FLOAT",
            PRODUCT_REF,
            Instant.parse("2026-06-04T02:00:00Z"),
            "approved-los",
            LosQuoteRequestService.SUPPORTED_SCHEMA_VERSION,
            "corr-PII-16-S02");
    assertEquals("VALIDATION_FAILED", service.submit(pii).error().orElseThrow().reason());
    assertTrue(service.outboxEvents().isEmpty());
  }

  @Test
  void failsClosedForInactiveChannelAndDisabledProductWithoutPricingCall() {
    AtomicInteger pricingCalls = new AtomicInteger();
    LosQuoteRequestService service = service(command -> {
      pricingCalls.incrementAndGet();
      return PricingResponseSnapshot.priced("quote-123", Map.of(), List.of());
    });

    LosQuoteResult inactive = service.submit(command("idem-1", "loan-1", "inactive-channel", PRODUCT_REF));
    LosQuoteResult disabledProduct = service.submit(command("idem-2", "loan-2", CHANNEL_ID, "product-ref-disabled"));

    assertFalse(inactive.valid());
    assertEquals("POLICY_NOT_SATISFIED", inactive.error().orElseThrow().reason());
    assertTrue(disabledProduct.valid());
    assertEquals(LosQuoteStatus.REJECTED, disabledProduct.value().orElseThrow().status());
    assertEquals("DISABLED_CHANNEL_PRODUCT", disabledProduct.value().orElseThrow().reasonCodes().get(0));
    assertEquals(0, pricingCalls.get());
  }

  @Test
  void retriesOneSafePricingTimeoutAndReturnsRetryableFailureWhenStillUnavailable() {
    AtomicInteger calls = new AtomicInteger();
    LosQuoteRequestService service = service(command -> {
      calls.incrementAndGet();
      return PricingResponseSnapshot.retryableTimeout();
    });

    LosQuoteResult result = service.submit(command("idem-1", "loan-1"));

    assertTrue(result.valid());
    assertEquals(2, calls.get());
    assertEquals(LosQuoteStatus.FAILED_RETRYABLE, result.value().orElseThrow().status());
    assertEquals("PRICING_SERVICE_TIMEOUT", result.value().orElseThrow().reasonCodes().get(0));
    assertEquals(LosQuoteRequestService.REJECTED_EVENT_TYPE, service.outboxEvents().get(1).eventType());
  }

  @Test
  void fetchIsTenantIsolated() {
    LosQuoteRequestService service = service(command -> PricingResponseSnapshot.priced("quote-123", Map.of("priceSummaryRef", "summary-hash-1"), List.of()));
    LosQuoteResponse created = service.submit(command("idem-1", "loan-1")).value().orElseThrow();

    assertTrue(service.fetch(TENANT_ONE, CHANNEL_ID, created.requestId(), "corr-PII-16-S02").valid());
    assertFalse(service.fetch(TENANT_TWO, CHANNEL_ID, created.requestId(), "corr-PII-16-S02").valid());
  }

  private LosQuoteRequestService service(LosQuoteRequestService.PricingClient pricingClient) {
    LosQuoteRequestService service =
        new LosQuoteRequestService(Clock.fixed(Instant.parse("2026-06-04T02:00:00Z"), ZoneOffset.UTC), pricingClient);
    service.configureChannel(TENANT_ONE, CHANNEL_ID, true, List.of(PRODUCT_REF), CONFIG_REF);
    service.configureChannel(TENANT_TWO, CHANNEL_ID, true, List.of(PRODUCT_REF), CONFIG_REF);
    service.configureChannel(TENANT_ONE, "inactive-channel", false, List.of(PRODUCT_REF), CONFIG_REF);
    return service;
  }

  private SubmitLosQuoteCommand command(String idempotencyKey, String losLoanId) {
    return command(idempotencyKey, losLoanId, CHANNEL_ID, PRODUCT_REF);
  }

  private SubmitLosQuoteCommand command(String idempotencyKey, String losLoanId, String channelId, String productPreference) {
    return new SubmitLosQuoteCommand(
        TENANT_ONE,
        channelId,
        idempotencyKey,
        "los-client",
        losLoanId,
        new BorrowerSummary("borrower-token", false, false),
        new PropertySummary("VA", "SINGLE_FAMILY", new BigDecimal("450000")),
        new LoanTerms(new BigDecimal("300000"), new BigDecimal("6.750"), "PURCHASE", "FIRST"),
        new BorrowerCreditSummary(720, "credit-bucket-a"),
        "PRIMARY_RESIDENCE",
        "FLOAT",
        productPreference,
        Instant.parse("2026-06-04T02:00:00Z"),
        "approved-los",
        LosQuoteRequestService.SUPPORTED_SCHEMA_VERSION,
        "corr-PII-16-S02");
  }

  private SubmitLosQuoteCommand commandWithCredit(String idempotencyKey, int representativeCreditScore) {
    return new SubmitLosQuoteCommand(
        TENANT_ONE,
        CHANNEL_ID,
        idempotencyKey,
        "los-client",
        "loan-1",
        new BorrowerSummary("borrower-token", false, false),
        new PropertySummary("VA", "SINGLE_FAMILY", new BigDecimal("450000")),
        new LoanTerms(new BigDecimal("300000"), new BigDecimal("6.750"), "PURCHASE", "FIRST"),
        new BorrowerCreditSummary(representativeCreditScore, "credit-bucket-a"),
        "PRIMARY_RESIDENCE",
        "FLOAT",
        PRODUCT_REF,
        Instant.parse("2026-06-04T02:00:00Z"),
        "approved-los",
        LosQuoteRequestService.SUPPORTED_SCHEMA_VERSION,
        "corr-PII-16-S02");
  }
}
