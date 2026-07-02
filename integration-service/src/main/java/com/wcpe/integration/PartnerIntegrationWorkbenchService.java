package com.wcpe.integration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class PartnerIntegrationWorkbenchService {
  public static final String QUOTES_PATH = "/api/v1/partners/{partnerId}/quotes";
  public static final String QUOTE_DETAIL_PATH = "/api/v1/partners/{partnerId}/quotes/{quoteId}";
  public static final String REPRICE_PATH = "/api/v1/partners/{partnerId}/quotes/{quoteId}/reprice";
  public static final String WEBHOOK_HEALTH_PATH = "/api/v1/partners/{partnerId}/integrations/webhooks";
  public static final String WORKBENCH_PATH = "/api/v1/partners/{partnerId}/integrations/workbench";
  public static final String WEBHOOK_REPLAY_PATH = "/api/v1/partners/{partnerId}/integrations/webhooks/{webhookId}/replay";
  public static final String WEBHOOK_TEST_PATH = "/api/v1/partners/{partnerId}/integrations/webhooks/{webhookId}/test";
  public static final String WEBHOOK_SAFETY_PATH = "/api/v1/partners/{partnerId}/integrations/webhooks/{webhookId}/safety";
  public static final List<String> REQUIRED_RESPONSE_METADATA = List.of("auditRefs", "replayHash", "versionRefs", "dependencyStatus", "correlationId");

  private static final List<WorkbenchTab> DEFAULT_TABS =
      List.of(
          new WorkbenchTab("quotes", "Partner quotes", "quote-ops"),
          new WorkbenchTab("webhooks", "Webhook delivery", "integration-ops"),
          new WorkbenchTab("dlq", "Dead-letter queue", "integration-ops"),
          new WorkbenchTab("credentials", "Credential posture", "security-ops"),
          new WorkbenchTab("audit", "Audit and replay", "audit-ops"),
          new WorkbenchTab("alerts", "Safety alerts", "partner-ops"));

  private final Clock clock;
  private final Map<String, PartnerQuote> quotes = new HashMap<>();
  private final Map<String, WebhookIntegration> webhooks = new HashMap<>();
  private final Map<String, ActionResult> idempotentReplayResults = new HashMap<>();
  private final List<PartnerIntegrationEvent> outboxEvents = new ArrayList<>();

  public PartnerIntegrationWorkbenchService() {
    this(Clock.systemUTC());
  }

  public PartnerIntegrationWorkbenchService(Clock clock) {
    this.clock = clock;
  }

  public void upsertQuote(PartnerQuote quote) {
    if (quote != null && !isBlank(quote.partnerId()) && !isBlank(quote.quoteId())) {
      quotes.put(quoteKey(quote.partnerId(), quote.quoteId()), quote);
    }
  }

  public void upsertWebhook(WebhookIntegration webhook) {
    if (webhook != null && !isBlank(webhook.partnerId()) && !isBlank(webhook.webhookId())) {
      webhooks.put(webhookKey(webhook.partnerId(), webhook.webhookId()), webhook);
    }
  }

  public PartnerResult<List<PartnerQuoteListItem>> quotes(String partnerId, QuoteStatus status, String correlationId) {
    if (isBlank(partnerId)) {
      return PartnerResult.failure(error("400", "VALIDATION_FAILED", "partnerId is required", correlationId));
    }
    List<PartnerQuoteListItem> list =
        quotes.values().stream()
            .filter(quote -> quote.partnerId().equals(partnerId))
            .filter(quote -> status == null || quote.status() == status)
            .sorted(Comparator.comparing(PartnerQuote::updatedAt).reversed().thenComparing(PartnerQuote::quoteId))
            .map(quote -> new PartnerQuoteListItem(quote.quoteId(), quote.status(), quote.slaState(), quote.lockState(), quote.errorFlags(), metadata(partnerId, quote.quoteId(), correlationId, dependencyStatus(quote))))
            .toList();
    return PartnerResult.success(list);
  }

  public PartnerResult<PartnerQuoteDetail> quoteDetail(String partnerId, String quoteId, String correlationId) {
    if (isBlank(partnerId) || isBlank(quoteId)) {
      return PartnerResult.failure(error("400", "VALIDATION_FAILED", "partnerId and quoteId are required", correlationId));
    }
    PartnerQuote quote = quotes.get(quoteKey(partnerId, quoteId));
    if (quote == null) {
      return PartnerResult.failure(error("404", "NOT_FOUND", "Partner quote was not found", correlationId));
    }
    return PartnerResult.success(new PartnerQuoteDetail(quote.quoteId(), quote.status(), quote.lifecycle(), quote.slaState(), quote.lockState(), quote.errorFlags(), quote.repriceAvailable(), quote.repriceGuidance(), metadata(partnerId, quoteId, correlationId, dependencyStatus(quote))));
  }

  public PartnerResult<PartnerRepriceResult> reprice(RepriceCommand command) {
    PartnerError validation = validateAction(command == null ? null : command.partnerId(), command == null ? null : command.quoteId(), command == null ? null : command.actorId(), command == null ? null : command.correlationId());
    if (validation != null) {
      return PartnerResult.failure(validation);
    }
    PartnerQuote quote = quotes.get(quoteKey(command.partnerId(), command.quoteId()));
    if (quote == null) {
      return PartnerResult.failure(error("404", "NOT_FOUND", "Partner quote was not found", command.correlationId()));
    }
    DependencyStatus dependencies = dependencyStatus(quote);
    boolean accepted = quote.repriceAvailable() && dependencies.allAvailable();
    String reason = accepted ? "REPRICE_REQUEST_ACCEPTED" : "DEPENDENCY_OR_POLICY_BLOCKED";
    PartnerRepriceResult result = new PartnerRepriceResult(command.quoteId(), accepted, reason, quote.repriceGuidance(), metadata(command.partnerId(), command.quoteId(), command.correlationId(), dependencies));
    outboxEvents.add(new PartnerIntegrationEvent("PartnerQuoteRepriced.v1", command.partnerId(), command.quoteId(), command.actorId(), command.correlationId(), clock.instant()));
    return PartnerResult.success(result);
  }

  public PartnerResult<PartnerWebhookHealthView> webhookHealth(String partnerId, String correlationId) {
    if (isBlank(partnerId)) {
      return PartnerResult.failure(error("400", "VALIDATION_FAILED", "partnerId is required", correlationId));
    }
    List<WebhookHealthItem> items =
        webhooks.values().stream()
            .filter(webhook -> webhook.partnerId().equals(partnerId))
            .sorted(Comparator.comparing(WebhookIntegration::webhookId))
            .map(webhook -> new WebhookHealthItem(webhook.webhookId(), webhook.safetyState(), webhook.deliveryAttempts(), webhook.dlqDepth(), webhook.lastRootCause(), metadata(partnerId, webhook.webhookId(), correlationId, webhook.dependencyStatus())))
            .toList();
    return PartnerResult.success(new PartnerWebhookHealthView(partnerId, items, metadata(partnerId, "webhooks", correlationId, aggregateWebhookDependencies(partnerId))));
  }

  public PartnerResult<PartnerChannelWorkbenchView> workbench(String partnerId, String correlationId) {
    if (isBlank(partnerId)) {
      return PartnerResult.failure(error("400", "VALIDATION_FAILED", "partnerId is required", correlationId));
    }
    return PartnerResult.success(new PartnerChannelWorkbenchView(partnerId, DEFAULT_TABS, metadata(partnerId, "workbench", correlationId, aggregateWebhookDependencies(partnerId))));
  }

  public PartnerResult<ActionResult> replay(WebhookActionCommand command) {
    PartnerResult<WebhookIntegration> prepared = prepareWebhookAction(command);
    if (!prepared.valid()) {
      return PartnerResult.failure(prepared.error().orElseThrow());
    }
    String requestHash = replayRequestHash(command);
    String idempotencyKey = command.partnerId() + ":" + command.webhookId() + ":" + command.idempotencyKey();
    ActionResult existing = idempotentReplayResults.get(idempotencyKey);
    if (existing != null) {
      if (existing.metadata().replayHash().equals(requestHash)) {
        return PartnerResult.success(existing);
      }
      return PartnerResult.failure(error("409", "IDEMPOTENCY_CONFLICT", "Idempotency key was reused with a different replay request", command.correlationId()));
    }
    WebhookIntegration webhook = prepared.value().orElseThrow();
    boolean accepted = webhook.dependencyStatus().allAvailable() && webhook.safetyState() == SafetyState.RESUMED;
    ActionResult result = new ActionResult(command.webhookId(), accepted, accepted ? "REPLAY_ACCEPTED" : "REPLAY_BLOCKED", accepted ? IdempotencyState.NEW : IdempotencyState.CONFLICT, metadata(command.partnerId(), command.webhookId(), command.correlationId(), webhook.dependencyStatus()).withReplayHash(requestHash));
    idempotentReplayResults.put(idempotencyKey, result);
    outboxEvents.add(new PartnerIntegrationEvent("PartnerWebhookReplayed.v1", command.partnerId(), command.webhookId(), command.actorId(), command.correlationId(), clock.instant()));
    return PartnerResult.success(result);
  }

  public PartnerResult<ActionResult> testEndpoint(WebhookActionCommand command) {
    PartnerResult<WebhookIntegration> prepared = prepareWebhookAction(command);
    if (!prepared.valid()) {
      return PartnerResult.failure(prepared.error().orElseThrow());
    }
    WebhookIntegration webhook = prepared.value().orElseThrow();
    boolean accepted = webhook.dependencyStatus().allAvailable();
    ActionResult result = new ActionResult(command.webhookId(), accepted, accepted ? "ENDPOINT_TEST_ACCEPTED" : "DEPENDENCY_UNAVAILABLE", IdempotencyState.NEW, metadata(command.partnerId(), command.webhookId(), command.correlationId(), webhook.dependencyStatus()));
    outboxEvents.add(new PartnerIntegrationEvent("PartnerWebhookTested.v1", command.partnerId(), command.webhookId(), command.actorId(), command.correlationId(), clock.instant()));
    return PartnerResult.success(result);
  }

  public PartnerResult<SafetyToggleResult> safety(WebhookSafetyCommand command) {
    PartnerResult<WebhookIntegration> prepared = prepareWebhookAction(command);
    if (!prepared.valid()) {
      return PartnerResult.failure(prepared.error().orElseThrow());
    }
    WebhookIntegration webhook = prepared.value().orElseThrow();
    WebhookIntegration updated = webhook.withSafety(command.targetState());
    webhooks.put(webhookKey(updated.partnerId(), updated.webhookId()), updated);
    SafetyToggleResult result = new SafetyToggleResult(command.webhookId(), command.targetState(), command.targetState() == SafetyState.PAUSED ? "new deliveries queued in DLQ" : "DLQ processing may resume", metadata(command.partnerId(), command.webhookId(), command.correlationId(), updated.dependencyStatus()));
    outboxEvents.add(new PartnerIntegrationEvent("PartnerWebhookSafetyToggled.v1", command.partnerId(), command.webhookId(), command.actorId(), command.correlationId(), clock.instant()));
    return PartnerResult.success(result);
  }

  public List<PartnerIntegrationEvent> outboxEvents() {
    return List.copyOf(outboxEvents);
  }

  private PartnerResult<WebhookIntegration> prepareWebhookAction(WebhookActionCommand command) {
    PartnerError validation = validateAction(command == null ? null : command.partnerId(), command == null ? null : command.webhookId(), command == null ? null : command.actorId(), command == null ? null : command.correlationId());
    if (validation != null) {
      return PartnerResult.failure(validation);
    }
    if (isBlank(command.idempotencyKey())) {
      return PartnerResult.failure(error("400", "VALIDATION_FAILED", "Idempotency-Key is required", command.correlationId()));
    }
    WebhookIntegration webhook = webhooks.get(webhookKey(command.partnerId(), command.webhookId()));
    if (webhook == null) {
      return PartnerResult.failure(error("404", "NOT_FOUND", "Webhook integration was not found", command.correlationId()));
    }
    return PartnerResult.success(webhook);
  }

  private PartnerResult<WebhookIntegration> prepareWebhookAction(WebhookSafetyCommand command) {
    PartnerError validation = validateAction(command == null ? null : command.partnerId(), command == null ? null : command.webhookId(), command == null ? null : command.actorId(), command == null ? null : command.correlationId());
    if (validation != null) {
      return PartnerResult.failure(validation);
    }
    if (command.targetState() == null) {
      return PartnerResult.failure(error("400", "VALIDATION_FAILED", "targetState is required", command.correlationId()));
    }
    WebhookIntegration webhook = webhooks.get(webhookKey(command.partnerId(), command.webhookId()));
    if (webhook == null) {
      return PartnerResult.failure(error("404", "NOT_FOUND", "Webhook integration was not found", command.correlationId()));
    }
    return PartnerResult.success(webhook);
  }

  private PartnerError validateAction(String partnerId, String resourceId, String actorId, String correlationId) {
    if (isBlank(partnerId) || isBlank(resourceId) || isBlank(actorId) || isBlank(correlationId)) {
      return error("400", "VALIDATION_FAILED", "partnerId, resource id, actorId, and correlationId are required", correlationId);
    }
    return null;
  }

  private Metadata metadata(String partnerId, String resourceId, String correlationId, DependencyStatus dependencyStatus) {
    String material = partnerId + ":" + resourceId + ":" + correlationId + ":" + dependencyStatus.statusByDependency();
    return new Metadata(List.of("audit:" + hash(material).substring(0, 16)), hash(material), List.of("partner-integration-api:v1"), dependencyStatus, correlationId);
  }

  private DependencyStatus dependencyStatus(PartnerQuote quote) {
    return quote == null ? DependencyStatus.unavailable(DependencyName.PARTNER_QUOTE_SERVICE) : quote.dependencyStatus();
  }

  private DependencyStatus aggregateWebhookDependencies(String partnerId) {
    Map<String, DependencyState> states = new HashMap<>();
    for (DependencyName dependency : DependencyName.values()) {
      states.put(dependency.name(), DependencyState.UNAVAILABLE);
    }
    webhooks.values().stream().filter(webhook -> webhook.partnerId().equals(partnerId)).forEach(webhook -> states.putAll(webhook.dependencyStatus().statusByDependency()));
    return new DependencyStatus(states);
  }

  private static PartnerError error(String code, String reason, String message, String correlationId) {
    return new PartnerError(code, reason, message, correlationId == null ? "" : correlationId);
  }

  private static String quoteKey(String partnerId, String quoteId) {
    return partnerId + ":" + quoteId;
  }

  private static String webhookKey(String partnerId, String webhookId) {
    return partnerId + ":" + webhookId;
  }

  private static String replayRequestHash(WebhookActionCommand command) {
    return hash(command.partnerId() + ":" + command.webhookId() + ":replay:" + command.idempotencyKey() + ":" + command.actorId());
  }

  private static String hash(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is required", exception);
    }
  }

  private static boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }

  public enum QuoteStatus {
    SUBMITTED,
    PRICING,
    PRICED,
    LOCKED,
    COMMITTED,
    FUNDED,
    REJECTED,
    WITHDRAWN
  }

  public enum SlaState {
    ON_TRACK,
    AT_RISK,
    BREACHED
  }

  public enum LockState {
    UNLOCKED,
    LOCKED,
    EXPIRED,
    RELOCKED,
    FLOAT_DOWN
  }

  public enum DeliveryAttemptStatus {
    SUCCESS,
    FAILED,
    RETRYING,
    DLQ
  }

  public enum RootCauseCode {
    TIMEOUT,
    CONNECTION_REFUSED,
    HTTP_4XX,
    HTTP_5XX,
    SCHEMA_VALIDATION_FAILED,
    IDEMPOTENCY_CONFLICT,
    CONSENT_VIOLATION,
    PAYLOAD_TOO_LARGE
  }

  public enum IdempotencyState {
    NEW,
    DUPLICATE,
    CONFLICT
  }

  public enum SafetyState {
    PAUSED,
    RESUMED
  }

  public enum DependencyName {
    PARTNER_QUOTE_SERVICE("partner-quote-service"),
    PRICING_SERVICE,
    LOCK_SERVICE,
    NOTIFICATIONS,
    WEBHOOK_ROUTING;

    private final String externalId;

    DependencyName() {
      this.externalId = name();
    }

    DependencyName(String externalId) {
      this.externalId = externalId;
    }

    static Optional<DependencyName> fromDependencyId(String dependency) {
      if (isBlank(dependency)) {
        return Optional.empty();
      }
      for (DependencyName knownDependency : values()) {
        if (knownDependency.name().equals(dependency) || knownDependency.externalId.equals(dependency)) {
          return Optional.of(knownDependency);
        }
      }
      return Optional.empty();
    }
  }

  public enum DependencyState {
    AVAILABLE,
    UNAVAILABLE,
    PARTIAL
  }

  public record DependencyStatus(Map<String, DependencyState> statusByDependency) {
    public DependencyStatus {
      statusByDependency = statusByDependency == null || statusByDependency.isEmpty() ? Map.of() : Map.copyOf(statusByDependency);
    }

    public static DependencyStatus available(DependencyName... dependencies) {
      Map<String, DependencyState> states = new HashMap<>();
      for (DependencyName dependency : dependencies) {
        states.put(dependency.name(), DependencyState.AVAILABLE);
      }
      return new DependencyStatus(states);
    }

    public static DependencyStatus unavailable(DependencyName dependency) {
      return dependency == null ? new DependencyStatus(Map.of()) : new DependencyStatus(Map.of(dependency.name(), DependencyState.UNAVAILABLE));
    }

    public static DependencyStatus unavailable(String dependency) {
      if (isBlank(dependency)) {
        return new DependencyStatus(Map.of());
      }
      return DependencyName.fromDependencyId(dependency).map(DependencyStatus::unavailable).orElseGet(() -> new DependencyStatus(Map.of(dependency, DependencyState.UNAVAILABLE)));
    }

    public boolean allAvailable() {
      return !statusByDependency.isEmpty() && statusByDependency.values().stream().allMatch(state -> state == DependencyState.AVAILABLE);
    }
  }

  public record Metadata(List<String> auditRefs, String replayHash, List<String> versionRefs, DependencyStatus dependencyStatus, String correlationId) {
    public Metadata withReplayHash(String replayHash) {
      return new Metadata(auditRefs, replayHash, versionRefs, dependencyStatus, correlationId);
    }
  }

  public record PartnerQuote(String partnerId, String quoteId, QuoteStatus status, List<QuoteStatus> lifecycle, SlaState slaState, LockState lockState, Set<String> errorFlags, boolean repriceAvailable, String repriceGuidance, DependencyStatus dependencyStatus, Instant updatedAt) {}

  public record PartnerQuoteListItem(String quoteId, QuoteStatus status, SlaState slaState, LockState lockState, Set<String> errorFlags, Metadata metadata) {}

  public record PartnerQuoteDetail(String quoteId, QuoteStatus status, List<QuoteStatus> lifecycle, SlaState slaState, LockState lockState, Set<String> errorFlags, boolean repriceAvailable, String repriceGuidance, Metadata metadata) {}

  public record PartnerRepriceResult(String quoteId, boolean accepted, String reason, String guidance, Metadata metadata) {}

  public record DeliveryAttempt(String attemptId, DeliveryAttemptStatus status, RootCauseCode rootCauseCode, Instant attemptedAt) {}

  public record WebhookIntegration(String partnerId, String webhookId, SafetyState safetyState, List<DeliveryAttempt> deliveryAttempts, int dlqDepth, RootCauseCode lastRootCause, DependencyStatus dependencyStatus) {
    public WebhookIntegration withSafety(SafetyState targetState) {
      return new WebhookIntegration(partnerId, webhookId, targetState, deliveryAttempts, dlqDepth, lastRootCause, dependencyStatus);
    }
  }

  public record WebhookHealthItem(String webhookId, SafetyState safetyState, List<DeliveryAttempt> deliveryAttempts, int dlqDepth, RootCauseCode lastRootCause, Metadata metadata) {}

  public record PartnerWebhookHealthView(String partnerId, List<WebhookHealthItem> webhooks, Metadata metadata) {}

  public record WorkbenchTab(String key, String label, String recoveryOwner) {}

  public record PartnerChannelWorkbenchView(String partnerId, List<WorkbenchTab> tabs, Metadata metadata) {}

  public record RepriceCommand(String partnerId, String quoteId, String actorId, String correlationId) {}

  public record WebhookActionCommand(String partnerId, String webhookId, String actorId, String idempotencyKey, String correlationId) {}

  public record WebhookSafetyCommand(String partnerId, String webhookId, SafetyState targetState, String actorId, String correlationId) {}

  public record ActionResult(String webhookId, boolean accepted, String reason, IdempotencyState idempotencyState, Metadata metadata) {}

  public record SafetyToggleResult(String webhookId, SafetyState safetyState, String effect, Metadata metadata) {}

  public record PartnerIntegrationEvent(String eventType, String partnerId, String resourceId, String actorId, String correlationId, Instant occurredAt) {}

  public record PartnerError(String code, String reason, String message, String correlationId) {}

  public record PartnerResult<T>(boolean valid, Optional<T> value, Optional<PartnerError> error) {
    static <T> PartnerResult<T> success(T value) {
      return new PartnerResult<>(true, Optional.of(value), Optional.empty());
    }

    static <T> PartnerResult<T> failure(PartnerError error) {
      return new PartnerResult<>(false, Optional.empty(), Optional.of(error));
    }
  }
}
