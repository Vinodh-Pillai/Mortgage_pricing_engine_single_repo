package com.wcpe.integration;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class LosQuoteRequestService {
  public static final String BASE_PATH = "/api/v1/tenants/{tenantId}/channels/{channelId}/quotes";
  public static final String REQUEST_PERMISSION = "integrations.quote.request";
  public static final String READ_PERMISSION = "integrations.quote.read";
  public static final String SUPPORTED_SCHEMA_VERSION = "LOS-QUOTE-V1";
  public static final Duration PRICING_TIMEOUT = Duration.ofSeconds(3);
  public static final String ACCEPTED_EVENT_TYPE = "integration.los-quote-request.accepted.v1";
  public static final String PRICED_EVENT_TYPE = "integration.los-quote-request.priced.v1";
  public static final String REJECTED_EVENT_TYPE = "integration.los-quote-request.rejected.v1";
  public static final String AUDIT_ACTION = "LOS_PRICING_REQUEST_COMPLETED";

  private final Clock clock;
  private final PricingClient pricingClient;
  private final Map<String, ChannelConfiguration> channels = new HashMap<>();
  private final Map<String, LosQuoteRequest> requests = new HashMap<>();
  private final Map<String, IdempotencyEntry> idempotencyEntries = new HashMap<>();
  private final List<LosQuoteOutboxEvent> outboxEvents = new ArrayList<>();
  private final List<LosQuoteAuditRecord> auditRecords = new ArrayList<>();

  public LosQuoteRequestService(PricingClient pricingClient) {
    this(Clock.systemUTC(), pricingClient);
  }

  public LosQuoteRequestService(Clock clock, PricingClient pricingClient) {
    this.clock = clock;
    this.pricingClient = pricingClient;
  }

  public void configureChannel(String tenantId, String channelId, boolean active, List<String> allowedProducts, String pricingConfigVersion) {
    channels.put(
        key(tenantId, channelId),
        new ChannelConfiguration(
            tenantId,
            channelId,
            active,
            allowedProducts == null ? List.of() : List.copyOf(allowedProducts),
            pricingConfigVersion));
  }

  public LosQuoteResult submit(SubmitLosQuoteCommand command) {
    LosQuoteError validation = validateCommand(command);
    if (validation != null) {
      return LosQuoteResult.failure(validation);
    }

    ChannelConfiguration channel = channels.get(key(command.tenantId(), command.channelId()));
    if (channel == null || !channel.active()) {
      return LosQuoteResult.failure(error("422", "POLICY_NOT_SATISFIED", "Channel is not active", command.correlationId(), false));
    }
    if (channel.pricingConfigVersion() == null || channel.pricingConfigVersion().isBlank()) {
      return LosQuoteResult.failure(error("422", "POLICY_NOT_SATISFIED", "Pricing configuration version is required", command.correlationId(), false));
    }
    if (!channel.allowedProducts().contains(command.productPreference())) {
      LosQuoteResponse response = rejectBeforePricing(command, "DISABLED_CHANNEL_PRODUCT", channel.pricingConfigVersion());
      return LosQuoteResult.success(response);
    }

    String requestHash = hash(canonicalScenario(command));
    String idempotencyKey = command.tenantId() + ":" + command.channelId() + ":" + command.idempotencyKey();
    LosQuoteResult replay = replayOrConflict(idempotencyKey, requestHash, command.correlationId());
    if (replay != null) {
      return replay;
    }

    Instant submittedAt = clock.instant();
    String requestId = deterministicId(command.tenantId(), command.channelId(), command.losLoanId(), requestHash);
    LosQuoteRequest accepted =
        new LosQuoteRequest(
            command.tenantId(),
            command.channelId(),
            requestId,
            command.losLoanId(),
            command.idempotencyKey(),
            LosQuoteStatus.ACCEPTED,
            requestHash,
            channel.pricingConfigVersion(),
            "",
            Map.of(),
            "",
            submittedAt,
            null,
            command.actorId(),
            command.correlationId());
    requests.put(requestKey(command.tenantId(), command.channelId(), requestId), accepted);
    writeOutbox(ACCEPTED_EVENT_TYPE, accepted, command.actorId(), command.idempotencyKey(), List.of(), Map.of("requestHash", requestHash));

    PriceScenarioCommand pricingCommand =
        new PriceScenarioCommand(
            command.tenantId(),
            command.channelId(),
            requestId,
            normalizedScenario(command),
            channel.pricingConfigVersion(),
            command.correlationId(),
            command.correlationId(),
            PRICING_TIMEOUT);
    PricingResponseSnapshot pricing = priceWithOneSafeRetry(pricingCommand);

    LosQuoteRequest completed;
    if (pricing.timeout()) {
      completed = complete(accepted, LosQuoteStatus.FAILED_RETRYABLE, "", Map.of("retryable", "true"), "PRICING_SERVICE_TIMEOUT");
    } else if (!pricing.reasonCodes().isEmpty()) {
      completed = complete(accepted, LosQuoteStatus.REJECTED, pricing.quoteId(), pricing.resultSnapshot(), String.join(",", pricing.reasonCodes()));
    } else {
      completed = complete(accepted, LosQuoteStatus.PRICED, pricing.quoteId(), pricing.resultSnapshot(), "");
    }
    requests.put(requestKey(completed.tenantId(), completed.channelId(), completed.requestId()), completed);

    String eventType = switch (completed.status()) {
      case PRICED -> PRICED_EVENT_TYPE;
      case REJECTED, FAILED_RETRYABLE -> REJECTED_EVENT_TYPE;
      case ACCEPTED -> ACCEPTED_EVENT_TYPE;
    };
    writeOutbox(eventType, completed, command.actorId(), command.idempotencyKey(), pricing.reasonCodes(), redactedResult(completed));
    writeAudit(command.actorId(), accepted, completed);

    LosQuoteResponse response = response(completed, pricing.reasonCodes(), pricing.warnings());
    idempotencyEntries.put(idempotencyKey, new IdempotencyEntry(requestHash, response));
    return LosQuoteResult.success(response);
  }

  public LosQuoteResult fetch(String tenantId, String channelId, String requestId, String correlationId) {
    if (!isUuid(tenantId) || isBlank(channelId) || isBlank(requestId)) {
      return LosQuoteResult.failure(error("400", "VALIDATION_FAILED", "tenantId, channelId, and requestId are required", correlationId, false));
    }
    LosQuoteRequest request = requests.get(requestKey(tenantId, channelId, requestId));
    if (request == null) {
      return LosQuoteResult.failure(error("404", "NOT_FOUND", "LOS quote request was not found", correlationId, false));
    }
    return LosQuoteResult.success(response(request, List.of(), List.of()));
  }

  public List<LosQuoteRequest> requestsForTenant(String tenantId) {
    return requests.values().stream()
        .filter(request -> request.tenantId().equals(tenantId))
        .sorted(Comparator.comparing(LosQuoteRequest::submittedAt))
        .toList();
  }

  public List<LosQuoteOutboxEvent> outboxEvents() {
    return List.copyOf(outboxEvents);
  }

  public List<LosQuoteAuditRecord> auditRecords() {
    return List.copyOf(auditRecords);
  }

  private LosQuoteResponse rejectBeforePricing(SubmitLosQuoteCommand command, String reason, String pricingConfigVersion) {
    Instant now = clock.instant();
    String requestHash = hash(canonicalScenario(command));
    String requestId = deterministicId(command.tenantId(), command.channelId(), command.losLoanId(), requestHash);
    LosQuoteRequest request =
        new LosQuoteRequest(
            command.tenantId(),
            command.channelId(),
            requestId,
            command.losLoanId(),
            command.idempotencyKey(),
            LosQuoteStatus.REJECTED,
            requestHash,
            pricingConfigVersion,
            "",
            Map.of(),
            reason,
            now,
            now,
            command.actorId(),
            command.correlationId());
    requests.put(requestKey(command.tenantId(), command.channelId(), requestId), request);
    writeOutbox(REJECTED_EVENT_TYPE, request, command.actorId(), command.idempotencyKey(), List.of(reason), Map.of("requestHash", requestHash));
    writeAudit(command.actorId(), null, request);
    return response(request, List.of(reason), List.of());
  }

  private LosQuoteError validateCommand(SubmitLosQuoteCommand command) {
    if (command == null) {
      return error("400", "VALIDATION_FAILED", "command is required", "", false);
    }
    if (!isUuid(command.tenantId())) {
      return error("400", "VALIDATION_FAILED", "tenantId must be a UUID", command.correlationId(), false);
    }
    if (isBlank(command.channelId())) {
      return error("400", "VALIDATION_FAILED", "channelId is required", command.correlationId(), false);
    }
    if (isBlank(command.actorId())) {
      return error("401", "UNAUTHENTICATED", "actorId is required", command.correlationId(), false);
    }
    if (isBlank(command.idempotencyKey())) {
      return error("400", "VALIDATION_FAILED", "Idempotency-Key is required", command.correlationId(), false);
    }
    if (isBlank(command.correlationId())) {
      return error("400", "VALIDATION_FAILED", "correlationId is required", "", false);
    }
    if (!SUPPORTED_SCHEMA_VERSION.equals(command.clientSchemaVersion())) {
      return error("400", "UNSUPPORTED_SCHEMA_VERSION", "Unsupported LOS quote schema version", command.correlationId(), false);
    }
    if (isBlank(command.losLoanId()) || command.property() == null || command.loanTerms() == null || command.credit() == null) {
      return error("400", "VALIDATION_FAILED", "losLoanId, property, loanTerms, and credit are required", command.correlationId(), false);
    }
    if (isBlank(command.occupancy()) || isBlank(command.lockIntent()) || isBlank(command.productPreference()) || command.requestedAt() == null || isBlank(command.sourceSystem())) {
      return error("400", "VALIDATION_FAILED", "occupancy, lockIntent, productPreference, requestedAt, and sourceSystem are required", command.correlationId(), false);
    }
    if (command.borrowerSummary() != null && command.borrowerSummary().containsSensitiveFields()) {
      return error("400", "VALIDATION_FAILED", "Borrower names and SSN are not accepted by this LOS quote contract", command.correlationId(), false);
    }
    if (command.loanTerms().loanAmount() == null || command.loanTerms().loanAmount().signum() <= 0 || command.loanTerms().noteRate() == null || command.loanTerms().noteRate().signum() < 0) {
      return error("400", "VALIDATION_FAILED", "Positive loan amount and non-negative note rate are required", command.correlationId(), false);
    }
    if (command.credit().representativeCreditScore() == null || command.credit().representativeCreditScore() < 300 || command.credit().representativeCreditScore() > 850) {
      return error("400", "VALIDATION_FAILED", "representativeCreditScore must be in the valid credit score range", command.correlationId(), false);
    }
    return null;
  }

  private LosQuoteResult replayOrConflict(String idempotencyKey, String requestHash, String correlationId) {
    IdempotencyEntry existing = idempotencyEntries.get(idempotencyKey);
    if (existing == null) {
      return null;
    }
    if (!existing.requestHash().equals(requestHash)) {
      return LosQuoteResult.failure(error("409", "IDEMPOTENCY_CONFLICT", "Idempotency key was reused with a different LOS quote body", correlationId, false));
    }
    return LosQuoteResult.success(existing.response());
  }

  private PricingResponseSnapshot priceWithOneSafeRetry(PriceScenarioCommand command) {
    PricingResponseSnapshot first = pricingClient.price(command);
    if (!first.retryableFailure()) {
      return first;
    }
    return pricingClient.price(command);
  }

  private LosQuoteRequest complete(LosQuoteRequest accepted, LosQuoteStatus status, String quoteId, Map<String, String> result, String failureCode) {
    return new LosQuoteRequest(
        accepted.tenantId(),
        accepted.channelId(),
        accepted.requestId(),
        accepted.losLoanId(),
        accepted.idempotencyKey(),
        status,
        accepted.normalizedScenarioHash(),
        accepted.pricingConfigVersion(),
        quoteId == null ? "" : quoteId,
        result == null ? Map.of() : Map.copyOf(result),
        failureCode == null ? "" : failureCode,
        accepted.submittedAt(),
        clock.instant(),
        accepted.actorId(),
        accepted.correlationId());
  }

  private void writeOutbox(String eventType, LosQuoteRequest request, String actorId, String idempotencyKey, List<String> reasonCodes, Map<String, String> payload) {
    outboxEvents.add(
        new LosQuoteOutboxEvent(
            deterministicId(request.tenantId(), request.requestId(), request.status().name(), String.valueOf(outboxEvents.size() + 1)),
            eventType,
            1,
            request.tenantId(),
            request.channelId(),
            request.requestId(),
            actorId,
            request.correlationId(),
            request.correlationId(),
            idempotencyKey,
            hash(request.normalizedScenarioHash() + ":" + request.status() + ":" + canonicalMap(payload)),
            request.completedAt() == null ? request.submittedAt() : request.completedAt(),
            Map.of(
                "requestId", request.requestId(),
                "tenantId", request.tenantId(),
                "status", request.status().name(),
                "reasonCodes", String.join(",", reasonCodes),
                "pricingConfigVersion", request.pricingConfigVersion(),
                "payloadHash", hash(canonicalMap(payload)))));
  }

  private void writeAudit(String actorId, LosQuoteRequest before, LosQuoteRequest after) {
    auditRecords.add(
        new LosQuoteAuditRecord(
            deterministicId(after.tenantId(), after.requestId(), "audit", String.valueOf(auditRecords.size() + 1)),
            after.tenantId(),
            after.channelId(),
            after.requestId(),
            actorId,
            AUDIT_ACTION,
            before == null ? "" : hash(before.status() + ":" + before.normalizedScenarioHash()),
            hash(after.status() + ":" + after.normalizedScenarioHash()),
            after.correlationId(),
            hash(after.requestId() + ":" + after.status() + ":" + after.normalizedScenarioHash()),
            after.completedAt() == null ? after.submittedAt() : after.completedAt()));
  }

  private LosQuoteResponse response(LosQuoteRequest request, List<String> reasonCodes, List<String> warnings) {
    return new LosQuoteResponse(
        request.requestId(),
        request.status(),
        request.quoteId(),
        List.of(),
        Map.copyOf(request.resultSnapshot()),
        List.copyOf(reasonCodes),
        List.copyOf(warnings),
        request.correlationId());
  }

  private Map<String, String> normalizedScenario(SubmitLosQuoteCommand command) {
    Map<String, String> scenario = new HashMap<>();
    scenario.put("tenantId", command.tenantId());
    scenario.put("channelId", command.channelId());
    scenario.put("losLoanId", command.losLoanId());
    scenario.put("propertyState", command.property().state());
    scenario.put("propertyUsage", command.property().propertyType());
    scenario.put("loanAmount", command.loanTerms().loanAmount().toPlainString());
    scenario.put("noteRate", command.loanTerms().noteRate().toPlainString());
    scenario.put("representativeCreditScoreHash", hash(String.valueOf(command.credit().representativeCreditScore())));
    scenario.put("occupancy", command.occupancy());
    scenario.put("lockIntent", command.lockIntent());
    scenario.put("productPreference", command.productPreference());
    scenario.put("requestedAt", command.requestedAt().toString());
    scenario.put("sourceSystem", command.sourceSystem());
    scenario.put("clientSchemaVersion", command.clientSchemaVersion());
    return scenario;
  }

  private Map<String, String> redactedResult(LosQuoteRequest request) {
    Map<String, String> result = new HashMap<>();
    result.put("quoteId", request.quoteId());
    result.put("failureCode", request.failureCode());
    result.put("resultHash", hash(canonicalMap(request.resultSnapshot())));
    return result;
  }

  private String canonicalScenario(SubmitLosQuoteCommand command) {
    return canonicalMap(normalizedScenario(command));
  }

  private String key(String tenantId, String channelId) {
    return tenantId + ":" + channelId;
  }

  private String requestKey(String tenantId, String channelId, String requestId) {
    return tenantId + ":" + channelId + ":" + requestId;
  }

  private String deterministicId(String... parts) {
    return UUID.nameUUIDFromBytes(String.join(":", parts).getBytes(StandardCharsets.UTF_8)).toString();
  }

  private boolean isUuid(String value) {
    try {
      UUID.fromString(value);
      return true;
    } catch (RuntimeException ignored) {
      return false;
    }
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private String canonicalMap(Map<String, String> map) {
    return map.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .map(entry -> entry.getKey() + "=" + entry.getValue())
        .reduce((left, right) -> left + ";" + right)
        .orElse("");
  }

  private String hash(String value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is required", exception);
    }
  }

  private LosQuoteError error(String code, String reason, String message, String correlationId, boolean retryable) {
    return new LosQuoteError(code, reason, message, List.of(), correlationId == null ? "" : correlationId, retryable);
  }

  private record IdempotencyEntry(String requestHash, LosQuoteResponse response) {}

  public enum LosQuoteStatus {
    ACCEPTED,
    PRICED,
    REJECTED,
    FAILED_RETRYABLE
  }

  public interface PricingClient {
    PricingResponseSnapshot price(PriceScenarioCommand command);
  }

  public record SubmitLosQuoteCommand(
      String tenantId,
      String channelId,
      String idempotencyKey,
      String actorId,
      String losLoanId,
      BorrowerSummary borrowerSummary,
      PropertySummary property,
      LoanTerms loanTerms,
      BorrowerCreditSummary credit,
      String occupancy,
      String lockIntent,
      String productPreference,
      Instant requestedAt,
      String sourceSystem,
      String clientSchemaVersion,
      String correlationId) {}

  public record BorrowerSummary(String tokenizedBorrowerRef, boolean containsName, boolean containsSsn) {
    boolean containsSensitiveFields() {
      return containsName || containsSsn;
    }
  }

  public record PropertySummary(String state, String propertyType, BigDecimal estimatedValue) {}

  public record LoanTerms(BigDecimal loanAmount, BigDecimal noteRate, String purpose, String lienPosition) {}

  public record BorrowerCreditSummary(Integer representativeCreditScore, String creditBucketRef) {}

  public record PriceScenarioCommand(
      String tenantId,
      String channelId,
      String requestId,
      Map<String, String> normalizedScenario,
      String pricingConfigVersion,
      String correlationId,
      String causationId,
      Duration timeout) {}

  public record PricingResponseSnapshot(
      String quoteId,
      Map<String, String> resultSnapshot,
      List<String> reasonCodes,
      List<String> warnings,
      boolean retryableFailure,
      boolean timeout) {
    public static PricingResponseSnapshot priced(String quoteId, Map<String, String> resultSnapshot, List<String> warnings) {
      return new PricingResponseSnapshot(quoteId, Map.copyOf(resultSnapshot), List.of(), List.copyOf(warnings), false, false);
    }

    public static PricingResponseSnapshot rejected(List<String> reasonCodes) {
      return new PricingResponseSnapshot("", Map.of(), List.copyOf(reasonCodes), List.of(), false, false);
    }

    public static PricingResponseSnapshot retryableTimeout() {
      return new PricingResponseSnapshot("", Map.of(), List.of("PRICING_SERVICE_TIMEOUT"), List.of(), true, true);
    }
  }

  public record ChannelConfiguration(String tenantId, String channelId, boolean active, List<String> allowedProducts, String pricingConfigVersion) {}

  public record LosQuoteRequest(
      String tenantId,
      String channelId,
      String requestId,
      String losLoanId,
      String idempotencyKey,
      LosQuoteStatus status,
      String normalizedScenarioHash,
      String pricingConfigVersion,
      String quoteId,
      Map<String, String> resultSnapshot,
      String failureCode,
      Instant submittedAt,
      Instant completedAt,
      String actorId,
      String correlationId) {}

  public record LosQuoteResponse(
      String requestId,
      LosQuoteStatus status,
      String quoteId,
      List<String> eligibleProducts,
      Map<String, String> priceSummary,
      List<String> reasonCodes,
      List<String> warnings,
      String correlationId) {}

  public record LosQuoteError(String code, String reason, String message, List<String> fieldErrors, String correlationId, boolean retryable) {}

  public record LosQuoteResult(boolean valid, Optional<LosQuoteResponse> value, Optional<LosQuoteError> error) {
    public static LosQuoteResult success(LosQuoteResponse value) {
      return new LosQuoteResult(true, Optional.of(value), Optional.empty());
    }

    public static LosQuoteResult failure(LosQuoteError error) {
      return new LosQuoteResult(false, Optional.empty(), Optional.of(error));
    }
  }

  public record LosQuoteOutboxEvent(
      String eventId,
      String eventType,
      int schemaVersion,
      String tenantId,
      String channelId,
      String requestId,
      String actor,
      String correlationId,
      String causationId,
      String idempotencyKey,
      String payloadHash,
      Instant occurredAt,
      Map<String, String> payload) {}

  public record LosQuoteAuditRecord(
      String auditId,
      String tenantId,
      String channelId,
      String requestId,
      String actor,
      String action,
      String beforeHash,
      String afterHash,
      String correlationId,
      String replayHash,
      Instant occurredAt) {}
}
