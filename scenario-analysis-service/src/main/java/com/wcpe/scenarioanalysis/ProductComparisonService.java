package com.wcpe.scenarioanalysis;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class ProductComparisonService {
  static final String AXIS_TYPE = "PRODUCT";

  private final ProductComparisonRepository repository;
  private final Clock clock;

  public ProductComparisonService() {
    throw FailClosedPersistence.notConfigured("product comparison store");
  }

  ProductComparisonService(ProductComparisonRepository repository, Clock clock) {
    this.repository = Objects.requireNonNull(repository, "repository is required");
    this.clock = Objects.requireNonNull(clock, "clock is required");
  }

  public ComparableProductsConfigResponse getComparableProductsConfig(
      String tenantId,
      String sourceQuoteId,
      String channel,
      String productFamily,
      String investorId) {
    return new ComparableProductsConfigResponse(
        requireText(tenantId, "tenantId is required"),
        defaultText(sourceQuoteId, ""),
        defaultText(channel, ""),
        defaultText(productFamily, ""),
        defaultText(investorId, ""),
        List.of(),
        "PRODUCT_CATALOG_CONFIG_UNAVAILABLE",
        "Comparable product candidates must come from tenant-scoped product catalog, eligibility, and channel policy. No candidate product defaults are assumed.");
  }

  public ProductComparisonResponse createRun(ProductComparisonCommand command) {
    ProductComparisonCommand validCommand = validate(command);
    String idempotencyKeyHash = sha256Hex(validCommand.idempotencyKey());
    String requestHash = sha256Hex(canonicalRequest(validCommand));

    Optional<StoredProductComparisonRun> existing = repository.findByIdempotencyKeyHash(
        validCommand.tenantId(), idempotencyKeyHash);
    if (existing.isPresent()) {
      StoredProductComparisonRun run = existing.get();
      if (!run.requestHash().equals(requestHash)) {
        throw new IdempotencyConflictException("idempotency key was already used with a different product comparison request");
      }
      return run.response();
    }

    Instant now = Instant.now(clock);
    UUID analysisId = UUID.randomUUID();
    List<String> productIds = collapseText(validCommand.candidateProductIds());
    List<ProductComparisonRow> rows = productIds.stream()
        .map(productId -> rowFor(validCommand, analysisId, productId))
        .filter(row -> validCommand.includeIneligible() || "ELIGIBLE".equals(row.eligibility()))
        .toList();
    String resultHash = "sha256:" + sha256Hex(canonicalResult(validCommand, rows));
    ProductComparisonResponse response = new ProductComparisonResponse(
        analysisId,
        "COMPLETED_WITH_DEPENDENCY_GAPS",
        AXIS_TYPE,
        validCommand.sourceQuoteId(),
        validCommand.sourceQuoteVersion(),
        validCommand.baselineProductId(),
        rows,
        summarize(validCommand, rows, productIds.size()),
        validationMessages(productIds, rows),
        resultHash,
        validCommand.correlationId());
    List<ProductComparisonEvent> events = List.of(
        event("whatif.product_comparison.completed.v1", validCommand, analysisId, null, resultHash, now));

    repository.save(new StoredProductComparisonRun(
        validCommand.tenantId(),
        analysisId,
        requestHash,
        idempotencyKeyHash,
        response,
        events,
        now));
    return response;
  }

  public ProductComparisonResponse getRun(String tenantId, UUID analysisId) {
    String normalizedTenantId = requireText(tenantId, "tenantId is required");
    if (analysisId == null) {
      throw new ValidationException("analysisId is required");
    }
    return repository.findByAnalysisId(normalizedTenantId, analysisId)
        .map(StoredProductComparisonRun::response)
        .orElseThrow(() -> new NotFoundException("product comparison run was not found"));
  }

  public ProductPromotionResponse promoteVariant(ProductPromotionCommand command) {
    ProductPromotionCommand validCommand = validate(command);
    StoredProductComparisonRun run = repository.findByAnalysisId(validCommand.tenantId(), validCommand.analysisId())
        .orElseThrow(() -> new NotFoundException("product comparison run was not found"));
    ProductComparisonRow row = run.response().rows().stream()
        .filter(candidate -> candidate.variantId().equals(validCommand.variantId()))
        .findFirst()
        .orElseThrow(() -> new NotFoundException("product comparison row was not found"));
    if (!"ELIGIBLE".equals(row.eligibility())) {
      throw new PolicyNotSatisfiedException("only eligible product comparison rows can be promoted to a draft variant");
    }
    Instant now = Instant.now(clock);
    ProductComparisonEvent event = event(
        "whatif.variant.promoted_from_product_comparison.v1",
        new ProductComparisonCommand(
            validCommand.tenantId(),
            run.response().sourceQuoteId(),
            run.response().sourceQuoteVersion(),
            List.of(row.productId()),
            List.of(row.investorId()),
            false,
            row.productId(),
            now,
            validCommand.idempotencyKey(),
            validCommand.actorId(),
            validCommand.correlationId(),
            validCommand.causationId()),
        validCommand.analysisId(),
        validCommand.variantId(),
        row.resultHash(),
        now);
    repository.appendEvent(validCommand.tenantId(), validCommand.analysisId(), event);
    return new ProductPromotionResponse(
        validCommand.analysisId(),
        validCommand.variantId(),
        "PROMOTED_TO_DRAFT_VARIANT",
        event.eventId(),
        validCommand.correlationId());
  }

  private ProductComparisonCommand validate(ProductComparisonCommand command) {
    if (command == null) {
      throw new ValidationException("product comparison request is required");
    }
    String tenantId = requireText(command.tenantId(), "tenantId is required");
    String sourceQuoteId = requireText(command.sourceQuoteId(), "sourceQuoteId is required");
    Integer sourceQuoteVersion = command.sourceQuoteVersion();
    if (sourceQuoteVersion == null || sourceQuoteVersion < 1) {
      throw new ValidationException("sourceQuoteVersion must be positive");
    }
    List<String> candidateProductIds = command.candidateProductIds() == null ? List.of() : command.candidateProductIds();
    List<String> investorIds = command.investorIds() == null ? List.of() : command.investorIds();
    Instant pricingAsOf = command.pricingAsOf();
    if (pricingAsOf == null) {
      throw new ValidationException("pricingAsOf is required");
    }
    String idempotencyKey = requireText(command.idempotencyKey(), "Idempotency-Key is required");
    String actorId = requireText(command.actorId(), "actorId is required");
    String correlationId = defaultText(command.correlationId(), UUID.randomUUID().toString());
    String causationId = defaultText(command.causationId(), correlationId);
    return new ProductComparisonCommand(
        tenantId,
        sourceQuoteId,
        sourceQuoteVersion,
        candidateProductIds,
        investorIds,
        command.includeIneligible(),
        defaultText(command.baselineProductId(), ""),
        pricingAsOf,
        idempotencyKey,
        actorId,
        correlationId,
        causationId);
  }

  private ProductPromotionCommand validate(ProductPromotionCommand command) {
    if (command == null) {
      throw new ValidationException("product comparison promotion request is required");
    }
    String tenantId = requireText(command.tenantId(), "tenantId is required");
    UUID analysisId = Objects.requireNonNull(command.analysisId(), "analysisId is required");
    UUID variantId = Objects.requireNonNull(command.variantId(), "variantId is required");
    String idempotencyKey = requireText(command.idempotencyKey(), "Idempotency-Key is required");
    String actorId = requireText(command.actorId(), "actorId is required");
    String correlationId = defaultText(command.correlationId(), UUID.randomUUID().toString());
    String causationId = defaultText(command.causationId(), correlationId);
    return new ProductPromotionCommand(tenantId, analysisId, variantId, idempotencyKey, actorId, correlationId, causationId);
  }

  private static ProductComparisonRow rowFor(ProductComparisonCommand command, UUID analysisId, String productId) {
    List<String> ruleHits = new ArrayList<>();
    ruleHits.add("product_catalog_version_unavailable");
    ruleHits.add("eligibility_dependency_unavailable");
    ruleHits.add("pricing_dependency_unavailable");
    ruleHits.add("payment_dependency_unavailable");
    ruleHits.add("apr_dependency_unavailable");
    String investorId = command.investorIds().isEmpty() ? "UNSPECIFIED" : command.investorIds().get(0);
    String resultHash = "sha256:" + sha256Hex(command.tenantId() + '|' + analysisId + '|' + productId + '|' + investorId);
    return new ProductComparisonRow(
        UUID.randomUUID(),
        productId,
        null,
        investorId,
        "UNKNOWN",
        null,
        null,
        "INELIGIBLE",
        new PricingSummary("UNAVAILABLE", null, null, null),
        new PaymentSummary("UNAVAILABLE", null, null),
        new AprSummary("UNAVAILABLE", null, "APR_ENGINE_UNAVAILABLE"),
        new ProductDeltas(null, null, null, null),
        ruleHits,
        resultHash);
  }

  private static ProductComparisonSummary summarize(
      ProductComparisonCommand command,
      List<ProductComparisonRow> rows,
      int candidateCount) {
    long ineligibleCount = rows.stream().filter(row -> "INELIGIBLE".equals(row.eligibility())).count();
    return new ProductComparisonSummary(
        candidateCount,
        rows.size(),
        0,
        ineligibleCount,
        command.baselineProductId().isBlank() ? null : command.baselineProductId(),
        "Product catalog, eligibility, pricing, payment, and APR outputs require versioned tenant-governed dependencies; this service stores references and result snapshots only.");
  }

  private static List<String> validationMessages(List<String> productIds, List<ProductComparisonRow> rows) {
    List<String> messages = new ArrayList<>();
    if (productIds.isEmpty()) {
      messages.add("no comparable product candidates were supplied by product catalog policy");
    }
    if (!productIds.isEmpty() && rows.isEmpty()) {
      messages.add("ineligible candidate products were suppressed because includeIneligible is false");
    }
    if (!productIds.isEmpty()) {
      messages.add("candidate products require product catalog, eligibility, pricing, payment, and APR dependencies before eligible economics can be returned");
    }
    return List.copyOf(messages);
  }

  private static List<String> collapseText(List<String> values) {
    if (values == null || values.isEmpty()) {
      return List.of();
    }
    LinkedHashSet<String> collapsed = new LinkedHashSet<>();
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        collapsed.add(value.trim());
      }
    }
    return List.copyOf(collapsed);
  }

  private static ProductComparisonEvent event(
      String eventType,
      ProductComparisonCommand command,
      UUID analysisId,
      UUID variantId,
      String resultHash,
      Instant occurredAt) {
    return new ProductComparisonEvent(
        UUID.randomUUID(),
        eventType,
        command.tenantId(),
        analysisId,
        variantId,
        command.actorId(),
        command.correlationId(),
        command.causationId(),
        command.idempotencyKey(),
        resultHash,
        occurredAt);
  }

  private static String canonicalRequest(ProductComparisonCommand command) {
    return command.tenantId() + '|'
        + command.sourceQuoteId() + '|'
        + command.sourceQuoteVersion() + '|'
        + collapseText(command.candidateProductIds()) + '|'
        + collapseText(command.investorIds()) + '|'
        + command.includeIneligible() + '|'
        + command.baselineProductId() + '|'
        + command.pricingAsOf() + '|'
        + command.actorId();
  }

  private static String canonicalResult(ProductComparisonCommand command, List<ProductComparisonRow> rows) {
    StringBuilder builder = new StringBuilder(canonicalRequest(command));
    for (ProductComparisonRow row : rows) {
      builder.append('|').append(row.productId()).append(':').append(row.investorId()).append(':').append(row.eligibility());
    }
    return builder.toString();
  }

  private static String sha256Hex(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(bytes.length * 2);
      for (byte b : bytes) {
        hex.append(String.format("%02x", b));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is not available", ex);
    }
  }

  private static String requireText(String value, String message) {
    if (value == null || value.isBlank()) {
      throw new ValidationException(message);
    }
    return value.trim();
  }

  private static String defaultText(String value, String defaultValue) {
    return value == null || value.isBlank() ? defaultValue : value.trim();
  }

  public record ProductComparisonCommand(
      String tenantId,
      String sourceQuoteId,
      Integer sourceQuoteVersion,
      List<String> candidateProductIds,
      List<String> investorIds,
      boolean includeIneligible,
      String baselineProductId,
      Instant pricingAsOf,
      String idempotencyKey,
      String actorId,
      String correlationId,
      String causationId) {}

  public record ProductPromotionCommand(
      String tenantId,
      UUID analysisId,
      UUID variantId,
      String idempotencyKey,
      String actorId,
      String correlationId,
      String causationId) {}

  public record ComparableProductsConfigResponse(
      String tenantId,
      String sourceQuoteId,
      String channel,
      String productFamily,
      String investorId,
      List<ComparableProductRef> candidates,
      String dependencyStatus,
      String message) {}

  public record ComparableProductRef(String productId, String productVersion, String investorId, String productFamily) {}

  public record ProductComparisonResponse(
      UUID analysisId,
      String status,
      String sensitivityAxis,
      String sourceQuoteId,
      int sourceQuoteVersion,
      String baselineProductId,
      List<ProductComparisonRow> rows,
      ProductComparisonSummary resultSummary,
      List<String> validationMessages,
      String resultHash,
      String correlationId) {}

  public record ProductComparisonRow(
      UUID variantId,
      String productId,
      String productVersion,
      String investorId,
      String productFamily,
      Integer termMonths,
      String amortizationType,
      String eligibility,
      PricingSummary pricingSummary,
      PaymentSummary paymentSummary,
      AprSummary aprSummary,
      ProductDeltas deltas,
      List<String> ruleHits,
      String resultHash) {}

  public record PricingSummary(String status, BigDecimal rate, BigDecimal pricePoints, Integer lockPeriodDays) {}

  public record PaymentSummary(String status, Integer principalAndInterestCents, Integer cashToCloseCents) {}

  public record AprSummary(String status, BigDecimal apr, String warningCode) {}

  public record ProductDeltas(BigDecimal rateDelta, BigDecimal priceDelta, Integer paymentDeltaCents, Integer cashToCloseDeltaCents) {}

  public record ProductComparisonSummary(
      int candidateCount,
      int rowCount,
      int eligibleCount,
      long ineligibleCount,
      String baselineProductId,
      String disclaimer) {}

  public record ProductPromotionResponse(
      UUID analysisId,
      UUID variantId,
      String status,
      UUID eventId,
      String correlationId) {}

  public record ProductComparisonEvent(
      UUID eventId,
      String eventType,
      String tenantId,
      UUID analysisId,
      UUID variantId,
      String actorId,
      String correlationId,
      String causationId,
      String idempotencyKey,
      String resultHash,
      Instant occurredAt) {}

  public record StoredProductComparisonRun(
      String tenantId,
      UUID analysisId,
      String requestHash,
      String idempotencyKeyHash,
      ProductComparisonResponse response,
      List<ProductComparisonEvent> events,
      Instant createdAt) {}

  interface ProductComparisonRepository {
    Optional<StoredProductComparisonRun> findByIdempotencyKeyHash(String tenantId, String idempotencyKeyHash);

    Optional<StoredProductComparisonRun> findByAnalysisId(String tenantId, UUID analysisId);

    void save(StoredProductComparisonRun run);

    void appendEvent(String tenantId, UUID analysisId, ProductComparisonEvent event);
  }

  public static class ValidationException extends RuntimeException {
    public ValidationException(String message) {
      super(message);
    }
  }

  public static class PolicyNotSatisfiedException extends RuntimeException {
    public PolicyNotSatisfiedException(String message) {
      super(message);
    }
  }

  public static class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException(String message) {
      super(message);
    }
  }

  public static class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
      super(message);
    }
  }
}
