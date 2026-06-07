package com.wcpe.mladvisory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FeedbackCommentPolicyTest {
  @Test
  void shouldRedactSensitiveBorrowerData() {
    FeedbackCommentPolicy.RedactionResult result =
        new FeedbackCommentPolicy().redact("Borrower email jane@example.com and SSN 123-45-6789 should not persist.");

    assertEquals("REDACTED_SENSITIVE", result.sensitivity());
    assertTrue(result.redactedComment().contains("[REDACTED_EMAIL]"));
    assertTrue(result.redactedComment().contains("[REDACTED_SSN]"));
    assertFalse(result.redactedComment().contains("jane@example.com"));
    assertFalse(result.redactedComment().contains("123-45-6789"));
  }
}
