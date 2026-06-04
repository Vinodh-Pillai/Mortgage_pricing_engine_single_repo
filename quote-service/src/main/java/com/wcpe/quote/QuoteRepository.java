package com.wcpe.quote;

import java.util.Optional;
import java.util.UUID;

public interface QuoteRepository {
    Optional<Quote> findByIdempotencyKey(UUID tenantId, String idempotencyKey);

    Optional<Quote> findById(UUID tenantId, UUID quoteId);

    Quote save(Quote quote);
}
