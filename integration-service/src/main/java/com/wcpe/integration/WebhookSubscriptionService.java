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
import java.util.Set;
import java.util.UUID;

public final class WebhookSubscriptionService {
  public static final String BASE_PATH = "/api/v1/tenants/{tenantId}/channels/{channelId}/webhook-subscriptions";
  public static final String WRITE_PERMISSION = "integrations.webhook.write";
  public static final String READ_PERMISSION = "integrations.webhook.read";
  public static final String CREATED_EVENT_TYPE = "integration.webhook-subscription.created.v1";
  public static final String UPDATED_EVENT_TYPE = "integration.webhook-subscription.updated.v1";
  public static final String PAUSED_EVENT_TYPE = "integration.webhook-subscription.paused.v1";
  public static final String SECRET_ROTATED_EVENT_TYPE = "integration.webhook-subscription.secret-rotated.v1";
  public static final String TEST_EVENT_TYPE = "integration.webhook-subscription.tested.v1";
  public static final String AUDIT_CREATED_ACTION = "WEBHOOK_SUBSCRIPTION_CREATED";
  public static final String AUDIT_UPDATED_ACTION = "WEBHOOK_SUBSCRIPTION_UPDATED";
  public static final String AUDIT_SECRET_ROTATED_ACTION = "WEBHOOK_SUBSCRIPTION_SECRET_ROTATED";
  public static final String AUDIT_TESTED_ACTION = "WEBHOOK_SUBSCRIPTION_TESTED";

  private final Clock clock;
  private final SigningSecretProvider secretProvider;
  private final DeliverySigner deliverySigner;
  private final Map<String, ChannelWebhookPolicy> channelPolicies = new HashMap<>();
  private final Map<String, WebhookSubscription> subscriptions = new HashMap<>();
  private final Map<String, String> activeEndpointEventIndex = new HashMap<>();
  private final Map<String, IdempotencyEntry> idempotencyEntries = new HashMap<>();
  private final Map<String, TestIdempotencyEntry> testIdempotencyEntries = new HashMap<>();
  private final List<WebhookOutboxEvent> outboxEvents = new ArrayList<>();
  private final List<WebhookAuditRecord> auditRecords = new ArrayList<>();
  private final List<WebhookTestDelivery> testDeliveries = new ArrayList<>();
  private final Map<String, Long> metrics = new HashMap<>();

  public WebhookSubscriptionService(SigningSecretProvider secretProvider, DeliverySigner deliverySigner) {
    this(Clock.systemUTC(), secretProvider, deliverySigner);
  }

  public WebhookSubscriptionService(Clock clock, SigningSecretProvider secretProvider, DeliverySigner deliverySigner) {
    this.clock = clock;
    this.secretProvider = secretProvider;
    this.deliverySigner = deliverySigner;
  }

  public void configureChannelPolicy(String tenantId, String channelId, ChannelWebhookPolicy policy) {
    channelPolicies.put(key(tenantId, channelId), policy);
  }

  public WebhookResult<WebhookSubscriptionResponse> create(CreateWebhookSubscriptionCommand command) {
    WebhookError validation = validateCreate(command);
    if (validation != null) {
      return WebhookResult.failure(validation);
    }

    String requestHash = hash(canonicalCreate(command));
    String idempotencyKey = command.tenantId() + ":" + command.channelId() + ":create:" + command.idempotencyKey();
    WebhookResult<WebhookSubscriptionResponse> replay = replayOrConflict(idempotencyKey, requestHash, command.correlationId());
    if (replay != null) {
      return replay;
    }

    Instant now = clock.instant();
    String subscriptionId = deterministicId(command.tenantId(), command.channelId(), command.endpointUrl(), requestHash);
    WebhookSubscription subscription =
        new WebhookSubscription(
            command.tenantId(),
            command.channelId(),
            subscriptionId,
            command.displayName(),
            normalizeEndpoint(command.endpointUrl()),
            hash(normalizeEndpoint(command.endpointUrl())),
            List.copyOf(command.eventTypes()),
            command.status(),
            command.signingSecretRef(),
            secretProvider.secretVersion(command.signingSecretRef()),
            command.retryPolicy(),
            0,
            null,
            null,
            1,
            command.actorId(),
            now,
            now,
            command.correlationId());

    if (subscription.status() == WebhookSubscriptionStatus.ACTIVE && activeEndpointEventIndex.containsKey(activeIndexKey(subscription))) {
      return WebhookResult.failure(error("409", "DUPLICATE_ACTIVE_ENDPOINT", "An active webhook subscription already exists for this endpoint and event set", command.correlationId(), false));
    }

    subscriptions.put(subscriptionKey(command.tenantId(), command.channelId(), subscriptionId), subscription);
    indexActive(subscription, null);
    writeOutboxAndAudit(CREATED_EVENT_TYPE, AUDIT_CREATED_ACTION, command.actorId(), command.idempotencyKey(), subscription, null, Map.of("changedFields", "created"));
    recordMetric("webhook_subscription_changes_total");
    WebhookSubscriptionResponse response = response(subscription, "created", List.of());
    idempotencyEntries.put(idempotencyKey, new IdempotencyEntry(requestHash, response));
    return WebhookResult.success(response);
  }

