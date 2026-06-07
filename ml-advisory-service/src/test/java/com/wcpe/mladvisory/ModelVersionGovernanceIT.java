package com.wcpe.mladvisory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class ModelVersionGovernanceIT {
  @Test
  void shouldInvalidateControlsAndRuntimeOnSuspension() {
    ModelRegistryService service = ModelVersionGovernanceFixtures.registryWithVisibleApproval();
    String modelVersionId = service.list(AdvisoryTestFixtures.TENANT, ModelStatus.APPROVED_ADVISORY_VISIBLE, AdvisoryType.PRICING).get(0).modelVersionId();

    ModelVersionResponse response =
        service
            .suspend(new SuspendModelVersionCommand(AdvisoryTestFixtures.TENANT, modelVersionId, "risk-admin-1", "MRM-SUSPEND-1", "drift review", "corr-suspend"))
            .value()
            .orElseThrow();

    assertEquals(ModelStatus.SUSPENDED, response.status());
    assertFalse(response.cacheInvalidationRef().isBlank());
    assertFalse(service.discoverable(AdvisoryTestFixtures.TENANT, modelVersionId, "ml-advisory-feature-schema-v1"));
  }
}
