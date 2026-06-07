package com.wcpe.mladvisory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.Clock;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ModelInvocationAuditIT {
  @Test
  void shouldPersistInvocationWithoutRawFeatureValues() {
    MlAdvisoryControlService service = new MlAdvisoryControlService(Clock.fixed(AdvisoryTestFixtures.NOW, ZoneOffset.UTC));

    service.invokeLocalModel(new FakeLocalModelAdapter(), AdvisoryTestFixtures.invocationRequest());

    assertEquals(1, service.modelInvocationsForTenant(AdvisoryTestFixtures.TENANT).size());
    String eventPayload = service.outboxEvents().toString();
    String auditPayload = service.auditRecords().toString();
    assertFalse(eventPayload.contains("raw-borrower-feature-value"));
    assertFalse(auditPayload.contains("raw-borrower-feature-value"));
    assertEquals("ML_MODEL_RUNTIME_INVOCATION_COMPLETED", service.auditRecords().get(0).action());
  }
}
