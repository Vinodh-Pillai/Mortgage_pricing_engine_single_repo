package com.wcpe.mladvisory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Map;
import org.junit.jupiter.api.Test;

class InferenceOutputValidatorTest {
  @Test
  void shouldRejectAuthoritativeOrMalformedOutput() {
    InferenceOutputValidator validator = new InferenceOutputValidator();

    MlAdvisoryResult<Map<String, String>> authoritative =
        validator.validate(Map.of("recommendation", "approve", "confidenceBand", "VISIBLE"), true);
    MlAdvisoryResult<Map<String, String>> malformed = validator.validate(Map.of("recommendation", "review"), false);

    assertFalse(authoritative.valid());
    assertFalse(malformed.valid());
    assertEquals("OUTPUT_REJECTED", authoritative.errorCode().orElseThrow());
    assertEquals("OUTPUT_REJECTED", malformed.errorCode().orElseThrow());
  }
}
