package com.wcpe.governance;

import java.time.Instant;
import java.util.Map;

public record ApprovalRequest(
    String approvalRequestId,
    String tenantId,
    String artifactId,
    String versionId,
    String status,
    Map<String, String> requiredPolicy,
    String submittedBy,
    Instant submittedAt) {
  public ApprovalRequest {
    requiredPolicy = Map.copyOf(requiredPolicy == null ? Map.of() : requiredPolicy);
  }
}
