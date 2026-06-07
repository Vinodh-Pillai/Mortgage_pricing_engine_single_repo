package com.wcpe.mladvisory;

import java.util.Map;

public record ModelEvidence(String evidenceType, String uriOrPayload, Map<String, String> metrics, String reviewStatus) {
  public ModelEvidence {
    metrics = metrics == null ? Map.of() : Map.copyOf(metrics);
  }
}
