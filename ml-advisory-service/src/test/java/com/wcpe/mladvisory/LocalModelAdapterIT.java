package com.wcpe.mladvisory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class LocalModelAdapterIT {
  @Test
  void shouldReturnNoAdvisoryWhenRuntimeTimesOut() {
    MlAdvisoryControlService service = new MlAdvisoryControlService(Clock.fixed(AdvisoryTestFixtures.NOW, ZoneOffset.UTC));
    InferenceRequest timedOut =
        new InferenceRequest(
            AdvisoryTestFixtures.TENANT,
            "snapshot-timeout",
            AdvisoryType.PRICING,
            "ml-advisory-feature-schema-v1",
            java.util.Map.of("incomeStability", "raw-borrower-feature-value"),
            "pricing-analyst-1",
            "corr-runtime-timeout-PII-14-S04",
            250,
            true,
            false,
            false);

    ModelInferenceResult result =
        service
            .invokeLocalModel(new FakeLocalModelAdapter(), new ModelInvocationRequest(AdvisoryTestFixtures.approvedArtifact(), timedOut))
            .value()
            .orElseThrow();

    assertEquals("TIMEOUT", result.status());
    assertEquals("NO_ADVISORY", result.advisoryResponse());
  }
}
