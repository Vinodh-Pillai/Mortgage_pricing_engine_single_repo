package com.wcpe.compliance;

import java.util.List;

public record ComplianceShellRequest(
    String requestId,
    String subjectRef,
    List<String> evidenceRefs,
    String ruleSetRef) {

  public ComplianceShellRequest(String requestId, String subjectRef) {
    this(requestId, subjectRef, null, null);
  }

  public ComplianceShellRequest(String requestId, String subjectRef, List<String> evidenceRefs) {
    this(requestId, subjectRef, evidenceRefs, null);
  }
}
