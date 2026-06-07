package com.wcpe.observability.cache;

import java.util.List;
import java.util.Objects;

public record CacheInvalidationResult(
    CacheInvalidationRequest request,
    List<CacheInvalidationEventEnvelope> events,
    List<CacheOperationAudit> auditRecords,
    List<String> metricNames,
    List<String> traceNames) {
  public CacheInvalidationResult {
    request = Objects.requireNonNull(request, "request is required");
    events = events == null ? List.of() : List.copyOf(events);
    auditRecords = auditRecords == null ? List.of() : List.copyOf(auditRecords);
    metricNames = metricNames == null ? List.of() : List.copyOf(metricNames);
    traceNames = traceNames == null ? List.of() : List.copyOf(traceNames);
  }
}