  public WebhookResult<WebhookSubscriptionResponse> update(UpdateWebhookSubscriptionCommand command) {
    WebhookError validation = validateUpdate(command);
    if (validation != null) {
      return WebhookResult.failure(validation);
    }
    String requestHash = hash(canonicalUpdate(command));
    String idempotencyKey = command.tenantId() + ":" + command.channelId() + ":" + command.subscriptionId() + ":update:" + command.idempotencyKey();
    WebhookResult<WebhookSubscriptionResponse> replay = replayOrConflict(idempotencyKey, requestHash, command.correlationId());
    if (replay != null) {
      return replay;
    }

    WebhookSubscription existing = subscriptions.get(subscriptionKey(command.tenantId(), command.channelId(), command.subscriptionId()));
    if (existing == null || existing.status() == WebhookSubscriptionStatus.DELETED) {
      return WebhookResult.failure(error("404", "NOT_FOUND", "Webhook subscription was not found", command.correlationId(), false));
    }
    if (command.expectedVersion() != existing.version()) {
      return WebhookResult.failure(error("409", "VERSION_CONFLICT", "Webhook subscription version does not match", command.correlationId(), false));
    }
    if (!isAllowedTransition(existing.status(), command.status())) {
      return WebhookResult.failure(error("422", "POLICY_NOT_SATISFIED", "Unsupported webhook subscription status transition", command.correlationId(), false));
    }

    WebhookSubscription updated =
        new WebhookSubscription(
            existing.tenantId(),
            existing.channelId(),
            existing.subscriptionId(),
            command.displayName(),
            normalizeEndpoint(command.endpointUrl()),
            hash(normalizeEndpoint(command.endpointUrl())),
            List.copyOf(command.eventTypes()),
            command.status(),
            existing.signingSecretRef(),
            existing.secretVersion(),
            command.retryPolicy(),
            existing.failureCount(),
            existing.lastSuccessAt(),
            existing.lastFailureAt(),
            existing.version() + 1,
            command.actorId(),
            existing.createdAt(),
            clock.instant(),
            command.correlationId());

    String duplicateId = activeEndpointEventIndex.get(activeIndexKey(updated));
    if (updated.status() == WebhookSubscriptionStatus.ACTIVE && duplicateId != null && !duplicateId.equals(updated.subscriptionId())) {
      return WebhookResult.failure(error("409", "DUPLICATE_ACTIVE_ENDPOINT", "An active webhook subscription already exists for this endpoint and event set", command.correlationId(), false));
    }

    subscriptions.put(subscriptionKey(updated.tenantId(), updated.channelId(), updated.subscriptionId()), updated);
    indexActive(updated, existing);
    String eventType = updated.status() == WebhookSubscriptionStatus.PAUSED ? PAUSED_EVENT_TYPE : UPDATED_EVENT_TYPE;
    String auditAction = updated.status() == WebhookSubscriptionStatus.PAUSED ? AUDIT_UPDATED_ACTION : AUDIT_UPDATED_ACTION;
    writeOutboxAndAudit(eventType, auditAction, command.actorId(), command.idempotencyKey(), updated, existing, Map.of("changedFields", String.join(",", changedFields(existing, updated))));
    recordMetric("webhook_subscription_changes_total");
    WebhookSubscriptionResponse response = response(updated, "updated", List.of());
    idempotencyEntries.put(idempotencyKey, new IdempotencyEntry(requestHash, response));
    return WebhookResult.success(response);
  }

