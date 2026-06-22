package com.wcpe.quote;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryQuoteRepository implements QuoteRepository {
    private final Map<String, Quote> byId = new ConcurrentHashMap<>();
    private final Map<String, Quote> byIdempotencyKey = new ConcurrentHashMap<>();

    @Override
    public Optional<Quote> findByIdempotencyKey(UUID tenantId, String idempotencyKey) {
        return Optional.ofNullable(byIdempotencyKey.get(tenantId + ":" + idempotencyKey));
    }

    @Override
    public Optional<Quote> findById(UUID tenantId, UUID quoteId) {
        return Optional.ofNullable(byId.get(tenantId + ":" + quoteId));
    }

    @Override
    public Quote save(Quote quote) {
        byId.put(quote.tenantId() + ":" + quote.quoteId(), quote);
        byIdempotencyKey.put(quote.tenantId() + ":" + quote.idempotencyKey(), quote);
        return quote;
    }
}
