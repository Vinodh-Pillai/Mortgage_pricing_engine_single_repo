package com.wcpe.mladvisory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LocalModelAdapterTest {
  @Test
  void shouldRejectUnapprovedModelVersion() {
    ModelArtifactRef unapproved =
        new ModelArtifactRef(
            "model-version-draft-1",
            "local://models/draft.fake",
            "sha256:draft",
            "sha256:draft",
            "REGISTERED",
            "ml-advisory-feature-schema-v1");

    ModelInferenceResult result =
        new FakeLocalModelAdapter().invoke(new ModelInvocationRequest(unapproved, AdvisoryTestFixtures.inferenceRequest("snapshot-draft")));

    assertEquals("MODEL_DISABLED", result.status());
    assertEquals("NO_ADVISORY", result.advisoryResponse());
  }

  @Test
  void shouldReturnSuccessfulBoundedInferenceForApprovedLocalModel() {
    ModelInferenceResult result = new FakeLocalModelAdapter().invoke(AdvisoryTestFixtures.invocationRequest());

    assertEquals("SUCCESS", result.status());
    assertEquals("ADVISORY_AVAILABLE", result.advisoryResponse());
    assertEquals("VISIBLE", result.confidenceBand());
    assertTrue(result.output().containsKey("recommendation"));
  }
}
