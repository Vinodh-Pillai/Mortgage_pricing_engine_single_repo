package com.wcpe.mladvisory;

import java.util.Map;

public record InferenceRequest(
    String tenantId,
    String snapshotId,
    AdvisoryType advisoryType,
    String featureSchemaVersion,
    Map<String, String> featureValues,
    String actorId,
    String correlationId,
    long timeoutMillis,
    boolean simulateTimeout,
    boolean simulateFailure,
    boolean authoritativeOutput) {
  public InferenceRequest {
    featureValues = featureValues == null ? Map.of() : Map.copyOf(featureValues);
  }
}
