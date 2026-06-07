package com.wcpe.mladvisory;

import java.util.Map;

public record ModelInferenceResult(
    String invocationId,
    String tenantId,
    String modelVersionId,
    String snapshotId,
    AdvisoryType advisoryType,
    String status,
    String advisoryResponse,
    long latencyMs,
    String confidenceBand,
    String reasonCode,
    String eventRef,
    String auditRef,
    String correlationId,
    boolean authoritative,
    Map<String, String> output) {
  public ModelInferenceResult {
    output = output == null ? Map.of() : Map.copyOf(output);
  }
}
