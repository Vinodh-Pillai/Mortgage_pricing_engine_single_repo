package com.wcpe.mladvisory;

public enum FeedbackOutcome {
  USEFUL,
  NOT_USEFUL,
  DISMISS,
  REPORT_CONCERN;

  public static FeedbackOutcome from(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("feedback outcome is required");
    }
    return FeedbackOutcome.valueOf(value.trim().toUpperCase().replace('-', '_'));
  }
}
