package com.wcpe.quote;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryQuoteSnapshotRepository implements QuoteSnapshotRepository {
    private final Map<String, QuoteSnapshot> byQuoteId = new ConcurrentHashMap<>();

    @Override
    public Optional<QuoteSnapshot> findByQuoteId(UUID tenantId, UUID quoteId) {
        return Optional.ofNullable(byQuoteId.get(key(tenantId, quoteId)));
    }

    @Override
    public QuoteSnapshot saveNew(QuoteSnapshot snapshot) {
        String key = key(snapshot.tenantId(), snapshot.quoteId());
        QuoteSnapshot existing = byQuoteId.putIfAbsent(key, snapshot);
        if (existing != null) {
            throw new QuoteCreateException("QUOTE_SNAPSHOT_ALREADY_EXISTS", "Quote snapshot is immutable and already exists");
        }
        return snapshot;
    }

    private static String key(UUID tenantId, UUID quoteId) {
        return tenantId + ":" + quoteId;
    }
}
