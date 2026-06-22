package com.wcpe.scenarioanalysis;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class LtvSensitivityService {
  static final String AXIS_TYPE = "LTV_DOWN_PAYMENT";
  private static final List<BigDecimal> THRESHOLDS = List.of(
      new BigDecimal("0.80"),
      new BigDecimal("0.85"),
      new BigDecimal("0.90"),
      new BigDecimal("0.95"),
      new BigDecimal("0.97"));

  private final LtvSensitivityRepository repository;
  private final Clock clock;

  public LtvSensitivityService() {
    throw FailClosedPersistence.notConfigured("LTV sensitivity store");
  }

  LtvSensitivityService(LtvSensitivityRepository repository, Clock clock) {
    this.repository = Objects.requireNonNull(repository, "repository is required");
    this.clock = Objects.requireNonNull(clock, "clock is required");
  }

  public LtvSensitivityResponse createRun(LtvSensitivityCommand command) {
    LtvSensitivityCommand validCommand = validate(command);
    String idempotencyKeyHash = sha256Hex(validCommand.idempotencyKey());
    String requestHash = sha256Hex(canonicalRequest(validCommand));

    Optional<StoredLtvSensitivityRun> existing = repository.findByIdempotencyKeyHash(
        validCommand.tenantId(), idempotencyKeyHash);
    if (existing.isPresent()) {
      StoredLtvSensitivityRun run = existing.get();
      if (!run.requestHash().equals(requestHash)) {
        throw new IdempotencyConflictException("idempotency key was already used with a different LTV sensitivity request");
      }
      return run.response();
    }

    Instant now = Instant.now(clock);
    UUID analysisId = UUID.randomUUID();
    BigDecimal valueBasis = propertyValueBasis(validCommand);
    BigDecimal baselineLtv = ratio(validCommand.currentLoanAmount(), valueBasis);
    List<BigDecimal> values = collapseValues(validCommand.values());
    List<LtvSensitivityRow> rows = values.stream()
        .map(value -> rowFor(validCommand, analysisId, valueBasis, baselineLtv, value))
        .sorted(Comparator.comparing(LtvSensitivityRow::targetLtv))
        .toList();
    String resultHash = "sha256:" + sha256Hex(canonicalResult(validCommand, rows));
    LtvSensitivityResponse response = new LtvSensitivityResponse(
        analysisId,
        "COMPLETED",
        AXIS_TYPE,
        validCommand.sourceQuoteId(),
        validCommand.sourceQuoteVersion(),
        validCommand.mode(),
        baselineLtv,
        rows,
        summarize(validCommand, valueBasis, rows),
        duplicateWarnings(validCommand.values(), values),
        resultHash,
        validCommand.correlationId());
    List<LtvSensitivityEvent> events = List.of(
        event(validCommand, analysisId, resultHash, now));

    repository.save(new StoredLtvSensitivityRun(
        validCommand.tenantId(),
        analysisId,
        requestHash,
        idempotencyKeyHash,
        response,
        events,
        now));
    return response;
  }

  public LtvSensitivityResponse getRun(String tenantId, UUID analysisId) {
    String normalizedTenantId = requireText(tenantId, "tenantId is required");
    if (analysisId == null) {
      throw new ValidationException("analysisId is required");
    }
    return repository.findByAnalysisId(normalizedTenantId, analysisId)
        .map(StoredLtvSensitivityRun::response)
        .orElseThrow(() -> new NotFoundException("LTV sensitivity run was not found"));
  }

  private LtvSensitivityCommand validate(LtvSensitivityCommand command) {
    if (command == null) {
      throw new ValidationException("LTV sensitivity request is required");
    }
    String tenantId = requireText(command.tenantId(), "tenantId is required");
    String sourceQuoteId = requireText(command.sourceQuoteId(), "sourceQuoteId is required");
    Integer sourceQuoteVersion = command.sourceQuoteVersion();
    if (sourceQuoteVersion == null || sourceQuoteVersion < 1) {
      throw new ValidationException("sourceQuoteVersion must be positive");
    }
    LtvSensitivityMode mode = Objects.requireNonNull(command.mode(), "mode is required");
    requirePositive(command.propertyValue(), "propertyValue must be positive");
    if (command.purchasePrice() != null) {
      requirePositive(command.purchasePrice(), "purchasePrice must be positive when supplied");
    }
    requirePositive(command.currentLoanAmount(), "currentLoanAmount must be positive");
    BigDecimal subordinateLienAmount = defaultMoney(command.subordinateLienAmount());
    if (subordinateLienAmount.signum() < 0) {
      throw new ValidationException("subordinateLienAmount cannot be negative");
    }
    List<BigDecimal> values = command.values();
    if (values == null || values.isEmpty()) {
      throw new PolicyNotSatisfiedException("tenant LTV sensitivity value configuration is required when values are not supplied");
    }
    values.forEach(value -> validateModeValue(mode, value));
    String idempotencyKey = requireText(command.idempotencyKey(), "Idempotency-Key is required");
    String actorId = requireText(command.actorId(), "actorId is required");
    String correlationId = defaultText(command.correlationId(), UUID.randomUUID().toString());
    String causationId = defaultText(command.causationId(), correlationId);
    Instant pricingAsOf = Objects.requireNonNull(command.pricingAsOf(), "pricingAsOf is required");
    return new LtvSensitivityCommand(
        tenantId,
        sourceQuoteId,
        sourceQuoteVersion,
        mode,
        values,
        scaleMoney(command.propertyValue()),
        command.purchasePrice() == null ? null : scaleMoney(command.purchasePrice()),
        scaleMoney(command.currentLoanAmount()),
        scaleMoney(subordinateLienAmount),
        command.includeMiEstimate(),
        pricingAsOf,
        idempotencyKey,
        actorId,
        correlationId,
        causationId);
  }

  private static void validateModeValue(LtvSensitivityMode mode, BigDecimal value) {
    if (value == null) {
      throw new ValidationException("sensitivity values cannot contain nulls");
    }
    switch (mode) {
      case DOWN_PAYMENT_AMOUNT -> {
        if (value.signum() < 0) {
          throw new ValidationException("down payment amount cannot be negative");
        }
      }
      case DOWN_PAYMENT_PERCENT, TARGET_LTV -> {
        if (value.signum() <= 0 || value.compareTo(new BigDecimal("100")) > 0) {
          throw new ValidationException(mode.name() + " values must be between 0 and 100");
        }
      }
      case LOAN_AMOUNT -> requirePositive(value, "loan amount values must be positive");
    }
  }

  static BigDecimal propertyValueBasis(LtvSensitivityCommand command) {
    if (command.purchasePrice() == null) {
      return command.propertyValue();
    }
    return command.purchasePrice().min(command.propertyValue());
  }

  static List<BigDecimal> collapseValues(List<BigDecimal> values) {
    LinkedHashSet<BigDecimal> collapsed = new LinkedHashSet<>();
    for (BigDecimal value : values) {
      collapsed.add(value.stripTrailingZeros());
    }
    return List.copyOf(collapsed);
  }

  private static LtvSensitivityRow rowFor(
      LtvSensitivityCommand command,
      UUID analysisId,
      BigDecimal valueBasis,
      BigDecimal baselineLtv,
      BigDecimal inputValue) {
    BigDecimal loanAmount = loanAmountFor(command, valueBasis, inputValue);
    BigDecimal downPaymentAmount = valueBasis.subtract(loanAmount).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    BigDecimal ltv = ratio(loanAmount, valueBasis);
    BigDecimal cltv = ratio(loanAmount.add(command.subordinateLienAmount()), valueBasis);
    List<ThresholdCrossing> crossedThresholds = crossedThresholds(baselineLtv, ltv);
    List<String> ruleHits = new ArrayList<>();
    ruleHits.add("pricing_client_ltv_sensitivity_unavailable");
    MiEstimate mi = command.includeMiEstimate()
        ? new MiEstimate("UNAVAILABLE", null, null, "MI_UNAVAILABLE")
        : new MiEstimate("NOT_REQUESTED", null, null, null);
    if ("UNAVAILABLE".equals(mi.status())) {
      ruleHits.add("mi_dependency_unavailable");
    }
    String resultHash = "sha256:" + sha256Hex(command.tenantId() + '|' + analysisId + '|' + inputValue + '|' + loanAmount + '|' + ltv);
    return new LtvSensitivityRow(
        UUID.randomUUID(),
        inputValue.stripTrailingZeros(),
        ltv,
        loanAmount,
        downPaymentAmount,
        cltv,
        cltv,
        "NOT_PRICED",
        mi,
        new PriceEstimate("UNAVAILABLE", null, null, null),
        new LtvSensitivityDeltas(ltv.subtract(baselineLtv).setScale(5, RoundingMode.HALF_UP), loanAmount.subtract(command.currentLoanAmount()).setScale(2, RoundingMode.HALF_UP), null, null),
        crossedThresholds,
        ruleHits,
        resultHash);
  }

  private static BigDecimal loanAmountFor(LtvSensitivityCommand command, BigDecimal valueBasis, BigDecimal inputValue) {
    return switch (command.mode()) {
      case DOWN_PAYMENT_AMOUNT -> valueBasis.subtract(inputValue).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
      case DOWN_PAYMENT_PERCENT -> valueBasis.multiply(BigDecimal.ONE.subtract(percent(inputValue))).setScale(2, RoundingMode.HALF_UP);
      case TARGET_LTV -> valueBasis.multiply(percent(inputValue)).setScale(2, RoundingMode.HALF_UP);
      case LOAN_AMOUNT -> inputValue.setScale(2, RoundingMode.HALF_UP);
    };
  }

  private static List<ThresholdCrossing> crossedThresholds(BigDecimal oldLtv, BigDecimal newLtv) {
    return THRESHOLDS.stream()
        .filter(threshold -> oldLtv.compareTo(threshold) < 0 && newLtv.compareTo(threshold) >= 0
            || oldLtv.compareTo(threshold) > 0 && newLtv.compareTo(threshold) < 0)
        .map(threshold -> new ThresholdCrossing(label(oldLtv), label(newLtv), label(threshold), "LTV_THRESHOLD_CROSSED"))
        .toList();
  }

  private static LtvSensitivitySummary summarize(
      LtvSensitivityCommand command,
      BigDecimal valueBasis,
      List<LtvSensitivityRow> rows) {
    int crossingCount = rows.stream().mapToInt(row -> row.crossedThresholds().size()).sum();
    long miUnavailableCount = rows.stream().filter(row -> "UNAVAILABLE".equals(row.mi().status())).count();
    return new LtvSensitivitySummary(
        rows.size(),
        0,
        crossingCount,
        miUnavailableCount,
        valueBasis,
        command.includeMiEstimate()
            ? "MI estimates are marked unavailable until the MI dependency supplies tenant product rate-card versions. Pricing output is non-binding."
            : "Pricing deltas are unavailable until the pricing client supports LTV/down payment sensitivity rows. Output is non-binding.");
  }

  private static List<String> duplicateWarnings(List<BigDecimal> originalValues, List<BigDecimal> collapsedValues) {
    if (originalValues.size() == collapsedValues.size()) {
      return List.of();
    }
    return List.of("duplicate LTV sensitivity values were collapsed");
  }

  private static LtvSensitivityEvent event(
      LtvSensitivityCommand command,
      UUID analysisId,
      String resultHash,
      Instant occurredAt) {
    return new LtvSensitivityEvent(
        UUID.randomUUID(),
        "whatif.ltv_sensitivity.completed.v1",
        command.tenantId(),
        analysisId,
        command.actorId(),
        command.correlationId(),
        command.causationId(),
        command.idempotencyKey(),
        resultHash,
        occurredAt);
  }

  private static BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
    requirePositive(denominator, "property value basis must be positive");
    return numerator.divide(denominator, 5, RoundingMode.HALF_UP);
  }

  private static BigDecimal percent(BigDecimal value) {
    return value.divide(new BigDecimal("100"), 8, RoundingMode.HALF_UP);
  }

  private static String label(BigDecimal ltv) {
    BigDecimal percent = ltv.compareTo(BigDecimal.ONE) > 0 ? ltv : ltv.multiply(new BigDecimal("100"));
    return percent.setScale(0, RoundingMode.HALF_UP).toPlainString() + "%";
  }

  private static String canonicalRequest(LtvSensitivityCommand command) {
    return command.tenantId() + '|'
        + command.sourceQuoteId() + '|'
        + command.sourceQuoteVersion() + '|'
        + command.mode() + '|'
        + collapseValues(command.values()) + '|'
        + command.propertyValue() + '|'
        + nullToEmpty(command.purchasePrice()) + '|'
        + command.currentLoanAmount() + '|'
        + command.subordinateLienAmount() + '|'
        + command.includeMiEstimate() + '|'
        + command.pricingAsOf() + '|'
        + command.actorId();
  }

  private static String canonicalResult(LtvSensitivityCommand command, List<LtvSensitivityRow> rows) {
    StringBuilder builder = new StringBuilder(canonicalRequest(command));
    for (LtvSensitivityRow row : rows) {
      builder.append('|').append(row.targetLtv()).append(':').append(row.loanAmount()).append(':').append(row.eligibility());
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

  private static void requirePositive(BigDecimal value, String message) {
    if (value == null || value.signum() <= 0) {
      throw new ValidationException(message);
    }
  }

  private static BigDecimal scaleMoney(BigDecimal value) {
    return value.setScale(2, RoundingMode.HALF_UP);
  }

  private static BigDecimal defaultMoney(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
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

  private static String nullToEmpty(Object value) {
    return value == null ? "" : value.toString();
  }

  public enum LtvSensitivityMode {
    DOWN_PAYMENT_AMOUNT,
    DOWN_PAYMENT_PERCENT,
    TARGET_LTV,
    LOAN_AMOUNT
  }

  public record LtvSensitivityCommand(
      String tenantId,
      String sourceQuoteId,
      Integer sourceQuoteVersion,
      LtvSensitivityMode mode,
      List<BigDecimal> values,
      BigDecimal propertyValue,
      BigDecimal purchasePrice,
      BigDecimal currentLoanAmount,
      BigDecimal subordinateLienAmount,
      boolean includeMiEstimate,
      Instant pricingAsOf,
      String idempotencyKey,
      String actorId,
      String correlationId,
      String causationId) {}

  public record LtvSensitivityResponse(
      UUID analysisId,
      String status,
      String sensitivityAxis,
      String sourceQuoteId,
      int sourceQuoteVersion,
      LtvSensitivityMode mode,
      BigDecimal baselineLtv,
      List<LtvSensitivityRow> rows,
      LtvSensitivitySummary resultSummary,
      List<String> validationMessages,
      String resultHash,
      String correlationId) {}

  public record LtvSensitivityRow(
      UUID variantId,
      BigDecimal axisValue,
      BigDecimal targetLtv,
      BigDecimal loanAmount,
      BigDecimal downPaymentAmount,
      BigDecimal cltv,
      BigDecimal hcltv,
      String eligibility,
      MiEstimate mi,
      PriceEstimate price,
      LtvSensitivityDeltas deltas,
      List<ThresholdCrossing> crossedThresholds,
      List<String> ruleHits,
      String resultHash) {}

  public record MiEstimate(String status, Integer monthlyCents, Integer upfrontCents, String warningCode) {}

  public record PriceEstimate(String status, Integer priceBps, Integer rateBps, Integer paymentCents) {}

  public record LtvSensitivityDeltas(
      BigDecimal ltvDelta,
      BigDecimal loanAmountDelta,
      Integer paymentDeltaCents,
      Integer priceDeltaBps) {}

  public record ThresholdCrossing(
      String oldThresholdLabel,
      String newThresholdLabel,
      String crossedThreshold,
      String reasonCode) {}

  public record LtvSensitivitySummary(
      int completedCount,
      int failedCount,
      int thresholdCrossingCount,
      long miUnavailableCount,
      BigDecimal propertyValueUsed,
      String disclaimer) {}

  public record LtvSensitivityEvent(
      UUID eventId,
      String eventType,
      String tenantId,
      UUID analysisId,
      String actorId,
      String correlationId,
      String causationId,
      String idempotencyKey,
      String resultHash,
      Instant occurredAt) {}

  public record StoredLtvSensitivityRun(
      String tenantId,
      UUID analysisId,
      String requestHash,
      String idempotencyKeyHash,
      LtvSensitivityResponse response,
      List<LtvSensitivityEvent> events,
      Instant createdAt) {}

  interface LtvSensitivityRepository {
    Optional<StoredLtvSensitivityRun> findByIdempotencyKeyHash(String tenantId, String idempotencyKeyHash);

    Optional<StoredLtvSensitivityRun> findByAnalysisId(String tenantId, UUID analysisId);

    void save(StoredLtvSensitivityRun run);
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
