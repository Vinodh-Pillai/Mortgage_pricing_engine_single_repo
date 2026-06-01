package com.wcpe.compliance;

import java.util.ArrayList;
import java.util.List;

public final class ComplianceShell {
  private static final String PENDING_EVIDENCE = "pending_evidence";
  private static final String DEFERRED_MESSAGE =
      "Compliance evaluation is deferred until quote, lock, and concession evidence is available.";

  private ComplianceShell() {}

  public static ComplianceShellResponse evaluateComplianceShell(ComplianceShellRequest request) {
    ComplianceShellRequest normalized = validateComplianceShellRequest(request);

    return new ComplianceShellResponse(
        normalized.requestId(),
        PENDING_EVIDENCE,
        List.of(),
        List.of(DEFERRED_MESSAGE),
        normalized.evidenceRefs(),
        normalized.ruleSetRef());
  }

  public static ComplianceShellRequest validateComplianceShellRequest(ComplianceShellRequest request) {
    if (request == null) {
      throw new ComplianceShellValidationError(
          "Compliance shell request must be an object.", List.of("request"));
    }

    List<String> details = new ArrayList<>();

    if (!isNonBlankString(request.requestId())) {
      details.add("requestId must be a non-empty string");
    }

    if (!isNonBlankString(request.subjectRef())) {
      details.add("subjectRef must be a non-empty string");
    }

    if (request.evidenceRefs() != null && !isStringList(request.evidenceRefs())) {
      details.add("evidenceRefs must be an array of non-empty strings when provided");
    }

    if (request.ruleSetRef() != null && !isNonBlankString(request.ruleSetRef())) {
      details.add("ruleSetRef must be a non-empty string when provided");
    }

    if (!details.isEmpty()) {
      throw new ComplianceShellValidationError(
          "Compliance shell request validation failed.", details);
    }

    return new ComplianceShellRequest(
        request.requestId().trim(),
        request.subjectRef().trim(),
        request.evidenceRefs() == null ? List.of() : List.copyOf(request.evidenceRefs()),
        request.ruleSetRef() == null ? null : request.ruleSetRef().trim());
  }

  private static boolean isNonBlankString(String value) {
    return value != null && !value.trim().isEmpty();
  }

  private static boolean isStringList(List<String> value) {
    return value.stream().allMatch(ComplianceShell::isNonBlankString);
  }
}
