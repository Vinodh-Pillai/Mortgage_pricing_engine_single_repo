package com.wcpe.quote;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record QuoteJob(
    UUID tenantId,
    UUID jobId,
    QuoteJobStatus status,
    Map<String, String> requestPayload,
    String requestHash,
    UUID quoteId,
    String failureCode,
    String failureDetail,
    Map<String, String> progress,
    int attemptCount,
    int maxAttempts,
    String idempotencyKey,
    String createdBy,
    Instant createdAt,
    Instant updatedAt,
    Instant expiresAt,
    String correlationId,
    int version
) {
    public QuoteJob {
        requestPayload = Map.copyOf(requestPayload == null ? Map.of() : requestPayload);
        progress = Map.copyOf(progress == null ? Map.of() : progress);
    }

    public boolean terminal() {
        return status == QuoteJobStatus.COMPLETED || status == QuoteJobStatus.FAILED || status == QuoteJobStatus.CANCELLED;
    }

    public QuoteJob running(Instant now) {
        requireNonTerminal();
        return transition(QuoteJobStatus.RUNNING, null, null, Map.of("stage", "quote-generation", "percent", "25"), attemptCount + 1, null, now);
    }

    public QuoteJob completed(Quote quote, Instant now) {
        requireNonTerminal();
        return transition(QuoteJobStatus.COMPLETED, quote.quoteId(), null, Map.of("stage", "completed", "percent", "100"), attemptCount, null, now);
    }

    public QuoteJob failed(String code, String detail, Instant now) {
        requireNonTerminal();
        return transition(QuoteJobStatus.FAILED, null, code, Map.of("stage", "failed", "percent", "100"), attemptCount, detail, now);
    }

    public QuoteJob cancelled(Instant now) {
        requireNonTerminal();
        return transition(QuoteJobStatus.CANCELLED, null, null, Map.of("stage", "cancelled", "percent", "100"), attemptCount, "cancelled by actor", now);
    }

    private QuoteJob transition(
        QuoteJobStatus nextStatus,
        UUID nextQuoteId,
        String nextFailureCode,
        Map<String, String> nextProgress,
        int nextAttemptCount,
        String nextFailureDetail,
        Instant now
    ) {
        return new QuoteJob(
            tenantId,
            jobId,
            nextStatus,
            requestPayload,
            requestHash,
            nextQuoteId == null ? quoteId : nextQuoteId,
            nextFailureCode,
            nextFailureDetail,
            nextProgress,
            nextAttemptCount,
            maxAttempts,
            idempotencyKey,
            createdBy,
            createdAt,
            now,
            expiresAt,
            correlationId,
            version + 1
        );
    }

    private void requireNonTerminal() {
        if (terminal()) {
            throw new QuoteCreateException("VERSION_CONFLICT", "Terminal quote jobs are immutable");
        }
    }
}
