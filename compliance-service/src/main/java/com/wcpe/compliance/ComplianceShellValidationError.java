package com.wcpe.compliance;

import java.util.List;

public class ComplianceShellValidationError extends RuntimeException {
  public static final String CODE = "COMPLIANCE_SHELL_VALIDATION_FAILED";

  private final String code;
  private final List<String> details;

  public ComplianceShellValidationError(String message, List<String> details) {
    super(message);
    this.code = CODE;
    this.details = List.copyOf(details);
  }

  public String getCode() {
    return code;
  }

  public List<String> getDetails() {
    return details;
  }
}
