package com.wcpe.observability.backpressure;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class BackpressureEvaluationService {
  public static final String STATE_CHANGED_EVENT = "BackpressureStateChanged.v1";
  public static final String REQUEST_REJECTED_EVENT = "BackpressureRequestRejected.v1";
  public static final String POLICY_PUBLISHED_EVENT = "BackpressurePolicyPublished.v1";

  public BackpressureDecision evaluate(
      List<BackpressurePolicy> policies,
      List<BackpressureSignal> signals,
      BackpressureRequest request) {
    Objects.requireNonNull(policies, "policies are required");
    Objects.requireNonNull(signals, "signals are required");
    Objects.requireNonNull(request, "request is required");
    BackpressurePolicy policy = policies.stream()
        .filter(candidate -> candidate.appliesTo(request.tenantId(), request.resource(), request.requestedAt()))
        .max(Comparator.comparing(BackpressurePolicy::effectiveFrom).thenComparingInt(BackpressurePolicy::version))
        .orElse(null);
    if (policy == null) {
      return failClosed(request, "POLICY_NOT_SATISFIED");
    }
    BackpressureTrigger breached = strongestBreach(policy, signals);
    if (breached == null) {
      return buildDecision(policy, request, BackpressureState.NORMAL, BackpressureAction.ALLOW,
          "NORMAL", "configured", BigDecimal.ZERO);
    }
    return buildDecision(policy, request, breached.targetState(), actionFor(request, breached),
        "BACKPRESSURE_ACTIVE", breached.metricName(), signalValue(signals, breached.metricName()));
  }

  private static BackpressureTrigger strongestBreach(BackpressurePolicy policy, List<BackpressureSignal> signals) {
    return policy.triggers().stream()
        .filter(trigger -> signals.stream().anyMatch(signal -> breaches(trigger, signal)))
        .max(Comparator.comparing(BackpressureTrigger::targetState)
            .thenComparing(trigger -> trigger.threshold()))
        .orElse(null);
  }

  private static boolean breaches(BackpressureTrigger trigger, BackpressureSignal signal) {
    return trigger.metricName().equals(signal.metricName())
        && signal.observedValue().compareTo(trigger.threshold()) > 0
        && signal.consecutiveBreachedWindows() >= trigger.consecutiveWindows();
  }

  private static BigDecimal signalValue(List<BackpressureSignal> signals, String metricName) {
    return signals.stream()
        .filter(signal -> metricName.equals(signal.metricName()))
        .map(BackpressureSignal::observedValue)
        .findFirst()
        .orElse(BigDecimal.ZERO);
  }

  private static BackpressureAction actionFor(BackpressureRequest request, BackpressureTrigger trigger) {
    if (request.priorityClass().rank() <= BackpressurePriorityClass.DIAGNOSTICS.rank()) {
      return BackpressureAction.REJECT_LOW_PRIORITY;
    }
    if (request.priorityClass() == BackpressurePriorityClass.CACHE_WARM) {
      return BackpressureAction.DISABLE_CACHE_WARM;
    }
    if (request.priorityClass() == BackpressurePriorityClass.BATCH_REPLAY && request.asyncDeferralSupported()) {
      return BackpressureAction.DEFER_ASYNC;
    }
    if (trigger.targetState().atLeast(BackpressureState.SHEDDING)
        && request.priorityClass().rank() < BackpressurePriorityClass.PARTNER_API_QUOTES.rank()) {
      return BackpressureAction.SHED_REQUEST;
    }
    return trigger.action();
  }

  private static BackpressureDecision failClosed(BackpressureRequest request, String reasonCode) {
    BackpressureStateSnapshot snapshot = snapshot(request, 0, BackpressureState.SHEDDING, "missing-policy", BigDecimal.ZERO, request.requestedAt());
    BackpressureAuditRecord audit = audit(request, "missing-policy:v0", "none", snapshot.state().name());
    return new BackpressureDecision(
        BackpressureState.SHEDDING,
        BackpressureAction.SHED_REQUEST,
        422,
        reasonCode,
        metadata(request, BackpressureState.SHEDDING, 0, reasonCode),
        snapshot,
        audit,
        List.of(event(request, 0, REQUEST_REJECTED_EVENT, BackpressureState.SHEDDING, BackpressureAction.SHED_REQUEST, reasonCode)),
        metricNames(BackpressureState.SHEDDING, true),
        List.of("backpressure.evaluate", "backpressure.action.apply"),
        runbookSteps());
  }

  private static BackpressureDecision buildDecision(
      BackpressurePolicy policy,
      BackpressureRequest request,
      BackpressureState state,
      BackpressureAction action,
      String reasonCode,
      String triggerMetric,
      BigDecimal triggerValue) {
    int status = httpStatus(action, state, request);
    BackpressureStateSnapshot snapshot = snapshot(request, policy.version(), state, triggerMetric, triggerValue,
        request.requestedAt().plus(policy.minimumStateDuration()).plus(policy.retryAfter()));
    BackpressureAuditRecord audit = audit(request, policy.policyId() + ":v" + policy.version(), "NORMAL", state.name());
    List<BackpressureEventEnvelope> events = status >= 400
        ? List.of(event(request, policy.version(), STATE_CHANGED_EVENT, state, action, reasonCode),
            event(request, policy.version(), REQUEST_REJECTED_EVENT, state, action, reasonCode))
        : List.of(event(request, policy.version(), STATE_CHANGED_EVENT, state, action, reasonCode));
    return new BackpressureDecision(
        state,
        action,
        status,
        reasonCode,
        metadata(request, state, policy.retryAfter().toSeconds(), reasonCode),
        snapshot,
        audit,
        events,
        metricNames(state, status >= 400),
        List.of("backpressure.evaluate", "backpressure.action.apply"),
        runbookSteps());
  }

  private static int httpStatus(BackpressureAction action, BackpressureState state, BackpressureRequest request) {
    if (action == BackpressureAction.DEFER_ASYNC && request.asyncDeferralSupported()) {
      return 202;
    }
    if (action == BackpressureAction.TIGHTEN_RATE_LIMIT || action == BackpressureAction.REJECT_LOW_PRIORITY) {
      return 429;
    }
    if (action == BackpressureAction.SHED_REQUEST || state.atLeast(BackpressureState.SHEDDING)) {
      return 503;
    }
    return 200;
  }

  private static BackpressureStateSnapshot snapshot(
      BackpressureRequest request,
      int policyVersion,
      BackpressureState state,
      String triggerMetric,
      BigDecimal triggerValue,
      Instant expiresAt) {
    return new BackpressureStateSnapshot(request.tenantId(), request.resource(), state, policyVersion, triggerMetric,
        triggerValue, request.requestedAt(), request.requestedAt(), expiresAt, request.correlationId());
  }

  private static Map<String, String> metadata(
      BackpressureRequest request,
      BackpressureState state,
      long retryAfterSeconds,
      String reasonCode) {
    return Map.of(
        "type", "https://wcpe.example/problems/backpressure",
        "title", "Backpressure policy affected this request",
        "retryAfter", Long.toString(Math.max(0, retryAfterSeconds)),
        "backpressureState", state.name(),
        "correlationId", request.correlationId(),
        "reasonCode", reasonCode);
  }

  private static BackpressureEventEnvelope event(
      BackpressureRequest request,
      int policyVersion,
      String eventType,
      BackpressureState state,
      BackpressureAction action,
      String reasonCode) {
    return new BackpressureEventEnvelope(
        request.tenantId(),
        request.correlationId() + ":" + eventType,
        eventType,
        1,
        "observability-service",
        request.actorId(),
        request.correlationId(),
        request.resource().token() + ":v" + policyVersion,
        request.requestedAt(),
        Map.of(
            "tenantId", request.tenantId().toString(),
            "resource", request.resource().token(),
            "state", state.name(),
            "action", action.name(),
            "priorityClass", request.priorityClass().name(),
            "reasonCode", reasonCode));
  }

  private static BackpressureAuditRecord audit(
      BackpressureRequest request,
      String policyConfigRef,
      String beforeSummary,
      String afterSummary) {
    return new BackpressureAuditRecord(
        request.tenantId(),
        "BACKPRESSURE_POLICY_COMPLETED",
        request.actorId(),
        beforeSummary,
        afterSummary,
        policyConfigRef,
        request.correlationId(),
        replayHash(request, policyConfigRef, afterSummary),
        request.requestedAt());
  }

  private static List<String> metricNames(BackpressureState state, boolean rejected) {
    List<String> metrics = new java.util.ArrayList<>();
    metrics.add("backpressure.state");
    metrics.add("backpressure.queue.depth");
    metrics.add("backpressure.recovery.duration");
    if (rejected) {
      metrics.add("backpressure.rejected.count");
    }
    metrics.add("backpressure.resource.trigger." + state.name().toLowerCase(java.util.Locale.ROOT));
    return List.copyOf(metrics);
  }

  private static List<String> runbookSteps() {
    return List.of(
        "Identify the active resource trigger and affected tenants or endpoints.",
        "Confirm protective actions are reducing load before widening limits.",
        "Disable non-critical jobs or cache warmers, then repair or scale the saturated resource.",
        "Monitor recovery hysteresis and flapping alerts before returning to NORMAL.");
  }

  private static String replayHash(BackpressureRequest request, String policyConfigRef, String afterSummary) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] bytes = digest.digest((request.tenantId() + "|" + request.resource() + "|" + policyConfigRef + "|"
          + afterSummary + "|" + request.correlationId()).getBytes(StandardCharsets.UTF_8));
      return "sha256:" + HexFormat.of().formatHex(bytes);
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 unavailable", ex);
    }
  }
}