  public WebhookResult<WebhookSubscriptionResponse> rotateSecret(RotateWebhookSecretCommand command) {
    WebhookError common = validateMutationCommon(command.tenantId(), command.channelId(), command.subscriptionId(), command.idempotencyKey(), command.actorId(), command.correlationId(), command.expectedVersion());
    if (common != null) {
      return WebhookResult.failure(common);
    }
    if (isBlank(command.newSigningSecretRef())) {
      return WebhookResult.failure(error("400", "VALIDATION_FAILED", "newSigningSecretRef is required", command.correlationId(), false));
    }
    if (!secretProvider.hasSecret(command.newSigningSecretRef())) {
      return WebhookResult.failure(error("422", "POLICY_NOT_SATISFIED", "Signing secret reference is not available", command.correlationId(), false));
    }

    String requestHash = hash(String.join("|", command.tenantId(), command.channelId(), command.subscriptionId(), command.newSigningSecretRef(), command.correlationId()));
    String idempotencyKey = command.tenantId() + ":" + command.channelId() + ":" + command.subscriptionId() + ":rotate:" + command.idempotencyKey();
    WebhookResult<WebhookSubscriptionResponse> replay = replayOrConflict(idempotencyKey, requestHash, command.correlationId());
    if (replay != null) {
      return replay;
    }

    WebhookSubscription existing = subscriptions.get(subscriptionKey(command.tenantId(), command.channelId(), command.subscriptionId()));
    if (existing == null || existing.status() == WebhookSubscriptionStatus.DELETED) {
      return WebhookResult.failure(error("404", "NOT_FOUND", "Webhook subscription was not found", command.correlationId(), false));
    }
    if (existing.version() != command.expectedVersion()) {
      return WebhookResult.failure(error("409", "VERSION_CONFLICT", "Webhook subscription version does not match", command.correlationId(), false));
    }
    WebhookSubscription rotated =
        new WebhookSubscription(
            existing.tenantId(),
            existing.channelId(),
            existing.subscriptionId(),
            existing.displayName(),
            existing.endpointUrl(),
            existing.endpointUrlHash(),
            existing.eventTypes(),
            existing.status(),
            command.newSigningSecretRef(),
            secretProvider.secretVersion(command.newSigningSecretRef()),
            existing.retryPolicy(),
            existing.failureCount(),
            existing.lastSuccessAt(),
            existing.lastFailureAt(),
            existing.version() + 1,
            command.actorId(),
            existing.createdAt(),
            clock.instant(),
            command.correlationId());
    subscriptions.put(subscriptionKey(rotated.tenantId(), rotated.channelId(), rotated.subscriptionId()), rotated);
    writeOutboxAndAudit(SECRET_ROTATED_EVENT_TYPE, AUDIT_SECRET_ROTATED_ACTION, command.actorId(), command.idempotencyKey(), rotated, existing, Map.of("changedFields", "signingSecretRef"));
    recordMetric("webhook_subscription_changes_total");
    WebhookSubscriptionResponse response = response(rotated, "secret rotated", List.of());
    idempotencyEntries.put(idempotencyKey, new IdempotencyEntry(requestHash, response));
    return WebhookResult.success(response);
  }

  public WebhookResult<WebhookTestResponse> test(TestWebhookSubscriptionCommand command) {
    WebhookError common = validateMutationCommon(command.tenantId(), command.channelId(), command.subscriptionId(), command.idempotencyKey(), command.actorId(), command.correlationId(), 1);
    if (common != null) {
      return WebhookResult.failure(common);
    }
    WebhookSubscription subscription = subscriptions.get(subscriptionKey(command.tenantId(), command.channelId(), command.subscriptionId()));
    if (subscription == null || subscription.status() == WebhookSubscriptionStatus.DELETED) {
      return WebhookResult.failure(error("404", "NOT_FOUND", "Webhook subscription was not found", command.correlationId(), false));
    }
    if (subscription.status() != WebhookSubscriptionStatus.ACTIVE) {
      return WebhookResult.failure(error("422", "POLICY_NOT_SATISFIED", "Only active webhook subscriptions can receive a test event", command.correlationId(), false));
    }

    String requestHash = hash(String.join("|", command.tenantId(), command.channelId(), command.subscriptionId(), command.correlationId()));
    String idempotencyKey = command.tenantId() + ":" + command.channelId() + ":" + command.subscriptionId() + ":test:" + command.idempotencyKey();
    TestIdempotencyEntry existing = testIdempotencyEntries.get(idempotencyKey);
    if (existing != null) {
      if (!existing.requestHash().equals(requestHash)) {
        return WebhookResult.failure(error("409", "IDEMPOTENCY_CONFLICT", "Idempotency key was reused with a different webhook test request", command.correlationId(), false));
      }
      return WebhookResult.success(existing.response());
    }

    String body = "{\"subscriptionId\":\"" + subscription.subscriptionId() + "\",\"test\":true}";
    String timestamp = clock.instant().toString();
    SignedWebhookEnvelope envelope = deliverySigner.sign(subscription.signingSecretRef(), timestamp, body);
    WebhookTestDelivery delivery =
        new WebhookTestDelivery(
            deterministicId(subscription.tenantId(), subscription.subscriptionId(), "test", String.valueOf(testDeliveries.size() + 1)),
            TEST_EVENT_TYPE,
            subscription.tenantId(),
            subscription.channelId(),
            subscription.subscriptionId(),
            subscription.endpointUrlHash(),
            envelope.signature(),
            timestamp,
            command.correlationId(),
            clock.instant());
    testDeliveries.add(delivery);
    writeOutboxAndAudit(TEST_EVENT_TYPE, AUDIT_TESTED_ACTION, command.actorId(), command.idempotencyKey(), subscription, subscription, Map.of("testDeliveryId", delivery.deliveryId(), "endpointUrlHash", subscription.endpointUrlHash()));
    recordMetric("webhook_test_deliveries_total");
    WebhookTestResponse response = new WebhookTestResponse(delivery.deliveryId(), TEST_EVENT_TYPE, subscription.secretVersion(), delivery.signature(), delivery.correlationId());
    testIdempotencyEntries.put(idempotencyKey, new TestIdempotencyEntry(requestHash, response));
    return WebhookResult.success(response);
  }

