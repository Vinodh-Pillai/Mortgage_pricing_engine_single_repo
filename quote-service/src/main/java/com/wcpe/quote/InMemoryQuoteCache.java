package com.wcpe.quote;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class InMemoryQuoteCache implements QuoteCache {
    private final Map<String, Quote> values = new LinkedHashMap<>();
    private boolean available = true;

    @Override
    public Optional<Quote> get(UUID tenantId, UUID quoteId, int version) {
        if (!available) {
            return Optional.empty();
        }
        return Optional.ofNullable(values.get(key(tenantId, quoteId, version)));
    }

    @Override
    public void put(Quote quote) {
        if (available) {
            values.put(key(quote.tenantId(), quote.quoteId(), quote.version()), quote);
        }
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    private static String key(UUID tenantId, UUID quoteId, int version) {
        return "quote:" + tenantId + ":" + quoteId + ":summary:v" + version;
    }
}
