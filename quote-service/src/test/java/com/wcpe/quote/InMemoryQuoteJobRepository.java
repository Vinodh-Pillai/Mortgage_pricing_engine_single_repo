package com.wcpe.quote;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryQuoteJobRepository implements QuoteJobRepository {
    private final Map<String, QuoteJob> byId = new ConcurrentHashMap<>();
    private final Map<String, QuoteJob> byIdempotencyKey = new ConcurrentHashMap<>();

    @Override
    public Optional<QuoteJob> findById(UUID tenantId, UUID jobId) {
        return Optional.ofNullable(byId.get(tenantId + ":" + jobId));
    }

    @Override
    public Optional<QuoteJob> findByIdempotencyKey(UUID tenantId, String idempotencyKey) {
        return Optional.ofNullable(byIdempotencyKey.get(tenantId + ":" + idempotencyKey));
    }

    @Override
    public QuoteJob save(QuoteJob job) {
        byId.put(job.tenantId() + ":" + job.jobId(), job);
        byIdempotencyKey.put(job.tenantId() + ":" + job.idempotencyKey(), job);
        return job;
    }
}
