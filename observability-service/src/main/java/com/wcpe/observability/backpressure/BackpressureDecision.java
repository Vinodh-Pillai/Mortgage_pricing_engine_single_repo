package com.wcpe.observability.backpressure;

import java.util.List;
import java.util.Map;

public record BackpressureDecision(
    BackpressureState state,
    BackpressureAction action,
    int httpStatus,
    String reasonCode,
    Map<String, String> responseMetadata,
    BackpressureStateSnapshot stateSnapshot,
    BackpressureAuditRecord auditRecord,
    List<BackpressureEventEnvelope> events,
    List<String> metricNames,
    List<String> traceNames,
    List<String> runbookSteps) {
  public boolean accepted() {
    return httpStatus >= 200 && httpStatus < 300;
  }
}
