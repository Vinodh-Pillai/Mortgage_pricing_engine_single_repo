package com.wcpe.mladvisory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class ModelArtifactResolverTest {
  @Test
  void shouldValidateChecksumBeforeLoad() {
    ModelArtifactRef tampered =
        new ModelArtifactRef(
            "model-version-approved-1",
            "local://models/ml-advisory/model-version-approved-1.fake",
            "sha256:registry",
            "sha256:tampered",
            "APPROVED_FOR_ADVISORY",
            "ml-advisory-feature-schema-v1");

    MlAdvisoryResult<ModelArtifactRef> result = new ModelArtifactResolver().resolve(tampered);

    assertFalse(result.valid());
    assertEquals("CHECKSUM_MISMATCH", result.errorCode().orElseThrow());
  }
}
