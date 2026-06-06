package com.wcpe.quote;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record QuoteJobResponse(
    UUID jobId,
    String status,
    String statusUrl,
    int retryAfterSeconds,
    UUID quoteId,
    String failureCode,
    String failureDetail,
    Map<String, String> progress,
    String auditRef,
    String correlationId,
    int version,
    Instant updatedAt
) {
    public QuoteJobResponse {
        progress = Map.copyOf(progress == null ? Map.of() : progress);
    }

    public static QuoteJobResponse from(QuoteJob job) {
        return new QuoteJobResponse(
            job.jobId(),
            job.status().name(),
            "/api/v1/tenants/" + job.tenantId() + "/quote-jobs/" + job.jobId(),
            job.status() == QuoteJobStatus.QUEUED || job.status() == QuoteJobStatus.RUNNING ? 5 : 0,
            job.quoteId(),
            job.failureCode(),
            job.failureDetail(),
            job.progress(),
            "audit:" + job.correlationId() + ":" + job.jobId(),
            job.correlationId(),
            job.version(),
            job.updatedAt()
        );
    }
}
