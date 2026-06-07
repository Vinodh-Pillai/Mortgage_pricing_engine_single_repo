package com.wcpe.tenantcontext.consumer;

public record ConsumerGuardDecision(Outcome outcome, ConsumerInboxRecord record, boolean sideEffectExecuted) {
    public enum Outcome {
        PROCESSING_STARTED,
        PROCESSING_COMPLETED,
        DUPLICATE_IGNORED
    }

    public ConsumerGuardDecision {
        if (outcome == null) {
            throw new ConsumerInboxException("CONSUMER_INBOX_VALIDATION_FAILED", "outcome is required");
        }
        if (record == null) {
            throw new ConsumerInboxException("CONSUMER_INBOX_VALIDATION_FAILED", "record is required");
        }
    }
}
