package com.wcpe.quote;

import java.util.Optional;
import java.util.UUID;

public interface QuoteCache {
    Optional<Quote> get(UUID tenantId, UUID quoteId, int version);

    void put(Quote quote);
}
