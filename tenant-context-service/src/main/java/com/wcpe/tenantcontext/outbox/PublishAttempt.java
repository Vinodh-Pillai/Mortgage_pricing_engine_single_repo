package com.wcpe.tenantcontext.outbox;

import java.time.Instant;

public record PublishAttempt(int attemptNumber, Instant attemptedAt, String resultCode, String message) {
    public PublishAttempt {
        if (attemptNumber < 1) {
            throw new OutboxException("OUTBOX_ATTEMPT_INVALID", "attempt number must be positive");
        }
        if (attemptedAt == null) {
            throw new OutboxException("OUTBOX_ATTEMPT_INVALID", "attempt timestamp is required");
        }
        resultCode = required(resultCode, "resultCode");
        message = message == null ? "" : message.trim();
    }

    private static String required(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new OutboxException("OUTBOX_ATTEMPT_INVALID", fieldName + " is required");
        }
        return value.trim();
    }
}