  public WebhookResult<WebhookSubscriptionResponse> fetch(String tenantId, String channelId, String subscriptionId, String correlationId) {
    if (!isUuid(tenantId) || isBlank(channelId) || isBlank(subscriptionId)) {
      return WebhookResult.failure(error("400", "VALIDATION_FAILED", "tenantId, channelId, and subscriptionId are required", correlationId, false));
    }
    WebhookSubscription subscription = subscriptions.get(subscriptionKey(tenantId, channelId, subscriptionId));
    if (subscription == null || subscription.status() == WebhookSubscriptionStatus.DELETED) {
      return WebhookResult.failure(error("404", "NOT_FOUND", "Webhook subscription was not found", correlationId, false));
    }
    return WebhookResult.success(response(subscription, "fetched", List.of()));
  }

  public List<WebhookSubscription> subscriptionsForTenant(String tenantId) {
    return subscriptions.values().stream()
        .filter(subscription -> subscription.tenantId().equals(tenantId))
        .filter(subscription -> subscription.status() != WebhookSubscriptionStatus.DELETED)
        .sorted(Comparator.comparing(WebhookSubscription::createdAt))
        .toList();
  }

  public List<WebhookOutboxEvent> outboxEvents() {
    return List.copyOf(outboxEvents);
  }

  public List<WebhookAuditRecord> auditRecords() {
    return List.copyOf(auditRecords);
  }

  public List<WebhookTestDelivery> testDeliveries() {
    return List.copyOf(testDeliveries);
  }

  public Map<String, Long> metrics() {
    Map<String, Long> copy = new HashMap<>(metrics);
    copy.put("webhook_subscriptions_active", subscriptions.values().stream().filter(subscription -> subscription.status() == WebhookSubscriptionStatus.ACTIVE).count());
    return Map.copyOf(copy);
  }

  private WebhookError validateCreate(CreateWebhookSubscriptionCommand command) {
    if (command == null) {
      return error("400", "VALIDATION_FAILED", "command is required", "", false);
    }
    WebhookError common = validateCommandCommon(command.tenantId(), command.channelId(), command.idempotencyKey(), command.actorId(), command.correlationId());
    if (common != null) {
      return common;
    }
    if (isBlank(command.displayName())) {
      return error("400", "VALIDATION_FAILED", "displayName is required", command.correlationId(), false);
    }
    if (command.status() == null || command.status() == WebhookSubscriptionStatus.DELETED || command.status() == WebhookSubscriptionStatus.FAILED_VALIDATION) {
      return error("400", "VALIDATION_FAILED", "Initial status must be DRAFT, ACTIVE, or PAUSED", command.correlationId(), false);
    }
    WebhookError policy = validatePolicyAndFields(command.tenantId(), command.channelId(), command.endpointUrl(), command.eventTypes(), command.retryPolicy(), command.signingSecretRef(), command.allowLocalDevEndpoint(), command.correlationId());
    if (policy != null) {
      return policy;
    }
    return null;
  }

  private WebhookError validateUpdate(UpdateWebhookSubscriptionCommand command) {
    if (command == null) {
      return error("400", "VALIDATION_FAILED", "command is required", "", false);
    }
    WebhookError common = validateMutationCommon(command.tenantId(), command.channelId(), command.subscriptionId(), command.idempotencyKey(), command.actorId(), command.correlationId(), command.expectedVersion());
    if (common != null) {
      return common;
    }
    if (isBlank(command.displayName())) {
      return error("400", "VALIDATION_FAILED", "displayName is required", command.correlationId(), false);
    }
    if (command.status() == null) {
      return error("400", "VALIDATION_FAILED", "status is required", command.correlationId(), false);
    }
    return validatePolicyAndFields(command.tenantId(), command.channelId(), command.endpointUrl(), command.eventTypes(), command.retryPolicy(), "existing-secret-ref", command.allowLocalDevEndpoint(), command.correlationId(), true);
  }

