package com.wcpe.scenarioanalysis;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class LockPeriodComparisonService {
  static final String AXIS_TYPE = "LOCK_PERIOD";

  private final LockPeriodComparisonRepository repository;
  private final Clock clock;

  public LockPeriodComparisonService() {
    throw FailClosedPersistence.notConfigured("lock period comparison store");
  }

  LockPeriodComparisonService(LockPeriodComparisonRepository repository, Clock clock) {
    this.repository = Objects.requireNonNull(repository, "repository is required");
    this.clock = Objects.requireNonNull(clock, "clock is required");
  }

  public LockPeriodConfigResponse getConfig(String tenantId, String productId, String investorId, String channel) {
    String normalizedTenantId = requireText(tenantId, "tenantId is required");
    return new LockPeriodConfigResponse(
        normalizedTenantId,
        defaultText(productId, ""),
        defaultText(investorId, ""),
        defaultText(channel, ""),
        List.of(),
        "LOCK_POLICY_CONFIG_UNAVAILABLE",
        null,
        null,
        "Lock period configuration must come from tenant/investor policy; no default lock days are assumed.");
  }

  public LockPeriodComparisonResponse createRun(LockPeriodComparisonCommand command) {
    LockPeriodComparisonCommand validCommand = validate(command);
    String idempotencyKeyHash = sha256Hex(validCommand.idempotencyKey());
    String requestHash = sha256Hex(canonicalRequest(validCommand));

    Optional<StoredLockPeriodComparisonRun> existing = repository.findByIdempotencyKeyHash(
        validCommand.tenantId(), idempotencyKeyHash);
    if (existing.isPresent()) {
      StoredLockPeriodComparisonRun run = existing.get();
      if (!run.requestHash().equals(requestHash)) {
        throw new IdempotencyConflictException("idempotency key was already used with a different lock period comparison request");
      }
      return run.response();
    }

    Instant now = Instant.now(clock);
    UUID analysisId = UUID.randomUUID();
    List<Integer> lockPeriods = collapseLockPeriods(validCommand.lockPeriods());
    List<LockPeriodComparisonRow> rows = lockPeriods.stream()
        .sorted(Comparator.naturalOrder())
        .map(days -> rowFor(validCommand, analysisId, days))
        .toList();
    String resultHash = "sha256:" + sha256Hex(canonicalResult(validCommand, rows));
    LockPeriodComparisonResponse response = new LockPeriodComparisonResponse(
        analysisId,
        "COMPLETED_WITH_DEPENDENCY_GAPS",
        AXIS_TYPE,
        validCommand.sourceQuoteId(),
        validCommand.sourceQuoteVersion(),
        baselineVariantId(validCommand),
        validCommand.lockStartDate(),
        rows,
        summarize(validCommand, rows),
        duplicateWarnings(validCommand.lockPeriods(), lockPeriods),
        resultHash,
        validCommand.correlationId());
    List<LockPeriodComparisonEvent> events = List.of(
        event("whatif.lock_comparison.completed.v1", validCommand, analysisId, resultHash, now));

    repository.save(new StoredLockPeriodComparisonRun(
        validCommand.tenantId(),
        analysisId,
        requestHash,
        idempotencyKeyHash,
        response,
        events,
        now));
    return response;
  }

  public LockPeriodComparisonResponse getRun(String tenantId, UUID analysisId) {
    String normalizedTenantId = requireText(tenantId, "tenantId is required");
    if (analysisId == null) {
      throw new ValidationException("analysisId is required");
    }
    return repository.findByAnalysisId(normalizedTenantId, analysisId)
        .map(StoredLockPeriodComparisonRun::response)
        .orElseThrow(() -> new NotFoundException("lock period comparison run was not found"));
  }

  private LockPeriodComparisonCommand validate(LockPeriodComparisonCommand command) {
    if (command == null) {
      throw new ValidationException("lock period comparison request is required");
    }
    String tenantId = requireText(command.tenantId(), "tenantId is required");
    String sourceQuoteId = requireText(command.sourceQuoteId(), "sourceQuoteId is required");
    Integer sourceQuoteVersion = command.sourceQuoteVersion();
    if (sourceQuoteVersion == null || sourceQuoteVersion < 1) {
      throw new ValidationException("sourceQuoteVersion must be positive");
    }
    List<Integer> lockPeriods = command.lockPeriods();
    if (lockPeriods == null || lockPeriods.isEmpty()) {
      throw new PolicyNotSatisfiedException("tenant lock period configuration is required when lock periods are not supplied");
    }
    lockPeriods.forEach(LockPeriodComparisonService::validateLockPeriod);
    LocalDate lockStartDate = Objects.requireNonNull(command.lockStartDate(), "lockStartDate is required");
    Instant pricingAsOf = Objects.requireNonNull(command.pricingAsOf(), "pricingAsOf is required");
    LocalDate pricingBusinessDate = pricingAsOf.atZone(ZoneOffset.UTC).toLocalDate();
    if (lockStartDate.isBefore(pricingBusinessDate)) {
      throw new PricingVersionStaleException("lockStartDate cannot be before pricing business date");
    }
    String idempotencyKey = requireText(command.idempotencyKey(), "Idempotency-Key is required");
    String actorId = requireText(command.actorId(), "actorId is required");
    String correlationId = defaultText(command.correlationId(), UUID.randomUUID().toString());
    String causationId = defaultText(command.causationId(), correlationId);
    return new LockPeriodComparisonCommand(
        tenantId,
        sourceQuoteId,
        sourceQuoteVersion,
        command.baseVariantId(),
        lockPeriods,
        lockStartDate,
        command.includeExtensionEstimate(),
        pricingAsOf,
        idempotencyKey,
        actorId,
        correlationId,
        causationId);
  }

  static LocalDate calculateExpirationDate(LocalDate lockStartDate, int lockPeriodDays, Set<LocalDate> tenantHolidays) {
    validateLockPeriod(lockPeriodDays);
    LocalDate expirationDate = Objects.requireNonNull(lockStartDate, "lockStartDate is required").plusDays(lockPeriodDays);
    Set<LocalDate> holidays = tenantHolidays == null ? Set.of() : tenantHolidays;
    while (expirationDate.getDayOfWeek().getValue() >= 6 || holidays.contains(expirationDate)) {
      expirationDate = expirationDate.plusDays(1);
    }
    return expirationDate;
  }

  static void validateLockPeriod(Integer lockPeriodDays) {
    if (lockPeriodDays == null || lockPeriodDays <= 0) {
      throw new ValidationException("lockPeriodDays must be positive");
    }
  }

  static List<Integer> collapseLockPeriods(List<Integer> lockPeriods) {
    return List.copyOf(new LinkedHashSet<>(lockPeriods));
  }

  private static LockPeriodComparisonRow rowFor(
      LockPeriodComparisonCommand command,
      UUID analysisId,
      int lockPeriodDays) {
    LocalDate expirationDate = calculateExpirationDate(command.lockStartDate(), lockPeriodDays, Set.of());
    List<String> ruleHits = new ArrayList<>();
    ruleHits.add("lock_policy_config_unavailable");
    ruleHits.add("holiday_calendar_version_unavailable");
    ruleHits.add("pricing_client_lock_adjustment_unavailable");
    if (command.includeExtensionEstimate()) {
      ruleHits.add("extension_fee_dependency_unavailable");
    }
    String resultHash = "sha256:" + sha256Hex(command.tenantId() + '|' + analysisId + '|' + lockPeriodDays + '|' + expirationDate);
    return new LockPeriodComparisonRow(
        UUID.randomUUID(),
        lockPeriodDays,
        expirationDate,
        null,
        new PriceEstimate("UNAVAILABLE", null, null, null),
        command.includeExtensionEstimate()
            ? new ExtensionEstimate("UNAVAILABLE", null, "EXTENSION_POLICY_UNAVAILABLE")
            : new ExtensionEstimate("NOT_REQUESTED", null, null),
        "INELIGIBLE",
        new LockPeriodDeltas(null, null, null, null),
        ruleHits,
        resultHash);
  }

  private static LockPeriodComparisonSummary summarize(
      LockPeriodComparisonCommand command,
      List<LockPeriodComparisonRow> rows) {
    long unavailableCount = rows.stream().filter(row -> row.ruleHits().contains("lock_policy_config_unavailable")).count();
    return new LockPeriodComparisonSummary(
        rows.size(),
        0,
        unavailableCount,
        null,
        null,
        "No real lock is created or committed. Lock policy, holiday calendar, pricing adjustment, and extension fee outputs are unavailable until tenant-governed dependencies provide versioned configuration.");
  }

  private static List<String> duplicateWarnings(List<Integer> originalLockPeriods, List<Integer> collapsedLockPeriods) {
    if (originalLockPeriods.size() == collapsedLockPeriods.size()) {
      return List.of();
    }
    return List.of("duplicate lock periods were collapsed");
  }

  private static String baselineVariantId(LockPeriodComparisonCommand command) {
    return defaultText(command.baseVariantId(), "source-quote:" + command.sourceQuoteId() + ":v" + command.sourceQuoteVersion());
  }

  private static LockPeriodComparisonEvent event(
      String eventType,
      LockPeriodComparisonCommand command,
      UUID analysisId,
      String resultHash,
      Instant occurredAt) {
    return new LockPeriodComparisonEvent(
        UUID.randomUUID(),
        eventType,
        command.tenantId(),
        analysisId,
        command.actorId(),
        command.correlationId(),
        command.causationId(),
        command.idempotencyKey(),
        resultHash,
        occurredAt);
  }

  private static String canonicalRequest(LockPeriodComparisonCommand command) {
    return command.tenantId() + '|'
        + command.sourceQuoteId() + '|'
        + command.sourceQuoteVersion() + '|'
        + nullToEmpty(command.baseVariantId()) + '|'
        + collapseLockPeriods(command.lockPeriods()) + '|'
        + command.lockStartDate() + '|'
        + command.includeExtensionEstimate() + '|'
        + command.pricingAsOf() + '|'
        + command.actorId();
  }

  private static String canonicalResult(LockPeriodComparisonCommand command, List<LockPeriodComparisonRow> rows) {
    StringBuilder builder = new StringBuilder(canonicalRequest(command));
    for (LockPeriodComparisonRow row : rows) {
      builder.append('|').append(row.lockPeriodDays()).append(':').append(row.expirationDate()).append(':').append(row.eligibility());
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

  private static String nullToEmpty(Object value) {
    return value == null ? "" : value.toString();
  }

  public record LockPeriodComparisonCommand(
      String tenantId,
      String sourceQuoteId,
      Integer sourceQuoteVersion,
      String baseVariantId,
      List<Integer> lockPeriods,
      LocalDate lockStartDate,
      boolean includeExtensionEstimate,
      Instant pricingAsOf,
      String idempotencyKey,
      String actorId,
      String correlationId,
      String causationId) {}

  public record LockPeriodConfigResponse(
      String tenantId,
      String productId,
      String investorId,
      String channel,
      List<AllowedLockPeriod> lockPeriods,
      String dependencyStatus,
      String lockPolicyVersion,
      String holidayCalendarVersion,
      String message) {}

  public record AllowedLockPeriod(int lockPeriodDays, String label, boolean available, String unavailableReason) {}

  public record LockPeriodComparisonResponse(
      UUID analysisId,
      String status,
      String sensitivityAxis,
      String sourceQuoteId,
      int sourceQuoteVersion,
      String baselineVariantId,
      LocalDate lockStartDate,
      List<LockPeriodComparisonRow> rows,
      LockPeriodComparisonSummary resultSummary,
      List<String> validationMessages,
      String resultHash,
      String correlationId) {}

  public record LockPeriodComparisonRow(
      UUID variantId,
      int lockPeriodDays,
      LocalDate expirationDate,
      Integer adjustmentBps,
      PriceEstimate price,
      ExtensionEstimate extensionEstimate,
      String eligibility,
      LockPeriodDeltas deltas,
      List<String> ruleHits,
      String resultHash) {}

  public record PriceEstimate(String status, Integer priceBps, Integer rateBps, Integer paymentCents) {}

  public record ExtensionEstimate(String status, Integer feeCents, String warningCode) {}

  public record LockPeriodDeltas(Integer priceDeltaBps, Integer rateDeltaBps, Integer paymentDeltaCents, Integer adjustmentDeltaBps) {}

  public record LockPeriodComparisonSummary(
      int completedCount,
      int failedCount,
      long unavailableDependencyCount,
      String lockPolicyVersion,
      String holidayCalendarVersion,
      String disclaimer) {}

  public record LockPeriodComparisonEvent(
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

  public record StoredLockPeriodComparisonRun(
      String tenantId,
      UUID analysisId,
      String requestHash,
      String idempotencyKeyHash,
      LockPeriodComparisonResponse response,
      List<LockPeriodComparisonEvent> events,
      Instant createdAt) {}

  interface LockPeriodComparisonRepository {
    Optional<StoredLockPeriodComparisonRun> findByIdempotencyKeyHash(String tenantId, String idempotencyKeyHash);

    Optional<StoredLockPeriodComparisonRun> findByAnalysisId(String tenantId, UUID analysisId);

    void save(StoredLockPeriodComparisonRun run);
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

  public static class PricingVersionStaleException extends RuntimeException {
    public PricingVersionStaleException(String message) {
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
