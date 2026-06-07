package com.wcpe.observability.ratelimit;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class RateLimitEvaluationService {
  public static final String DECISION_EVENT = "RateLimitDecisionRecorded.v1";
  public static final String BREACH_EVENT = "RateLimitBreached.v1";

  private final RateLimitCounterStore primaryCounterStore;
  private final RateLimitCounterStore emergencyFallbackStore;

  public RateLimitEvaluationService(
      RateLimitCounterStore primaryCounterStore,
      RateLimitCounterStore emergencyFallbackStore) {
    this.primaryCounterStore = Objects.requireNonNull(primaryCounterStore, "primaryCounterStore is required");
    this.emergencyFallbackStore = Objects.requireNonNull(emergencyFallbackStore, "emergencyFallbackStore is required");
  }

  public RateLimitResult evaluate(List<RateLimitPolicy> policies, RateLimitRequest request) {
    Objects.requireNonNull(policies, "policies are required");
    Objects.requireNonNull(request, "request is required");
    RateLimitPolicy policy = policies.stream()
        .filter(policyCandidate -> policyCandidate.matches(request))
        .max(Comparator.comparing((RateLimitPolicy policyCandidate) -> policyCandidate.exactPrincipalMatch(request))
            .thenComparing(RateLimitPolicy::effectiveFrom)
            .thenComparingInt(RateLimitPolicy::version))
        .orElse(null);
    if (policy == null) {
      return failClosed(request, "POLICY_NOT_SATISFIED", 0, request.requestedAt(), null);
    }

    RateLimitCounterKey key = counterKey(policy, request);
    Instant resetAt = resetAt(request.requestedAt(), policy.window());
    try {
      return fromCounter(policy, request, primaryCounterStore.increment(key, policy.capacity(), resetAt), false);
    } catch (RateLimitCounterUnavailableException ex) {
      return evaluateCounterFallback(policy, request, key, resetAt);
    }
  }

  private RateLimitResult evaluateCounterFallback(
      RateLimitPolicy policy,
      RateLimitRequest request,
      RateLimitCounterKey key,
      Instant resetAt) {
    if (request.endpointRisk() != EndpointRisk.BUSINESS_QUOTE_READ || policy.emergencyFallbackLimit() <= 0) {
      return failClosed(request, "RATE_LIMIT_COUNTER_UNAVAILABLE", policy.capacity(), resetAt, policy);
    }
    RateLimitCounterSnapshot snapshot = emergencyFallbackStore.increment(key, policy.emergencyFallbackLimit(), resetAt);
    if (snapshot.used() <= policy.emergencyFallbackLimit()) {
      return buildResult(policy, request, RateLimitDecision.FAIL_OPEN_DEGRADED, "REDIS_FALLBACK_ACTIVE",
          policy.emergencyFallbackLimit(), snapshot.remaining(), snapshot.resetAt());
    }
    return buildResult(policy, request, RateLimitDecision.THROTTLED, "RATE_LIMIT_EXCEEDED",
        policy.emergencyFallbackLimit(), snapshot.remaining(), snapshot.resetAt());
  }

  private RateLimitResult fromCounter(
      RateLimitPolicy policy,
      RateLimitRequest request,
      RateLimitCounterSnapshot snapshot,
      boolean degraded) {
    if (snapshot.used() <= policy.capacity()) {
      return buildResult(policy, request, degraded ? RateLimitDecision.FAIL_OPEN_DEGRADED : RateLimitDecision.ALLOWED,
          degraded ? "REDIS_FALLBACK_ACTIVE" : "ALLOWED", policy.capacity(), snapshot.remaining(), snapshot.resetAt());
    }
    return buildResult(policy, request, RateLimitDecision.THROTTLED, "RATE_LIMIT_EXCEEDED",
        policy.capacity(), snapshot.remaining(), snapshot.resetAt());
  }

  private RateLimitResult failClosed(
      RateLimitRequest request,
      String reasonCode,
      int limit,
      Instant resetAt,
      RateLimitPolicy policy) {
    String policyKey = policy == null ? "missing-policy" : policy.policyKey();
    int policyVersion = policy == null ? 0 : policy.version();
    RateLimitDecisionAudit audit = new RateLimitDecisionAudit(
        request.tenantId(), policyKey, policyVersion, request.principalHash(), request.endpointGroup(),
        RateLimitDecision.FAIL_CLOSED, 0, resetAt, request.correlationId(), request.requestedAt());
    return new RateLimitResult(
        RateLimitDecision.FAIL_CLOSED,
        reasonCode,
        limit,
        0,
        resetAt,
        headers(limit, 0, resetAt, true, request.requestedAt()),
        problem(reasonCode, request.correlationId()),
        audit,
        List.of(event(request, policyKey, policyVersion, DECISION_EVENT, RateLimitDecision.FAIL_CLOSED, reasonCode)),
        List.of("rate_limit.decision.count", "rate_limit.fallback.count"),
        List.of("api.rate_limit.evaluate"));
  }

  private RateLimitResult buildResult(
      RateLimitPolicy policy,
      RateLimitRequest request,
      RateLimitDecision decision,
      String reasonCode,
      int limit,
      int remaining,
      Instant resetAt) {
    boolean throttled = decision == RateLimitDecision.THROTTLED;
    RateLimitDecisionAudit audit = new RateLimitDecisionAudit(
        request.tenantId(), policy.policyKey(), policy.version(), request.principalHash(), request.endpointGroup(),
        decision, remaining, resetAt, request.correlationId(), request.requestedAt());
    List<RateLimitEventEnvelope> events = throttled
        ? List.of(event(request, policy.policyKey(), policy.version(), DECISION_EVENT, decision, reasonCode),
            event(request, policy.policyKey(), policy.version(), BREACH_EVENT, decision, reasonCode))
        : List.of(event(request, policy.policyKey(), policy.version(), DECISION_EVENT, decision, reasonCode));
    return new RateLimitResult(
        decision,
        reasonCode,
        limit,
        remaining,
        resetAt,
        headers(limit, remaining, resetAt, throttled, request.requestedAt()),
        throttled ? problem(reasonCode, request.correlationId()) : Map.of(),
        audit,
        events,
        metricNames(decision),
        List.of("api.rate_limit.evaluate"));
  }

  private static RateLimitCounterKey counterKey(RateLimitPolicy policy, RateLimitRequest request) {
    long windowSeconds = policy.window().toSeconds();
    long windowStart = (request.requestedAt().getEpochSecond() / windowSeconds) * windowSeconds;
    return new RateLimitCounterKey(
        request.tenantId(), policy.policyKey(), policy.version(), request.principalHash(), request.endpointGroup(), windowStart);
  }

  private static Instant resetAt(Instant requestedAt, Duration window) {
    long seconds = window.toSeconds();
    return Instant.ofEpochSecond(((requestedAt.getEpochSecond() / seconds) + 1) * seconds);
  }

  private static Map<String, String> headers(
      int limit,
      int remaining,
      Instant resetAt,
      boolean retryAfter,
      Instant requestedAt) {
    Map<String, String> base = new java.util.LinkedHashMap<>();
    base.put("X-RateLimit-Limit", Integer.toString(limit));
    base.put("X-RateLimit-Remaining", Integer.toString(remaining));
    base.put("X-RateLimit-Reset", resetAt.toString());
    if (retryAfter) {
      base.put("Retry-After", Long.toString(Math.max(0, resetAt.getEpochSecond() - requestedAt.getEpochSecond())));
    }
    return base;
  }

  private static Map<String, String> problem(String reasonCode, String correlationId) {
    String status = switch (reasonCode) {
      case "POLICY_NOT_SATISFIED" -> "422";
      case "RATE_LIMIT_COUNTER_UNAVAILABLE" -> "503";
      default -> "429";
    };
    return Map.of(
        "type", "https://wcpe.example/problems/rate-limit",
        "title", "Rate limit policy did not allow this request",
        "status", status,
        "reasonCode", reasonCode,
        "correlationId", correlationId);
  }

  private static RateLimitEventEnvelope event(
      RateLimitRequest request,
      String policyKey,
      int policyVersion,
      String eventType,
      RateLimitDecision decision,
      String reasonCode) {
    return new RateLimitEventEnvelope(
        request.tenantId(),
        request.correlationId() + ":" + eventType,
        eventType,
        1,
        "observability-service",
        request.correlationId(),
        policyKey,
        policyVersion,
        request.requestedAt(),
        Map.of(
            "tenantId", request.tenantId().toString(),
            "endpointGroup", request.endpointGroup(),
            "principalHash", request.principalHash(),
            "policyKey", policyKey,
            "policyVersion", Integer.toString(policyVersion),
            "decision", decision.name(),
            "reasonCode", reasonCode));
  }

  private static List<String> metricNames(RateLimitDecision decision) {
    return switch (decision) {
      case ALLOWED -> List.of("rate_limit.decision.count", "rate_limit.remaining");
      case THROTTLED -> List.of("rate_limit.decision.count", "http.server.requests{status=429}");
      case FAIL_OPEN_DEGRADED -> List.of("rate_limit.decision.count", "rate_limit.fallback.count");
      case FAIL_CLOSED -> List.of("rate_limit.decision.count", "rate_limit.fallback.count");
    };
  }
}