  private WebhookError validateCommandCommon(String tenantId, String channelId, String idempotencyKey, String actorId, String correlationId) {
    if (!isUuid(tenantId)) {
      return error("400", "VALIDATION_FAILED", "tenantId must be a UUID", correlationId, false);
    }
    if (isBlank(channelId)) {
      return error("400", "VALIDATION_FAILED", "channelId is required", correlationId, false);
    }
    if (isBlank(actorId)) {
      return error("401", "UNAUTHENTICATED", "actorId is required", correlationId, false);
    }
    if (isBlank(idempotencyKey)) {
      return error("400", "VALIDATION_FAILED", "Idempotency-Key is required", correlationId, false);
    }
    if (isBlank(correlationId)) {
      return error("400", "VALIDATION_FAILED", "correlationId is required", "", false);
    }
    return null;
  }

  private WebhookError validateMutationCommon(String tenantId, String channelId, String subscriptionId, String idempotencyKey, String actorId, String correlationId, int expectedVersion) {
    WebhookError common = validateCommandCommon(tenantId, channelId, idempotencyKey, actorId, correlationId);
    if (common != null) {
      return common;
    }
    if (isBlank(subscriptionId)) {
      return error("400", "VALIDATION_FAILED", "subscriptionId is required", correlationId, false);
    }
    if (expectedVersion < 1) {
      return error("400", "VALIDATION_FAILED", "expectedVersion is required", correlationId, false);
    }
    return null;
  }

  private WebhookError validatePolicyAndFields(String tenantId, String channelId, String endpointUrl, List<String> eventTypes, WebhookRetryPolicy retryPolicy, String signingSecretRef, boolean allowLocalDevEndpoint, String correlationId) {
    return validatePolicyAndFields(tenantId, channelId, endpointUrl, eventTypes, retryPolicy, signingSecretRef, allowLocalDevEndpoint, correlationId, false);
  }

  private WebhookError validatePolicyAndFields(String tenantId, String channelId, String endpointUrl, List<String> eventTypes, WebhookRetryPolicy retryPolicy, String signingSecretRef, boolean allowLocalDevEndpoint, String correlationId, boolean skipSecretLookup) {
    ChannelWebhookPolicy policy = channelPolicies.get(key(tenantId, channelId));
    if (policy == null || policy.allowedEventTypes() == null || policy.allowedEventTypes().isEmpty() || policy.retryBounds() == null) {
      return error("422", "POLICY_NOT_SATISFIED", "Tenant channel webhook policy configuration is required", correlationId, false);
    }
    WebhookError endpoint = validateEndpoint(endpointUrl, allowLocalDevEndpoint, correlationId);
    if (endpoint != null) {
      return endpoint;
    }
    if (eventTypes == null || eventTypes.isEmpty() || eventTypes.stream().anyMatch(this::isBlank)) {
      return error("400", "VALIDATION_FAILED", "At least one event type is required", correlationId, false);
    }
    Set<String> allowed = Set.copyOf(policy.allowedEventTypes());
    if (!allowed.containsAll(eventTypes)) {
      return error("422", "POLICY_NOT_SATISFIED", "Webhook event types must be present in tenant-governed allowlist", correlationId, false);
    }
    if (!skipSecretLookup && (isBlank(signingSecretRef) || !secretProvider.hasSecret(signingSecretRef))) {
      return error("422", "POLICY_NOT_SATISFIED", "Signing secret reference is not available", correlationId, false);
    }
    if (retryPolicy == null) {
      return error("400", "VALIDATION_FAILED", "retryPolicy is required", correlationId, false);
    }
    RetryPolicyBounds bounds = policy.retryBounds();
    if (retryPolicy.maxAttempts() < bounds.minAttempts() || retryPolicy.maxAttempts() > bounds.maxAttempts()) {
      return error("422", "POLICY_NOT_SATISFIED", "retryPolicy maxAttempts is outside tenant-governed bounds", correlationId, false);
    }
    if (retryPolicy.initialBackoff().compareTo(bounds.minInitialBackoff()) < 0 || retryPolicy.initialBackoff().compareTo(bounds.maxInitialBackoff()) > 0) {
      return error("422", "POLICY_NOT_SATISFIED", "retryPolicy initialBackoff is outside tenant-governed bounds", correlationId, false);
    }
    if (retryPolicy.timeout().isNegative() || retryPolicy.timeout().isZero() || retryPolicy.timeout().compareTo(bounds.maxTimeout()) > 0) {
      return error("422", "POLICY_NOT_SATISFIED", "retryPolicy timeout is outside tenant-governed bounds", correlationId, false);
    }
    return null;
  }

