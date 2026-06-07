package com.wcpe.mladvisory;

import java.util.regex.Pattern;

public final class FeedbackCommentPolicy {
  private static final Pattern SSN = Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b");
  private static final Pattern EMAIL = Pattern.compile("\\b[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}\\b");
  private static final Pattern PHONE = Pattern.compile("\\b(?:\\+?1[-. ]?)?\\(?\\d{3}\\)?[-. ]?\\d{3}[-. ]?\\d{4}\\b");

  public RedactionResult redact(String comment) {
    if (comment == null || comment.isBlank()) {
      return new RedactionResult("", "NONE", 0);
    }
    String redacted = comment.strip();
    redacted = SSN.matcher(redacted).replaceAll("[REDACTED_SSN]");
    redacted = EMAIL.matcher(redacted).replaceAll("[REDACTED_EMAIL]");
    redacted = PHONE.matcher(redacted).replaceAll("[REDACTED_PHONE]");
    int redactionCount = countMarkers(redacted);
    return new RedactionResult(redacted, redactionCount == 0 ? "NONE" : "REDACTED_SENSITIVE", redactionCount);
  }

  private int countMarkers(String value) {
    int count = 0;
    int index = value.indexOf("[REDACTED_");
    while (index >= 0) {
      count++;
      index = value.indexOf("[REDACTED_", index + 1);
    }
    return count;
  }

  public record RedactionResult(String redactedComment, String sensitivity, int redactionCount) {}
}
