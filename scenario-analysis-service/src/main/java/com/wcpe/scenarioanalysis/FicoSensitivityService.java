package com.wcpe.scenarioanalysis;

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
public class FicoSensitivityService {
  static final String AXIS_TYPE = "FICO";
  static final int MIN_FICO = 300;
  static final int MAX_FICO = 850;

  private final FicoSensitivityRepository repository;
  private final Clock clock;

  public FicoSensitivityService() {
    this(new InMemoryFicoSensitivityRepository(), Clock.systemUTC());
  }

  FicoSensitivityService(FicoSensitivityRepository repository, Clock clock) {
    this.repository = Objects.requireNonNull(repository, "repository is required");
    this.clock = Objects.requireNonNull(clock, "clock is required");
  }

  public FicoSensitivityResponse createRun(FicoSensitivityCommand command) {
    FicoSensitivityCommand validCommand = validate(command);
    String idempotencyKeyHash = sha256Hex(validCommand.idempotencyKey());
    String requestHash = sha256Hex(canonicalRequest(validCommand));

    Optional<StoredFicoSensitivityRun> existing = repository.findByIdempotencyKeyHash(
        validCommand.tenantId(), idempotencyKeyHash);
    if (existing.isPresent()) {
      StoredFicoSensitivityRun run = existing.get();
      if (!run.requestHash().equals(requestHash)) {
        throw new IdempotencyConflictException("idempotency key was already used with a different FICO sensitivity request");
      }
      return run.response();
    }

    Instant now = Instant.now(clock);
    UUID analysisId = UUID.randomUUID();
    UUID runId = UUID.randomUUID();
    List<Integer> scores = collapseScores(validCommand.scores());
    List<String> warnings = duplicateWarnings(validCommand.scores(), scores);
    List<FicoSensitivityRow> rows = scores.stream()
        .sorted(Comparator.naturalOrder())
        .map(score -> rowFor(validCommand, analysisId, score))
        .toList();
    String resultHash = "sha256:" + sha256Hex(canonicalResult(validCommand, rows));
    FicoSensitivitySummary summary = summarize(validCommand.sourceFico(), rows);
    FicoSensitivityResponse response = new FicoSensitivityResponse(
        analysisId,
        runId,
        "COMPLETED",
        baselineVariantId(validCommand),
        AXIS_TYPE,
        validCommand.sourceFico(),
        rows,
        summary,
        warnings,
        resultHash,
        validCommand.correlationId());
    List<FicoSensitivityEvent> events = List.of(
        event("whatif.sensitivity.requested.v1", validCommand, analysisId, runId, resultHash, now),
        event("whatif.sensitivity.completed.v1", validCommand, analysisId, runId, resultHash, now));

    repository.save(new StoredFicoSensitivityRun(
        validCommand.tenantId(),
        analysisId,
        requestHash,
        idempotencyKeyHash,
        response,
        events,
        now));
    return response;
  }

  public FicoSensitivityResponse getRun(String tenantId, UUID analysisId) {
    String normalizedTenantId = requireText(tenantId, "tenantId is required");
    if (analysisId == null) {
      throw new ValidationException("analysisId is required");
    }
    return repository.findByAnalysisId(normalizedTenantId, analysisId)
        .map(StoredFicoSensitivityRun::response)
        .orElseThrow(() -> new NotFoundException("FICO sensitivity run was not found"));
  }