  private WebhookError validateEndpoint(String endpointUrl, boolean allowLocalDevEndpoint, String correlationId) {
    if (isBlank(endpointUrl)) {
      return error("400", "VALIDATION_FAILED", "endpointUrl is required", correlationId, false);
    }
    URI uri;
    try {
      uri = new URI(endpointUrl);
    } catch (URISyntaxException exception) {
      return error("400", "VALIDATION_FAILED", "endpointUrl must be a valid URI", correlationId, false);
    }
    String scheme = uri.getScheme();
    String host = uri.getHost();
    if (isBlank(scheme) || isBlank(host)) {
      return error("400", "VALIDATION_FAILED", "endpointUrl requires scheme and host", correlationId, false);
    }
    if (!"https".equalsIgnoreCase(scheme) && !(allowLocalDevEndpoint && "http".equalsIgnoreCase(scheme) && isLocalDevHost(host))) {
      return error("422", "POLICY_NOT_SATISFIED", "Webhook endpoint must use HTTPS except explicit local dev endpoints", correlationId, false);
    }
    if (isBlockedHost(host) && !allowLocalDevEndpoint) {
      return error("422", "POLICY_NOT_SATISFIED", "Webhook endpoint host is blocked by SSRF policy", correlationId, false);
    }
    return null;
  }

  private WebhookResult<WebhookSubscriptionResponse> replayOrConflict(String idempotencyKey, String requestHash, String correlationId) {
    IdempotencyEntry existing = idempotencyEntries.get(idempotencyKey);
    if (existing == null) {
      return null;
    }
    if (!existing.requestHash().equals(requestHash)) {
      return WebhookResult.failure(error("409", "IDEMPOTENCY_CONFLICT", "Idempotency key was reused with a different webhook subscription body", correlationId, false));
    }
    return WebhookResult.success(existing.response());
  }

  private void writeOutboxAndAudit(String eventType, String auditAction, String actorId, String idempotencyKey, WebhookSubscription after, WebhookSubscription before, Map<String, String> eventDetails) {
    String eventId = deterministicId(after.tenantId(), after.subscriptionId(), eventType, String.valueOf(outboxEvents.size() + 1));
    String payloadHash = hash(after.endpointUrlHash() + ":" + after.status() + ":" + after.secretVersion() + ":" + canonicalList(after.eventTypes()));
    Map<String, String> payload = new HashMap<>();
    payload.put("subscriptionId", after.subscriptionId());
    payload.put("tenantId", after.tenantId());
    payload.put("channelId", after.channelId());
    payload.put("endpointUrlHash", after.endpointUrlHash());
    payload.put("eventTypes", canonicalList(after.eventTypes()));
    payload.put("status", after.status().name());
    payload.put("retryPolicyHash", hash(after.retryPolicy().toString()));
    payload.put("secretVersion", after.secretVersion());
    payload.put("actor", actorId);
    payload.putAll(eventDetails);
    outboxEvents.add(new WebhookOutboxEvent(eventId, eventType, 1, after.tenantId(), after.channelId(), after.subscriptionId(), actorId, after.correlationId(), after.correlationId(), idempotencyKey, payloadHash, after.updatedAt(), Map.copyOf(payload)));
    auditRecords.add(new WebhookAuditRecord(deterministicId(after.tenantId(), after.subscriptionId(), auditAction, String.valueOf(auditRecords.size() + 1)), after.tenantId(), after.channelId(), after.subscriptionId(), actorId, auditAction, before == null ? "" : hash(before.status() + ":" + before.version() + ":" + before.endpointUrlHash()), hash(after.status() + ":" + after.version() + ":" + after.endpointUrlHash()), after.correlationId(), payloadHash, after.updatedAt()));
  }

  private WebhookSubscriptionResponse response(WebhookSubscription subscription, String summary, List<String> validationMessages) {
    return new WebhookSubscriptionResponse(subscription.subscriptionId(), subscription.status(), subscription.version(), summary, List.copyOf(validationMessages), "audit:" + subscription.tenantId() + ":" + subscription.subscriptionId() + ":" + subscription.version(), "event:" + subscription.tenantId() + ":" + subscription.subscriptionId() + ":" + subscription.version(), subscription.secretVersion(), subscription.updatedAt(), subscription.correlationId());
  }

  private List<String> changedFields(WebhookSubscription before, WebhookSubscription after) {
    List<String> changed = new ArrayList<>();
    if (!before.displayName().equals(after.displayName())) {
      changed.add("displayName");
    }
    if (!before.endpointUrlHash().equals(after.endpointUrlHash())) {
      changed.add("endpointUrlHash");
    }
    if (!before.eventTypes().equals(after.eventTypes())) {
      changed.add("eventTypes");
    }
    if (before.status() != after.status()) {
      changed.add("status");
    }
    if (!before.retryPolicy().equals(after.retryPolicy())) {
      changed.add("retryPolicy");
    }
    return changed.isEmpty() ? List.of("version") : changed;
  }

