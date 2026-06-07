package com.wcpe.integration;

import java.net.URI;
import java.net.URISyntaxException;
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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class WebhookDeliveryService {
  public static final String BASE_PATH = "/api/v1/tenants/{tenantId}/webhook-deliveries";
  public static final String READ_PERMISSION = "integrations.webhook.delivery.read";
  public static final String WRITE_PERMISSION = "integrations.webhook.delivery.write";
  public static final String DELIVERED_EVENT_TYPE = "integration.webhook-delivery.delivered.v1";
  public static final String FAILED_EVENT_TYPE = "integration.webhook-delivery.failed.v1";
  public static final String DEAD_LETTERED_EVENT_TYPE = "integration.webhook-delivery.dead-lettered.v1";
  public static final String AUDIT_DELIVERED_ACTION = "WEBHOOK_DELIVERY_DELIVERED";
  public static final String AUDIT_FAILED_ACTION = "WEBHOOK_DELIVERY_FAILED";
  public static final String AUDIT_DEAD_LETTERED_ACTION = "WEBHOOK_DELIVERY_DEAD_LETTERED";
  public static final String SIGNATURE_HEADER = "X-Integration-Signature";
  public static final int RESPONSE_CAPTURE_LIMIT = 512;

  private static final List<Duration> DEFAULT_BACKOFF =
      List.of(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(30), Duration.ofHours(2), Duration.ofHours(6));

  private final Clock clock;
  private final WebhookSignatureBuilder signatureBuilder;
  private final WebhookEndpointClient endpointClient;
  private final WebhookFailureClassifier failureClassifier = new WebhookFailureClassifier();
  private final Map<String, WebhookEndpointPolicy> endpointPolicies = new HashMap<>();
  private final Map<String, WebhookDelivery> deliveries = new HashMap<>();
  private final Map<String, String> payloadBodiesByDelivery = new HashMap<>();
  private final Map<String, IdempotencyEntry> idempotencyEntries = new HashMap<>();
  private final List<WebhookDeliveryAttempt> attempts = new ArrayList<>();
  private final List<WebhookDeliveryOutboxEvent> outboxEvents = new ArrayList<>();
  private final List<WebhookDeliveryAuditRecord> auditRecords = new ArrayList<>();
  private final Map<String, Long> metrics = new HashMap<>();

  public WebhookDeliveryService(WebhookSignatureBuilder signatureBuilder, WebhookEndpointClient endpointClient) {
    this(Clock.systemUTC(), signatureBuilder, endpointClient);
  }

  public WebhookDeliveryService(Clock clock, WebhookSignatureBuilder signatureBuilder, WebhookEndpointClient endpointClient) {
    this.clock = clock;
    this.signatureBuilder = signatureBuilder;
    this.endpointClient = endpointClient;
  }

  public void configureEndpointPolicy(String tenantId, String subscriptionId, WebhookEndpointPolicy policy) {
    endpointPolicies.put(policyKey(tenantId, subscriptionId), policy);
  }

  public WebhookDeliveryResult<WebhookDeliveryResponse> enqueue(EnqueueWebhookDeliveryCommand command) {
    WebhookDeliveryError validation = validateEnqueue(command);
    if (validation != null) {
      return WebhookDeliveryResult.failure(validation);
    }

    WebhookEndpointPolicy policy = endpointPolicies.get(policyKey(command.tenantId(), command.subscriptionId()));
    if (policy == null || policy.maxAttempts() < 1 || policy.allowedEventTypes() == null || !policy.allowedEventTypes().contains(command.eventType())) {
      return WebhookDeliveryResult.failure(error("422", "POLICY_NOT_SATISFIED", "Tenant webhook delivery policy configuration is required", command.correlationId(), false));
    }
    WebhookDeliveryError endpoint = validateEndpoint(policy.endpointUrl(), command.correlationId());
    if (endpoint != null) {
      return WebhookDeliveryResult.failure(endpoint);
    }

    String requestHash = hash(canonicalEnqueue(command));
    String idempotencyKey = command.tenantId() + ":" + command.subscriptionId() + ":" + command.sourceEventId();
    WebhookDeliveryResult<WebhookDeliveryResponse> replay = replayOrConflict(idempotencyKey, requestHash, command.correlationId());
    if (replay != null) {
      return replay;
    }

    Instant now = clock.instant();
    String deliveryId = deterministicId(command.tenantId(), command.subscriptionId(), command.sourceEventId());
    WebhookDelivery delivery =
        new WebhookDelivery(
            command.tenantId(),
            deliveryId,
            command.subscriptionId(),
            command.sourceEventId(),
            command.eventType(),
            WebhookDeliveryStatus.PENDING,
            now,
            0,
            hash(command.payloadBody()),
            policy.secretVersion(),
            "",
            1,
            command.actorId(),
            command.correlationId(),
            now,
            now);
    deliveries.put(deliveryKey(delivery.tenantId(), delivery.deliveryId()), delivery);
    payloadBodiesByDelivery.put(deliveryKey(delivery.tenantId(), delivery.deliveryId()), command.payloadBody());
    WebhookDeliveryResponse response = response(delivery, "queued", List.of());
    idempotencyEntries.put(idempotencyKey, new IdempotencyEntry(requestHash, response));
    return WebhookDeliveryResult.success(response);
  }

  public List<WebhookDeliveryResponse> processDue(String tenantId, Instant dueAt, int limit) {
    if (!isUuid(tenantId) || dueAt == null || limit < 1) {
      return List.of();
    }
    return deliveries.values().stream()
        .filter(delivery -> delivery.tenantId().equals(tenantId))
        .filter(delivery -> delivery.status() == WebhookDeliveryStatus.PENDING || delivery.status() == WebhookDeliveryStatus.RETRY_SCHEDULED)
        .filter(delivery -> !delivery.nextAttemptAt().isAfter(dueAt))
        .sorted(Comparator.comparing(WebhookDelivery::nextAttemptAt).thenComparing(WebhookDelivery::deliveryId))
        .limit(limit)
        .map(delivery -> attemptDelivery(delivery, delivery.actorId(), delivery.correlationId(), false))
        .toList();
  }

  public WebhookDeliveryResult<WebhookDeliveryResponse> manualRetry(ManualWebhookRetryCommand command) {
    WebhookDeliveryError validation = validateManualRetry(command);
    if (validation != null) {
      return WebhookDeliveryResult.failure(validation);
    }
    WebhookDelivery delivery = deliveries.get(deliveryKey(command.tenantId(), command.deliveryId()));
    if (delivery == null) {
      return WebhookDeliveryResult.failure(error("404", "NOT_FOUND", "Webhook delivery was not found", command.correlationId(), false));
    }
    if (delivery.status() == WebhookDeliveryStatus.DELIVERED || delivery.status() == WebhookDeliveryStatus.IN_FLIGHT || delivery.status() == WebhookDeliveryStatus.CANCELLED) {
      return WebhookDeliveryResult.failure(error("422", "POLICY_NOT_SATISFIED", "Webhook delivery status does not allow manual retry", command.correlationId(), false));
    }
    return WebhookDeliveryResult.success(attemptDelivery(delivery, command.actorId(), command.correlationId(), true));
  }

  public WebhookDeliveryResult<WebhookDeliveryResponse> fetch(String tenantId, String deliveryId, String correlationId) {
    if (!isUuid(tenantId) || isBlank(deliveryId)) {
      return WebhookDeliveryResult.failure(error("400", "VALIDATION_FAILED", "tenantId and deliveryId are required", correlationId, false));
    }
    WebhookDelivery delivery = deliveries.get(deliveryKey(tenantId, deliveryId));
    if (delivery == null) {
      return WebhookDeliveryResult.failure(error("404", "NOT_FOUND", "Webhook delivery was not found", correlationId, false));
    }
    return WebhookDeliveryResult.success(response(delivery, "fetched", List.of()));
  }

  public List<WebhookDeliveryResponse> search(SearchWebhookDeliveriesQuery query) {
    if (query == null || !isUuid(query.tenantId())) {
      return List.of();
    }
    return deliveries.values().stream()
        .filter(delivery -> delivery.tenantId().equals(query.tenantId()))
        .filter(delivery -> query.status() == null || delivery.status() == query.status())
        .filter(delivery -> isBlank(query.subscriptionId()) || delivery.subscriptionId().equals(query.subscriptionId()))
        .filter(delivery -> isBlank(query.eventType()) || delivery.eventType().equals(query.eventType()))
        .filter(delivery -> query.from() == null || !delivery.createdAt().isBefore(query.from()))
        .filter(delivery -> query.to() == null || !delivery.createdAt().isAfter(query.to()))
        .sorted(Comparator.comparing(WebhookDelivery::updatedAt).reversed())
        .map(delivery -> response(delivery, "search result", List.of()))
        .toList();
  }

  public List<WebhookDeliveryAttempt> attemptsForDelivery(String tenantId, String deliveryId) {
    return attempts.stream()
        .filter(attempt -> attempt.tenantId().equals(tenantId))
        .filter(attempt -> attempt.deliveryId().equals(deliveryId))
        .sorted(Comparator.comparing(WebhookDeliveryAttempt::startedAt))
        .toList();
  }

  public List<WebhookDeliveryOutboxEvent> outboxEvents() {
    return List.copyOf(outboxEvents);
  }

  public List<WebhookDeliveryAuditRecord> auditRecords() {
    return List.copyOf(auditRecords);
  }

  public Map<String, Long> metrics() {
    return Map.copyOf(metrics);
  }

  private WebhookDeliveryResponse attemptDelivery(WebhookDelivery existing, String actorId, String correlationId, boolean manual) {
    WebhookEndpointPolicy policy = endpointPolicies.get(policyKey(existing.tenantId(), existing.subscriptionId()));
    String key = deliveryKey(existing.tenantId(), existing.deliveryId());
    String payloadBody = payloadBodiesByDelivery.getOrDefault(key, "");
    Instant startedAt = clock.instant();
    SignedWebhookPayload signed = signatureBuilder.sign(policy.secretRef(), startedAt.toString(), payloadBody);
    Map<String, String> signedHeaders = canonicalSignedHeaders(signed);
    WebhookDeliveryRequest request =
        new WebhookDeliveryRequest(
            existing.tenantId(),
            existing.deliveryId(),
            existing.subscriptionId(),
            existing.sourceEventId(),
            existing.eventType(),
            policy.endpointUrl(),
            signedHeaders,
            existing.payloadHash(),
            payloadBody,
            correlationId);

    WebhookDeliveryHttpResponse httpResponse = endpointClient.deliver(request);
    Instant completedAt = clock.instant();
    WebhookFailureClass failureClass = failureClassifier.classify(httpResponse);
    boolean retryable = failureClassifier.isRetryable(failureClass, httpResponse.statusCode());
    int attemptCount = existing.attemptCount() + 1;
    WebhookDeliveryStatus nextStatus;
    Instant nextAttemptAt = null;
    String eventType;
    String auditAction;
    if (failureClass == WebhookFailureClass.NONE) {
      nextStatus = WebhookDeliveryStatus.DELIVERED;
      eventType = DELIVERED_EVENT_TYPE;
      auditAction = AUDIT_DELIVERED_ACTION;
      payloadBodiesByDelivery.remove(key);
    } else if (retryable && attemptCount < policy.maxAttempts()) {
      nextStatus = WebhookDeliveryStatus.RETRY_SCHEDULED;
      nextAttemptAt = new WebhookRetryScheduler(policy.retryBackoffSchedule(), policy.retryJitterMax()).nextAttemptAt(completedAt, attemptCount, existing.tenantId(), existing.deliveryId());
      eventType = FAILED_EVENT_TYPE;
      auditAction = AUDIT_FAILED_ACTION;
    } else if (retryable) {
      nextStatus = WebhookDeliveryStatus.DEAD_LETTERED;
      eventType = DEAD_LETTERED_EVENT_TYPE;
      auditAction = AUDIT_DEAD_LETTERED_ACTION;
      recordMetric("webhook_delivery_dlq_total");
    } else {
      nextStatus = WebhookDeliveryStatus.FAILED;
      eventType = FAILED_EVENT_TYPE;
      auditAction = AUDIT_FAILED_ACTION;
    }

    WebhookDelivery updated =
        new WebhookDelivery(
            existing.tenantId(),
            existing.deliveryId(),
            existing.subscriptionId(),
            existing.sourceEventId(),
            existing.eventType(),
            nextStatus,
            nextAttemptAt,
            attemptCount,
            existing.payloadHash(),
            policy.secretVersion(),
            failureClass.name(),
            existing.version() + 1,
            actorId,
            correlationId,
            existing.createdAt(),
            completedAt);
    deliveries.put(key, updated);
    attempts.add(
        new WebhookDeliveryAttempt(
            deterministicId(existing.tenantId(), existing.deliveryId(), String.valueOf(attemptCount)),
            existing.tenantId(),
            existing.deliveryId(),
            startedAt,
            completedAt,
            httpResponse.statusCode(),
            httpResponse.responseTime().toMillis(),
            hash(signedHeaders.toString()),
            hash(captureBody(httpResponse.responseBody())),
            failureClass.name(),
            retryable,
            manual));
    writeOutboxAndAudit(eventType, auditAction, actorId, policy.idempotencyKeyLabel(), updated, existing, retryable, manual);
    recordMetric("webhook_delivery_attempts_total");
    recordMetric("webhook_delivery_latency_ms", httpResponse.responseTime().toMillis());
    return response(updated, nextStatus == WebhookDeliveryStatus.RETRY_SCHEDULED ? "retry scheduled" : nextStatus.name().toLowerCase(Locale.ROOT), List.of());
  }

  private WebhookDeliveryError validateEnqueue(EnqueueWebhookDeliveryCommand command) {
    if (command == null) {
      return error("400", "VALIDATION_FAILED", "command is required", "", false);
    }
    WebhookDeliveryError common = validateCommon(command.tenantId(), command.actorId(), command.correlationId());
    if (common != null) {
      return common;
    }
    if (isBlank(command.subscriptionId()) || isBlank(command.sourceEventId()) || isBlank(command.eventType()) || isBlank(command.payloadBody())) {
      return error("400", "VALIDATION_FAILED", "subscriptionId, sourceEventId, eventType, and payloadBody are required", command.correlationId(), false);
    }
    return null;
  }

  private WebhookDeliveryError validateManualRetry(ManualWebhookRetryCommand command) {
    if (command == null) {
      return error("400", "VALIDATION_FAILED", "command is required", "", false);
    }
    WebhookDeliveryError common = validateCommon(command.tenantId(), command.actorId(), command.correlationId());
    if (common != null) {
      return common;
    }
    if (isBlank(command.deliveryId())) {
      return error("400", "VALIDATION_FAILED", "deliveryId is required", command.correlationId(), false);
    }
    return null;
  }

  private WebhookDeliveryError validateCommon(String tenantId, String actorId, String correlationId) {
    if (!isUuid(tenantId)) {
      return error("400", "VALIDATION_FAILED", "tenantId must be a UUID", correlationId, false);
    }
    if (isBlank(actorId)) {
      return error("401", "UNAUTHENTICATED", "actorId is required", correlationId, false);
    }
    if (isBlank(correlationId)) {
      return error("400", "VALIDATION_FAILED", "correlationId is required", "", false);
    }
    return null;
  }

  private WebhookDeliveryError validateEndpoint(String endpointUrl, String correlationId) {
    if (isBlank(endpointUrl)) {
      return error("422", "POLICY_NOT_SATISFIED", "endpointUrl is required by tenant policy", correlationId, false);
    }
    try {
      URI uri = new URI(endpointUrl);
      if (!"https".equalsIgnoreCase(uri.getScheme()) || isBlank(uri.getHost())) {
        return error("422", "POLICY_NOT_SATISFIED", "Webhook delivery endpoint must use HTTPS", correlationId, false);
      }
    } catch (URISyntaxException exception) {
      return error("422", "POLICY_NOT_SATISFIED", "Webhook delivery endpoint must be a valid URI", correlationId, false);
    }
    return null;
  }

  private WebhookDeliveryResult<WebhookDeliveryResponse> replayOrConflict(String idempotencyKey, String requestHash, String correlationId) {
    IdempotencyEntry existing = idempotencyEntries.get(idempotencyKey);
    if (existing == null) {
      return null;
    }
    if (!existing.requestHash().equals(requestHash)) {
      return WebhookDeliveryResult.failure(error("409", "IDEMPOTENCY_CONFLICT", "Source event was reused with a different webhook delivery payload", correlationId, false));
    }
    return WebhookDeliveryResult.success(existing.response());
  }

  private void writeOutboxAndAudit(String eventType, String auditAction, String actorId, String idempotencyKey, WebhookDelivery after, WebhookDelivery before, boolean retryable, boolean manual) {
    String eventId = deterministicId(after.tenantId(), after.deliveryId(), eventType, String.valueOf(outboxEvents.size() + 1));
    String payloadHash = hash(after.tenantId() + ":" + after.deliveryId() + ":" + after.status() + ":" + after.attemptCount() + ":" + after.payloadHash());
    Map<String, String> payload = new HashMap<>();
    payload.put("tenantId", after.tenantId());
    payload.put("deliveryId", after.deliveryId());
    payload.put("subscriptionId", after.subscriptionId());
    payload.put("sourceEventId", after.sourceEventId());
    payload.put("eventType", after.eventType());
    payload.put("status", after.status().name());
    payload.put("attemptCount", String.valueOf(after.attemptCount()));
    payload.put("payloadHash", after.payloadHash());
    payload.put("failureClass", after.lastFailureClass());
    payload.put("retryable", String.valueOf(retryable));
    payload.put("manual", String.valueOf(manual));
    payload.put("replayEligibility", String.valueOf(after.status() == WebhookDeliveryStatus.DEAD_LETTERED || after.status() == WebhookDeliveryStatus.FAILED));
    outboxEvents.add(new WebhookDeliveryOutboxEvent(eventId, eventType, 1, after.tenantId(), actorId, after.correlationId(), after.deliveryId(), idempotencyKey, payloadHash, after.updatedAt(), Map.copyOf(payload)));
    auditRecords.add(new WebhookDeliveryAuditRecord(deterministicId(after.tenantId(), after.deliveryId(), auditAction, String.valueOf(auditRecords.size() + 1)), after.tenantId(), after.deliveryId(), actorId, auditAction, before == null ? "" : hash(before.status() + ":" + before.attemptCount()), hash(after.status() + ":" + after.attemptCount()), after.correlationId(), payloadHash, after.updatedAt()));
  }

  private WebhookDeliveryResponse response(WebhookDelivery delivery, String summary, List<String> validationMessages) {
    return new WebhookDeliveryResponse(
        delivery.deliveryId(),
        delivery.subscriptionId(),
        delivery.sourceEventId(),
        delivery.eventType(),
        delivery.status(),
        delivery.nextAttemptAt(),
        delivery.attemptCount(),
        delivery.payloadHash(),
        delivery.signatureVersion(),
        delivery.lastFailureClass(),
        delivery.version(),
        summary,
        List.copyOf(validationMessages),
        "audit:" + delivery.tenantId() + ":" + delivery.deliveryId() + ":" + delivery.version(),
        "dlq:" + delivery.tenantId() + ":" + delivery.deliveryId(),
        delivery.correlationId());
  }

  private String canonicalEnqueue(EnqueueWebhookDeliveryCommand command) {
    return String.join("|", command.tenantId(), command.subscriptionId(), command.sourceEventId(), command.eventType(), hash(command.payloadBody()), command.actorId(), command.correlationId());
  }

  private String captureBody(String body) {
    if (body == null) {
      return "";
    }
    return body.length() <= RESPONSE_CAPTURE_LIMIT ? body : body.substring(0, RESPONSE_CAPTURE_LIMIT);
  }

  private Map<String, String> canonicalSignedHeaders(SignedWebhookPayload signed) {
    Map<String, String> headers = new HashMap<>(signed == null || signed.headers() == null ? Map.of() : signed.headers());
    if (signed != null && !isBlank(signed.signature()) && headers.keySet().stream().noneMatch(SIGNATURE_HEADER::equalsIgnoreCase)) {
      headers.put(SIGNATURE_HEADER, signed.signature());
    }
    return Map.copyOf(headers);
  }

  private String policyKey(String tenantId, String subscriptionId) {
    return tenantId + ":" + subscriptionId;
  }

  private String deliveryKey(String tenantId, String deliveryId) {
    return tenantId + ":" + deliveryId;
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

  private String hash(String value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is required", exception);
    }
  }

  private void recordMetric(String metric) {
    metrics.put(metric, metrics.getOrDefault(metric, 0L) + 1L);
  }

  private void recordMetric(String metric, long value) {
    metrics.put(metric, metrics.getOrDefault(metric, 0L) + value);
  }

  private WebhookDeliveryError error(String code, String reason, String message, String correlationId, boolean retryable) {
    return new WebhookDeliveryError(code, reason, message, List.of(), correlationId == null ? "" : correlationId, retryable);
  }

  private record IdempotencyEntry(String requestHash, WebhookDeliveryResponse response) {}

  public enum WebhookDeliveryStatus {
    PENDING,
    IN_FLIGHT,
    DELIVERED,
    RETRY_SCHEDULED,
    FAILED,
    DEAD_LETTERED,
    CANCELLED
  }

  public enum WebhookFailureClass {
    NONE,
    CLIENT_ERROR,
    RETRYABLE_CLIENT_ERROR,
    SERVER_ERROR,
    TIMEOUT,
    TLS_FAILURE,
    DEPENDENCY_UNAVAILABLE
  }

  public interface WebhookEndpointClient {
    WebhookDeliveryHttpResponse deliver(WebhookDeliveryRequest request);
  }

  public interface WebhookSignatureBuilder {
    SignedWebhookPayload sign(String secretRef, String timestamp, String payloadBody);
  }

  public record WebhookEndpointPolicy(String endpointUrl, String secretRef, String secretVersion, int maxAttempts, List<String> allowedEventTypes, String idempotencyKeyLabel, List<Duration> retryBackoffSchedule, Duration retryJitterMax) {
    public WebhookEndpointPolicy(String endpointUrl, String secretRef, String secretVersion, int maxAttempts, List<String> allowedEventTypes, String idempotencyKeyLabel) {
      this(endpointUrl, secretRef, secretVersion, maxAttempts, allowedEventTypes, idempotencyKeyLabel, DEFAULT_BACKOFF, Duration.ZERO);
    }

    public WebhookEndpointPolicy {
      retryBackoffSchedule = retryBackoffSchedule == null || retryBackoffSchedule.isEmpty() ? DEFAULT_BACKOFF : List.copyOf(retryBackoffSchedule);
      retryJitterMax = retryJitterMax == null || retryJitterMax.isNegative() ? Duration.ZERO : retryJitterMax;
    }
  }

  public record SignedWebhookPayload(String signature, Map<String, String> headers) {}

  public record EnqueueWebhookDeliveryCommand(String tenantId, String subscriptionId, String sourceEventId, String eventType, String payloadBody, String actorId, String correlationId) {}

  public record ManualWebhookRetryCommand(String tenantId, String deliveryId, String actorId, String correlationId) {}

  public record SearchWebhookDeliveriesQuery(String tenantId, WebhookDeliveryStatus status, String eventType, String subscriptionId, Instant from, Instant to) {}

  public record WebhookDeliveryRequest(String tenantId, String deliveryId, String subscriptionId, String sourceEventId, String eventType, String endpointUrl, Map<String, String> headers, String payloadHash, String payloadBody, String correlationId) {}

  public record WebhookDeliveryHttpResponse(int statusCode, Duration responseTime, String responseBody, WebhookFailureClass transportFailure) {}

  public record WebhookDelivery(String tenantId, String deliveryId, String subscriptionId, String sourceEventId, String eventType, WebhookDeliveryStatus status, Instant nextAttemptAt, int attemptCount, String payloadHash, String signatureVersion, String lastFailureClass, int version, String actorId, String correlationId, Instant createdAt, Instant updatedAt) {}

  public record WebhookDeliveryAttempt(String attemptId, String tenantId, String deliveryId, Instant startedAt, Instant completedAt, int httpStatus, long responseTimeMs, String requestHeadersHash, String responseBodyHash, String failureClass, boolean retryable, boolean manual) {}

  public record WebhookDeliveryResponse(String id, String subscriptionId, String sourceEventId, String eventType, WebhookDeliveryStatus status, Instant nextAttemptAt, int attemptCount, String payloadHash, String signatureVersion, String failureClass, int version, String resultSummary, List<String> validationMessages, String auditRef, String replayRef, String correlationId) {}

  public record WebhookDeliveryError(String code, String reason, String message, List<String> fieldErrors, String correlationId, boolean retryable) {}

  public record WebhookDeliveryOutboxEvent(String eventId, String eventType, int schemaVersion, String tenantId, String actor, String correlationId, String deliveryId, String idempotencyKey, String payloadHash, Instant occurredAt, Map<String, String> payload) {}

  public record WebhookDeliveryAuditRecord(String auditId, String tenantId, String deliveryId, String actor, String action, String beforeHash, String afterHash, String correlationId, String replayHash, Instant occurredAt) {}

  public record WebhookDeliveryResult<T>(boolean valid, Optional<T> value, Optional<WebhookDeliveryError> error) {
    public static <T> WebhookDeliveryResult<T> success(T value) {
      return new WebhookDeliveryResult<>(true, Optional.of(value), Optional.empty());
    }

    public static <T> WebhookDeliveryResult<T> failure(WebhookDeliveryError error) {
      return new WebhookDeliveryResult<>(false, Optional.empty(), Optional.of(error));
    }
  }

  public static final class WebhookFailureClassifier {
    public WebhookFailureClass classify(WebhookDeliveryHttpResponse response) {
      if (response == null) {
        return WebhookFailureClass.DEPENDENCY_UNAVAILABLE;
      }
      if (response.transportFailure() != null && response.transportFailure() != WebhookFailureClass.NONE) {
        return response.transportFailure();
      }
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        return WebhookFailureClass.NONE;
      }
      if (response.statusCode() == 408 || response.statusCode() == 409 || response.statusCode() == 425 || response.statusCode() == 429) {
        return WebhookFailureClass.RETRYABLE_CLIENT_ERROR;
      }
      if (response.statusCode() >= 500 || response.statusCode() == 0) {
        return WebhookFailureClass.SERVER_ERROR;
      }
      return WebhookFailureClass.CLIENT_ERROR;
    }

    public boolean isRetryable(WebhookFailureClass failureClass, int statusCode) {
      return failureClass == WebhookFailureClass.RETRYABLE_CLIENT_ERROR
          || failureClass == WebhookFailureClass.SERVER_ERROR
          || failureClass == WebhookFailureClass.TIMEOUT
          || failureClass == WebhookFailureClass.TLS_FAILURE
          || failureClass == WebhookFailureClass.DEPENDENCY_UNAVAILABLE;
    }
  }

  public static final class WebhookRetryScheduler {
    private final List<Duration> backoffSchedule;
    private final Duration jitterMax;

    public WebhookRetryScheduler(List<Duration> backoffSchedule) {
      this(backoffSchedule, Duration.ZERO);
    }

    public WebhookRetryScheduler(List<Duration> backoffSchedule, Duration jitterMax) {
      this.backoffSchedule = backoffSchedule == null || backoffSchedule.isEmpty() ? DEFAULT_BACKOFF : List.copyOf(backoffSchedule);
      this.jitterMax = jitterMax == null || jitterMax.isNegative() ? Duration.ZERO : jitterMax;
    }

    public Instant nextAttemptAt(Instant completedAt, int completedAttemptCount) {
      return nextAttemptAt(completedAt, completedAttemptCount, "", "");
    }

    public Instant nextAttemptAt(Instant completedAt, int completedAttemptCount, String tenantId, String deliveryId) {
      int index = Math.max(0, Math.min(completedAttemptCount - 1, backoffSchedule.size() - 1));
      return completedAt.plus(backoffSchedule.get(index)).plus(deterministicJitter(completedAttemptCount, tenantId, deliveryId));
    }

    private Duration deterministicJitter(int completedAttemptCount, String tenantId, String deliveryId) {
      long maxMillis = jitterMax.toMillis();
      if (maxMillis < 1) {
        return Duration.ZERO;
      }
      int seed = java.util.Objects.hash(completedAttemptCount, tenantId == null ? "" : tenantId, deliveryId == null ? "" : deliveryId);
      return Duration.ofMillis(Math.floorMod(seed, maxMillis + 1));
    }
  }
}
