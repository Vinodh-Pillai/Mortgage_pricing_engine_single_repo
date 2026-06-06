package com.wcpe.quote;

import java.util.Optional;
import java.util.UUID;

public interface QuoteJobRepository {
    Optional<QuoteJob> findById(UUID tenantId, UUID jobId);

    Optional<QuoteJob> findByIdempotencyKey(UUID tenantId, String idempotencyKey);

    QuoteJob save(QuoteJob job);
}
