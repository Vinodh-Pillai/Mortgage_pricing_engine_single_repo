package com.wcpe.mladvisory;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

public final class FakeLocalModelAdapter implements LocalModelAdapter {
  private final ModelRuntimeGuard guard = new ModelRuntimeGuard();
  private final InferenceOutputValidator outputValidator = new InferenceOutputValidator();

  @Override
  public ModelInferenceResult invoke(ModelInvocationRequest request) {
    MlAdvisoryResult<ModelArtifactRef> approved = guard.approve(request);
    if (!approved.valid()) {
      return noAdvisory(request, approved.errorCode().orElse("MODEL_UNAVAILABLE"), 0);
    }
    InferenceRequest inference = request.inferenceRequest();
    if (inference.simulateTimeout()) {
      return noAdvisory(request, "TIMEOUT", inference.timeoutMillis());
    }
    if (inference.simulateFailure()) {
      return noAdvisory(request, "MODEL_UNAVAILABLE", 1);
    }

    Map<String, String> output =
        Map.of(
            "recommendation", "Review advisory evidence before final pricing decision",
            "confidenceBand", "VISIBLE",
            "sourceSnapshot", inference.snapshotId());
    MlAdvisoryResult<Map<String, String>> validation = outputValidator.validate(output, inference.authoritativeOutput());
    if (!validation.valid()) {
      return noAdvisory(request, validation.errorCode().orElse("OUTPUT_REJECTED"), 1);
    }
    return result(request, "SUCCESS", "ADVISORY_AVAILABLE", 1, "VISIBLE", "", false, output);
  }

  private ModelInferenceResult noAdvisory(ModelInvocationRequest request, String reasonCode, long latencyMs) {
    return result(request, reasonCode, "NO_ADVISORY", latencyMs, "", reasonCode, false, Map.of());
  }

  private ModelInferenceResult result(
      ModelInvocationRequest request,
      String status,
      String advisoryResponse,
      long latencyMs,
      String confidenceBand,
      String reasonCode,
      boolean authoritative,
      Map<String, String> output) {
    InferenceRequest inference = request.inferenceRequest();
    String invocationId = stableId(inference.tenantId(), request.artifactRef().modelVersionId(), inference.snapshotId(), status);
    return new ModelInferenceResult(
        invocationId,
        inference.tenantId(),
        request.artifactRef().modelVersionId(),
        inference.snapshotId(),
        inference.advisoryType(),
        status,
        advisoryResponse,
        latencyMs,
        confidenceBand,
        reasonCode,
        stableId(invocationId, inference.correlationId(), MlAdvisoryControlService.ML_MODEL_INFERENCE_COMPLETED_EVENT),
        stableId(invocationId, inference.actorId(), "runtime-audit"),
        inference.correlationId(),
        authoritative,
        output);
  }

  private String stableId(String... parts) {
    return UUID.nameUUIDFromBytes(String.join(":", parts).getBytes(StandardCharsets.UTF_8)).toString();
  }
}
