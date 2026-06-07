package com.wcpe.observability.ratelimit;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record RateLimitResult(
    RateLimitDecision decision,
    String reasonCode,
    int limit,
    int remaining,
    Instant resetAt,
    Map<String, String> responseHeaders,
    Map<String, String> problemJson,
    RateLimitDecisionAudit auditRecord,
    List<RateLimitEventEnvelope> events,
    List<String> metricNames,
    List<String> traceNames) {
  public RateLimitResult {
    responseHeaders = Map.copyOf(responseHeaders);
    problemJson = Map.copyOf(problemJson);
    events = List.copyOf(events);
    metricNames = List.copyOf(metricNames);
    traceNames = List.copyOf(traceNames);
  }

  public boolean allowed() {
    return decision == RateLimitDecision.ALLOWED || decision == RateLimitDecision.FAIL_OPEN_DEGRADED;
  }
}