  private void indexActive(WebhookSubscription after, WebhookSubscription before) {
    if (before != null) {
      activeEndpointEventIndex.remove(activeIndexKey(before));
    }
    if (after.status() == WebhookSubscriptionStatus.ACTIVE) {
      activeEndpointEventIndex.put(activeIndexKey(after), after.subscriptionId());
    }
  }

  private String activeIndexKey(WebhookSubscription subscription) {
    return subscription.tenantId() + ":" + subscription.channelId() + ":" + subscription.endpointUrlHash() + ":" + canonicalList(subscription.eventTypes());
  }

  private boolean isAllowedTransition(WebhookSubscriptionStatus from, WebhookSubscriptionStatus to) {
    return switch (from) {
      case DRAFT -> to == WebhookSubscriptionStatus.ACTIVE || to == WebhookSubscriptionStatus.PAUSED || to == WebhookSubscriptionStatus.DELETED;
      case ACTIVE -> to == WebhookSubscriptionStatus.PAUSED || to == WebhookSubscriptionStatus.FAILED_VALIDATION || to == WebhookSubscriptionStatus.DELETED || to == WebhookSubscriptionStatus.ACTIVE;
      case PAUSED, FAILED_VALIDATION -> to == WebhookSubscriptionStatus.ACTIVE || to == WebhookSubscriptionStatus.DELETED || to == WebhookSubscriptionStatus.PAUSED;
      case DELETED -> false;
    };
  }

  private boolean isBlockedHost(String host) {
    String normalized = host.toLowerCase(Locale.ROOT);
    return isLocalDevHost(normalized)
        || normalized.equals("metadata.google.internal")
        || normalized.startsWith("169.254.")
        || normalized.startsWith("127.")
        || normalized.startsWith("10.")
        || normalized.startsWith("192.168.")
        || is172Private(normalized)
        || normalized.equals("0.0.0.0")
        || normalized.equals("::1");
  }

  private boolean isLocalDevHost(String host) {
    String normalized = host.toLowerCase(Locale.ROOT);
    return normalized.equals("localhost") || normalized.equals("127.0.0.1") || normalized.equals("::1");
  }

  private boolean is172Private(String host) {
    if (!host.startsWith("172.")) {
      return false;
    }
    String[] parts = host.split("\\.");
    if (parts.length < 2) {
      return false;
    }
    try {
      int second = Integer.parseInt(parts[1]);
      return second >= 16 && second <= 31;
    } catch (NumberFormatException exception) {
      return false;
    }
  }

  private String normalizeEndpoint(String endpointUrl) {
    URI uri = URI.create(endpointUrl);
    String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
    String host = uri.getHost().toLowerCase(Locale.ROOT);
    int port = uri.getPort();
    String path = uri.getRawPath() == null || uri.getRawPath().isBlank() ? "/" : uri.getRawPath();
    return scheme + "://" + host + (port > -1 ? ":" + port : "") + path;
  }

  private String canonicalCreate(CreateWebhookSubscriptionCommand command) {
    return String.join("|", command.tenantId(), command.channelId(), command.idempotencyKey(), command.actorId(), command.displayName(), normalizeEndpoint(command.endpointUrl()), canonicalList(command.eventTypes()), command.status().name(), command.signingSecretRef(), command.retryPolicy().toString(), command.correlationId());
  }

  private String canonicalUpdate(UpdateWebhookSubscriptionCommand command) {
    return String.join("|", command.tenantId(), command.channelId(), command.subscriptionId(), command.idempotencyKey(), command.actorId(), command.displayName(), normalizeEndpoint(command.endpointUrl()), canonicalList(command.eventTypes()), command.status().name(), String.valueOf(command.expectedVersion()), command.retryPolicy().toString(), command.correlationId());
  }

  private String key(String tenantId, String channelId) {
    return tenantId + ":" + channelId;
  }

  private String subscriptionKey(String tenantId, String channelId, String subscriptionId) {
    return tenantId + ":" + channelId + ":" + subscriptionId;
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

  private String canonicalList(List<String> values) {
    return values.stream().sorted().reduce((left, right) -> left + "," + right).orElse("");
  }

  private String hash(String value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is required", exception);
    }
  }

  private WebhookError error(String code, String reason, String message, String correlationId, boolean retryable) {
    return new WebhookError(code, reason, message, List.of(), correlationId == null ? "" : correlationId, retryable);
  }

  private void recordMetric(String metric) {
    metrics.put(metric, metrics.getOrDefault(metric, 0L) + 1L);
  }