  private FicoSensitivityCommand validate(FicoSensitivityCommand command) {
    if (command == null) {
      throw new ValidationException("FICO sensitivity request is required");
    }
    String tenantId = requireText(command.tenantId(), "tenantId is required");
    String sourceQuoteId = requireText(command.sourceQuoteId(), "sourceQuoteId is required");
    Integer sourceQuoteVersion = command.sourceQuoteVersion();
    if (sourceQuoteVersion == null || sourceQuoteVersion < 1) {
      throw new ValidationException("sourceQuoteVersion must be positive");
    }
    Integer sourceFico = command.sourceFico();
    if (sourceFico == null) {
      throw new SourceFicoRequiredException("source quote representative FICO is required");
    }
    validateFico(sourceFico);
    List<Integer> scores = command.scores();
    if (scores == null || scores.isEmpty()) {
      throw new PolicyNotSatisfiedException("tenant FICO ladder configuration is required when scores are not supplied");
    }
    scores.forEach(FicoSensitivityService::validateFico);
    String idempotencyKey = requireText(command.idempotencyKey(), "Idempotency-Key is required");
    String actorId = requireText(command.actorId(), "actorId is required");
    String correlationId = defaultText(command.correlationId(), UUID.randomUUID().toString());
    String causationId = defaultText(command.causationId(), correlationId);
    Instant pricingAsOf = Objects.requireNonNull(command.pricingAsOf(), "pricingAsOf is required");
    return new FicoSensitivityCommand(
        tenantId,
        sourceQuoteId,
        sourceQuoteVersion,
        sourceFico,
        command.baseVariantId(),
        scores,
        command.includeIneligible(),
        pricingAsOf,
        idempotencyKey,
        actorId,
        correlationId,
        causationId);
  }

  static void validateFico(Integer score) {
    if (score == null || score < MIN_FICO || score > MAX_FICO) {
      throw new ValidationException("FICO score must be between 300 and 850");
    }
  }

  static List<Integer> collapseScores(List<Integer> scores) {
    return List.copyOf(new LinkedHashSet<>(scores));
  }

  static FicoSensitivitySummary summarize(int baselineFico, List<FicoSensitivityRow> rows) {
    Optional<Integer> firstEligibleAboveBaseline = rows.stream()
        .map(FicoSensitivityRow::fico)
        .filter(score -> score > baselineFico)
        .min(Comparator.naturalOrder());
    return new FicoSensitivitySummary(
        rows.size(),
        0,
        firstEligibleAboveBaseline.orElse(null),
        "Pricing deltas are unavailable until the pricing client supports synthetic FICO overrides.");
  }

  private static FicoSensitivityRow rowFor(FicoSensitivityCommand command, UUID analysisId, int score) {
    int delta = score - command.sourceFico();
    String resultHash = "sha256:" + sha256Hex(command.tenantId() + '|' + analysisId + '|' + score + '|' + delta);
    return new FicoSensitivityRow(
        UUID.randomUUID(),
        score,
        bucketLabel(score),
        "NOT_PRICED",
        new FicoSensitivityDeltas(delta, null, null, null, null, null),
        List.of("pricing_client_synthetic_fico_override_unavailable"),
        resultHash);
  }

  private static String bucketLabel(int score) {
    return "FICO_" + score;
  }

  private static List<String> duplicateWarnings(List<Integer> originalScores, List<Integer> collapsedScores) {
    if (originalScores.size() == collapsedScores.size()) {
      return List.of();
    }
    return List.of("duplicate FICO scores were collapsed");
  }

  private static String baselineVariantId(FicoSensitivityCommand command) {
    return defaultText(command.baseVariantId(), "source-quote:" + command.sourceQuoteId() + ":v" + command.sourceQuoteVersion());
  }

  private static FicoSensitivityEvent event(
      String eventType,
      FicoSensitivityCommand command,
      UUID analysisId,
      UUID runId,
      String resultHash,
      Instant occurredAt) {
    return new FicoSensitivityEvent(
        UUID.randomUUID(),
        eventType,
        command.tenantId(),
        analysisId,
        runId,
        command.actorId(),
        command.correlationId(),
        command.causationId(),
        command.idempotencyKey(),
        resultHash,
        occurredAt);
  }

  private static String canonicalRequest(FicoSensitivityCommand command) {
    return command.tenantId() + '|'
        + command.sourceQuoteId() + '|'
        + command.sourceQuoteVersion() + '|'
        + command.sourceFico() + '|'
        + nullToEmpty(command.baseVariantId()) + '|'
        + collapseScores(command.scores()) + '|'
        + command.includeIneligible() + '|'
        + command.pricingAsOf() + '|'
        + command.actorId();
  }

