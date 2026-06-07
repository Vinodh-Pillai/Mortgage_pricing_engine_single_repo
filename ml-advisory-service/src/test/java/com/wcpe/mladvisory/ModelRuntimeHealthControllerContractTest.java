package com.wcpe.mladvisory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.Clock;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ModelRuntimeHealthControllerContractTest {
  @Test
  void shouldExposeSafeOperationalStatus() {
    MlAdvisoryControlService service = new MlAdvisoryControlService(Clock.fixed(AdvisoryTestFixtures.NOW, ZoneOffset.UTC));
    service.invokeLocalModel(new FakeLocalModelAdapter(), AdvisoryTestFixtures.invocationRequest());

    RuntimeHealth health = service.runtimeHealthForTenant(AdvisoryTestFixtures.TENANT).get(0);

    assertEquals(
        "GET /api/v1/tenants/{tenantId}/ml-advisory/model-runtime/health",
        MlAdvisoryControlService.GET_MODEL_RUNTIME_HEALTH_ENDPOINT);
    assertEquals("READY", health.status());
    assertEquals("model-version-approved-1", health.modelVersionId());
    assertFalse(health.toString().contains("raw-borrower-feature-value"));
    assertFalse(health.toString().contains("local://models"));
  }
}
