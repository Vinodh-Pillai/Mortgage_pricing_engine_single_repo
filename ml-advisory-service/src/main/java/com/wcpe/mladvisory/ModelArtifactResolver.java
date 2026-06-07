package com.wcpe.mladvisory;

final class ModelArtifactResolver {
  MlAdvisoryResult<ModelArtifactRef> resolve(ModelArtifactRef artifactRef) {
    if (artifactRef == null
        || isBlank(artifactRef.modelVersionId())
        || isBlank(artifactRef.artifactUri())
        || isBlank(artifactRef.registryChecksum())
        || isBlank(artifactRef.actualChecksum())
        || isBlank(artifactRef.approvalStatus())
        || isBlank(artifactRef.schemaVersion())) {
      return MlAdvisoryResult.failure("VALIDATION_FAILED");
    }
    if (!approvedForAdvisoryUse(artifactRef.approvalStatus())) {
      return MlAdvisoryResult.failure("MODEL_DISABLED");
    }
    if (!artifactRef.registryChecksum().equals(artifactRef.actualChecksum())) {
      return MlAdvisoryResult.failure("CHECKSUM_MISMATCH");
    }
    return MlAdvisoryResult.success(artifactRef);
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private boolean approvedForAdvisoryUse(String status) {
    return "APPROVED_FOR_ADVISORY".equals(status)
        || "APPROVED_SHADOW".equals(status)
        || "APPROVED_ADVISORY_VISIBLE".equals(status);
  }
}