  private static String canonicalResult(FicoSensitivityCommand command, List<FicoSensitivityRow> rows) {
    StringBuilder builder = new StringBuilder(canonicalRequest(command));
    for (FicoSensitivityRow row : rows) {
      builder.append('|').append(row.fico()).append(':').append(row.deltas().ficoDelta()).append(':').append(row.eligibility());
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

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  public record FicoSensitivityCommand(
      String tenantId,
      String sourceQuoteId,
      Integer sourceQuoteVersion,
      Integer sourceFico,
      String baseVariantId,
      List<Integer> scores,
      boolean includeIneligible,
      Instant pricingAsOf,
      String idempotencyKey,
      String actorId,
      String correlationId,
      String causationId) {}

  public record FicoSensitivityResponse(
      UUID analysisId,
      UUID runId,
      String status,
      String baselineVariantId,
      String sensitivityAxis,
      int baselineFico,
      List<FicoSensitivityRow> rows,
      FicoSensitivitySummary resultSummary,
      List<String> validationMessages,
      String resultHash,
      String correlationId) {}

  public record FicoSensitivityRow(
      UUID variantId,
      int fico,
      String ficoBucket,
      String eligibility,
      FicoSensitivityDeltas deltas,
      List<String> ruleHits,
      String resultHash) {}

  public record FicoSensitivityDeltas(
      int ficoDelta,
      Integer priceDeltaBps,
      Integer rateDeltaBps,
      Integer pointsDeltaBps,
      Integer paymentDeltaCents,
      Integer miDeltaCents) {}

  public record FicoSensitivitySummary(
      int completedCount,
      int failedCount,
      Integer firstEligibleScoreAboveBaseline,
      String disclaimer) {}

  public record FicoSensitivityEvent(
      UUID eventId,
      String eventType,
      String tenantId,
      UUID analysisId,
      UUID runId,
      String actorId,
      String correlationId,
      String causationId,
      String idempotencyKey,
      String resultHash,
      Instant occurredAt) {}

  public record StoredFicoSensitivityRun(
      String tenantId,
      UUID analysisId,
      String requestHash,
      String idempotencyKeyHash,
      FicoSensitivityResponse response,
      List<FicoSensitivityEvent> events,
      Instant createdAt) {}

  interface FicoSensitivityRepository {
    Optional<StoredFicoSensitivityRun> findByIdempotencyKeyHash(String tenantId, String idempotencyKeyHash);

    Optional<StoredFicoSensitivityRun> findByAnalysisId(String tenantId, UUID analysisId);

    void save(StoredFicoSensitivityRun run);
  }

  static class InMemoryFicoSensitivityRepository implements FicoSensitivityRepository {
    private final Map<String, StoredFicoSensitivityRun> runsByTenantAndIdempotency = new ConcurrentHashMap<>();
    private final Map<String, StoredFicoSensitivityRun> runsByTenantAndAnalysisId = new ConcurrentHashMap<>();

    @Override
    public Optional<StoredFicoSensitivityRun> findByIdempotencyKeyHash(String tenantId, String idempotencyKeyHash) {
      return Optional.ofNullable(runsByTenantAndIdempotency.get(key(tenantId, idempotencyKeyHash)));
    }

    @Override
    public Optional<StoredFicoSensitivityRun> findByAnalysisId(String tenantId, UUID analysisId) {
      return Optional.ofNullable(runsByTenantAndAnalysisId.get(key(tenantId, analysisId.toString())));
    }

    @Override
    public void save(StoredFicoSensitivityRun run) {
      runsByTenantAndIdempotency.put(key(run.tenantId(), run.idempotencyKeyHash()), run);
      runsByTenantAndAnalysisId.put(key(run.tenantId(), run.analysisId().toString()), run);
    }

    int size() {
      return runsByTenantAndAnalysisId.size();
    }

    private static String key(String tenantId, String id) {
      return tenantId + ':' + id;
    }
  }

  public static class ValidationException extends RuntimeException {
    public ValidationException(String message) {
      super(message);
    }
  }

  public static class SourceFicoRequiredException extends RuntimeException {
    public SourceFicoRequiredException(String message) {
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
