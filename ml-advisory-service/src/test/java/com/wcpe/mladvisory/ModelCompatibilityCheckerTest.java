package com.wcpe.mladvisory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class ModelCompatibilityCheckerTest {
  @Test
  void shouldRejectIncompatibleFeatureSchema() {
    ModelRegistryService service = ModelVersionGovernanceFixtures.registryWithVisibleApproval();
    String modelVersionId = service.list(AdvisoryTestFixtures.TENANT, ModelStatus.APPROVED_ADVISORY_VISIBLE, AdvisoryType.PRICING).get(0).modelVersionId();

    assertFalse(service.discoverable(AdvisoryTestFixtures.TENANT, modelVersionId, "different-schema"));

    ModelVersion version = service.list(AdvisoryTestFixtures.TENANT, ModelStatus.APPROVED_ADVISORY_VISIBLE, AdvisoryType.PRICING).get(0);
    MlAdvisoryResult<ModelVersion> result = new ModelCompatibilityChecker().validateFeatureSchema(version, "different-schema");
    assertEquals("ML_MODEL_FEATURE_SCHEMA_INCOMPATIBLE", result.errorCode().orElseThrow());
  }
}
