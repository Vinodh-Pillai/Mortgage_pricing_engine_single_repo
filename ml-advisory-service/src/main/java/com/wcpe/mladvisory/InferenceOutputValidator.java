package com.wcpe.mladvisory;

import java.util.Map;

final class InferenceOutputValidator {
  MlAdvisoryResult<Map<String, String>> validate(Map<String, String> output, boolean authoritative) {
    if (authoritative || output == null || output.isEmpty()) {
      return MlAdvisoryResult.failure("OUTPUT_REJECTED");
    }
    if (!output.containsKey("recommendation") || !output.containsKey("confidenceBand")) {
      return MlAdvisoryResult.failure("OUTPUT_REJECTED");
    }
    if ("true".equalsIgnoreCase(output.getOrDefault("authoritative", "false"))) {
      return MlAdvisoryResult.failure("OUTPUT_REJECTED");
    }
    if (output.keySet().stream().anyMatch(key -> key.toLowerCase().contains("borrower"))) {
      return MlAdvisoryResult.failure("OUTPUT_REJECTED");
    }
    return MlAdvisoryResult.success(output);
  }
}
