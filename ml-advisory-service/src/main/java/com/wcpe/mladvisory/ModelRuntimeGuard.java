package com.wcpe.mladvisory;

final class ModelRuntimeGuard {
  private final ModelArtifactResolver artifactResolver = new ModelArtifactResolver();

  MlAdvisoryResult<ModelArtifactRef> approve(ModelInvocationRequest request) {
    MlAdvisoryResult<ModelArtifactRef> artifact = artifactResolver.resolve(request.artifactRef());
    if (!artifact.valid()) {
      return artifact;
    }
    if (!request.artifactRef().schemaVersion().equals(request.inferenceRequest().featureSchemaVersion())) {
      return MlAdvisoryResult.failure("SCHEMA_MISMATCH");
    }
    if (request.inferenceRequest().timeoutMillis() <= 0) {
      return MlAdvisoryResult.failure("TIMEOUT");
    }
    return artifact;
  }
}
