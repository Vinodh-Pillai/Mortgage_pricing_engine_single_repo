package com.wcpe.mladvisory;

final class ModelCompatibilityChecker {
  MlAdvisoryResult<ModelVersion> validateFeatureSchema(ModelVersion version, String expectedFeatureSchemaVersion) {
    if (expectedFeatureSchemaVersion == null || expectedFeatureSchemaVersion.isBlank()) {
      return MlAdvisoryResult.failure("VALIDATION_FAILED");
    }
    if (!version.featureSchemaVersion().equals(expectedFeatureSchemaVersion)) {
      return MlAdvisoryResult.failure("ML_MODEL_FEATURE_SCHEMA_INCOMPATIBLE");
    }
    return MlAdvisoryResult.success(version);
  }
}
