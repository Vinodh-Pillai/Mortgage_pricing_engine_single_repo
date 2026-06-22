package com.wcpe.scenarioanalysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wcpe.scenarioanalysis.ProductComparisonService.IdempotencyConflictException;
import com.wcpe.scenarioanalysis.ProductComparisonService.PolicyNotSatisfiedException;
import com.wcpe.scenarioanalysis.ProductComparisonService.ProductComparisonCommand;
import com.wcpe.scenarioanalysis.ProductComparisonService.ProductPromotionCommand;
import com.wcpe.scenarioanalysis.ProductComparisonService.ValidationException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProductComparisonServiceTest {
  private TestOnlyInMemoryProductComparisonRepository repository;
  private ProductComparisonService service;

  @BeforeEach
  void setUp() {
    repository = new TestOnlyInMemoryProductComparisonRepository();
    service = new ProductComparisonService(
        repository,
        Clock.fixed(Instant.parse("2026-06-01T12:00:00Z"), ZoneOffset.UTC));
  }

  @Test
  void comparableProductPolicyTestFiltersByChannelAndStateWithoutInventingCandidates() {
    var response = service.getComparableProductsConfig("tenant-001", "quote-123", "retail", "conventional", "investor-a");

    assertThat(response.candidates()).isEmpty();
    assertThat(response.dependencyStatus()).isEqualTo("PRODUCT_CATALOG_CONFIG_UNAVAILABLE");
    assertThat(response.message()).contains("No candidate product defaults are assumed");
  }

  @Test
  void productComparisonITPricesEligibleAndCapturesIneligibleAsDependencyGaps() {
    var response = service.createRun(validCommand("idem-001", List.of("conv-30", "conv-15", "conv-30"), true));

    assertThat(response.status()).isEqualTo("COMPLETED_WITH_DEPENDENCY_GAPS");
    assertThat(response.sensitivityAxis()).isEqualTo("PRODUCT");
    assertThat(response.resultSummary().candidateCount()).isEqualTo(2);
    assertThat(response.rows()).extracting(row -> row.productId()).containsExactly("conv-30", "conv-15");
    assertThat(response.rows()).allSatisfy(row -> {
      assertThat(row.eligibility()).isEqualTo("INELIGIBLE");
      assertThat(row.pricingSummary().status()).isEqualTo("UNAVAILABLE");
      assertThat(row.paymentSummary().status()).isEqualTo("UNAVAILABLE");
      assertThat(row.aprSummary().status()).isEqualTo("UNAVAILABLE");
      assertThat(row.ruleHits()).contains(
          "product_catalog_version_unavailable",
          "eligibility_dependency_unavailable",
          "pricing_dependency_unavailable",
          "payment_dependency_unavailable",
          "apr_dependency_unavailable");
      assertThat(row.resultHash()).startsWith("sha256:");
    });
    assertThat(response.resultSummary().disclaimer()).contains("stores references and result snapshots only");
    assertThat(repository.size()).isEqualTo(1);
  }

  @Test
  void productComparisonResultTestPinsEligibleRowsFirstBySuppressingIneligibleWhenRequested() {
    var response = service.createRun(validCommand("idem-002", List.of("conv-30", "arm-7"), false));

    assertThat(response.rows()).isEmpty();
    assertThat(response.validationMessages()).contains("ineligible candidate products were suppressed because includeIneligible is false");
  }

  @Test
  void productDeltaTestComputesRatePricePaymentDeltasAsUnavailableUntilPricingDependenciesExist() {
    var row = service.createRun(validCommand("idem-003", List.of("conv-30"), true)).rows().get(0);

    assertThat(row.deltas().rateDelta()).isNull();
    assertThat(row.deltas().priceDelta()).isNull();
    assertThat(row.deltas().paymentDeltaCents()).isNull();
    assertThat(row.deltas().cashToCloseDeltaCents()).isNull();
  }

  @Test
  void productCatalogVersionITRejectsStaleCandidateVersionByFailingClosedWhenCatalogDependencyIsMissing() {
    var response = service.createRun(validCommand("idem-004", List.of("retired-product"), true));

    assertThat(response.rows()).singleElement().satisfies(row -> {
      assertThat(row.eligibility()).isEqualTo("INELIGIBLE");
      assertThat(row.ruleHits()).contains("product_catalog_version_unavailable");
    });
  }

  @Test
  void noComparableProductsReturnsEmptyStateWithFiltersRetained() {
    var response = service.createRun(validCommand("idem-005", List.of(), true));

    assertThat(response.rows()).isEmpty();
    assertThat(response.resultSummary().candidateCount()).isZero();
    assertThat(response.validationMessages()).contains("no comparable product candidates were supplied by product catalog policy");
  }

  @Test
  void missingActorFailsValidation() {
    assertThatThrownBy(() -> service.createRun(new ProductComparisonCommand(
        "tenant-001",
        "quote-123",
        3,
        List.of("conv-30"),
        List.of("investor-a"),
        true,
        "conv-30",
        Instant.parse("2026-06-01T12:00:00Z"),
        "idem-001",
        " ",
        "corr-001",
        "cause-001")))
        .isInstanceOf(ValidationException.class)
        .hasMessage("actorId is required");
  }

  @Test
  void missingPricingAsOfFailsValidation() {
    assertThatThrownBy(() -> service.createRun(new ProductComparisonCommand(
        "tenant-001",
        "quote-123",
        3,
        List.of("conv-30"),
        List.of("investor-a"),
        true,
        "conv-30",
        null,
        "idem-missing-pricing-as-of",
        "actor-001",
        "corr-001",
        "cause-001")))
        .isInstanceOf(ValidationException.class)
        .hasMessage("pricingAsOf is required");
  }

  @Test
  void replaysSameCreateResponseForDuplicateIdempotencyKey() {
    var first = service.createRun(validCommand("idem-006", List.of("conv-30"), true));
    var replay = service.createRun(validCommand("idem-006", List.of("conv-30"), true));

    assertThat(replay).isEqualTo(first);
    assertThat(repository.size()).isEqualTo(1);
  }

  @Test
  void duplicateIdempotencyKeyWithDifferentRequestIsConflict() {
    service.createRun(validCommand("idem-007", List.of("conv-30"), true));

    assertThatThrownBy(() -> service.createRun(validCommand("idem-007", List.of("conv-15"), true)))
        .isInstanceOf(IdempotencyConflictException.class)
        .hasMessage("idempotency key was already used with a different product comparison request");
  }

  @Test
  void promotionFailsClosedUntilEligibleProductRowExists() {
    var comparison = service.createRun(validCommand("idem-008", List.of("conv-30"), true));

    assertThatThrownBy(() -> service.promoteVariant(new ProductPromotionCommand(
        "tenant-001",
        comparison.analysisId(),
        comparison.rows().get(0).variantId(),
        "promote-001",
        "actor-001",
        "corr-001",
        "cause-001")))
        .isInstanceOf(PolicyNotSatisfiedException.class)
        .hasMessage("only eligible product comparison rows can be promoted to a draft variant");
  }

  private static ProductComparisonCommand validCommand(
      String idempotencyKey,
      List<String> candidateProductIds,
      boolean includeIneligible) {
    return new ProductComparisonCommand(
        "tenant-001",
        "quote-123",
        3,
        candidateProductIds,
        List.of("investor-a"),
        includeIneligible,
        "conv-30",
        Instant.parse("2026-06-01T12:00:00Z"),
        idempotencyKey,
        "actor-001",
        "corr-001",
        "cause-001");
  }
}