  private record IdempotencyEntry(String requestHash, WebhookSubscriptionResponse response) {}

  private record TestIdempotencyEntry(String requestHash, WebhookTestResponse response) {}

  public enum WebhookSubscriptionStatus {
    DRAFT,
    ACTIVE,
    PAUSED,
    FAILED_VALIDATION,
    DELETED
  }

  public interface SigningSecretProvider {
    boolean hasSecret(String secretRef);

    String secretVersion(String secretRef);
  }

  public interface DeliverySigner {
    SignedWebhookEnvelope sign(String secretRef, String timestamp, String body);
  }

  public record CreateWebhookSubscriptionCommand(
      String tenantId,
      String channelId,
      String idempotencyKey,
      String actorId,
      String displayName,
      String endpointUrl,
      List<String> eventTypes,
      WebhookSubscriptionStatus status,
      WebhookRetryPolicy retryPolicy,
      String signingSecretRef,
      boolean allowLocalDevEndpoint,
      String correlationId) {}

  public record UpdateWebhookSubscriptionCommand(
      String tenantId,
      String channelId,
      String subscriptionId,
      String idempotencyKey,
      String actorId,
      String displayName,
      String endpointUrl,
      List<String> eventTypes,
      WebhookSubscriptionStatus status,
      WebhookRetryPolicy retryPolicy,
      int expectedVersion,
      boolean allowLocalDevEndpoint,
      String correlationId) {}

  public record RotateWebhookSecretCommand(
      String tenantId,
      String channelId,
      String subscriptionId,
      String idempotencyKey,
      String actorId,
      int expectedVersion,
      String newSigningSecretRef,
      String correlationId) {}

  public record TestWebhookSubscriptionCommand(
      String tenantId,
      String channelId,
      String subscriptionId,
      String idempotencyKey,
      String actorId,
      String correlationId) {}

  public record ChannelWebhookPolicy(List<String> allowedEventTypes, RetryPolicyBounds retryBounds) {}

  public record RetryPolicyBounds(int minAttempts, int maxAttempts, Duration minInitialBackoff, Duration maxInitialBackoff, Duration maxTimeout) {}

  public record WebhookRetryPolicy(int maxAttempts, Duration initialBackoff, Duration maxBackoff, Duration timeout) {}

  public record SignedWebhookEnvelope(String signature, Map<String, String> headers) {}

  public record WebhookSubscription(
      String tenantId,
      String channelId,
      String subscriptionId,
      String displayName,
      String endpointUrl,
      String endpointUrlHash,
      List<String> eventTypes,
      WebhookSubscriptionStatus status,
      String signingSecretRef,
      String secretVersion,
      WebhookRetryPolicy retryPolicy,
      int failureCount,
      Instant lastSuccessAt,
      Instant lastFailureAt,
      int version,
      String actorId,
      Instant createdAt,
      Instant updatedAt,
      String correlationId) {}

  public record WebhookSubscriptionResponse(
      String id,
      WebhookSubscriptionStatus status,
      int version,
      String resultSummary,
      List<String> validationMessages,
      String auditRef,
      String replayRef,
      String secretVersion,
      Instant lastValidatedAt,
      String correlationId) {}

  public record WebhookTestResponse(String deliveryId, String eventType, String secretVersion, String signature, String correlationId) {}

  public record WebhookError(String code, String reason, String message, List<String> fieldErrors, String correlationId, boolean retryable) {}

  public record WebhookResult<T>(boolean valid, Optional<T> value, Optional<WebhookError> error) {
    public static <T> WebhookResult<T> success(T value) {
      return new WebhookResult<>(true, Optional.of(value), Optional.empty());
    }

    public static <T> WebhookResult<T> failure(WebhookError error) {
      return new WebhookResult<>(false, Optional.empty(), Optional.of(error));
    }
  }

  public record WebhookOutboxEvent(
      String eventId,
      String eventType,
      int schemaVersion,
      String tenantId,
      String channelId,
      String subscriptionId,
      String actor,
      String correlationId,
      String causationId,
      String idempotencyKey,
      String payloadHash,
      Instant occurredAt,
      Map<String, String> payload) {}

  public record WebhookAuditRecord(
      String auditId,
      String tenantId,
      String channelId,
      String subscriptionId,
      String actor,
      String action,
      String beforeHash,
      String afterHash,
      String correlationId,
      String replayHash,
      Instant occurredAt) {}

  public record WebhookTestDelivery(
      String deliveryId,
      String eventType,
      String tenantId,
      String channelId,
      String subscriptionId,
      String endpointUrlHash,
      String signature,
      String timestamp,
      String correlationId,
      Instant queuedAt) {}
}
