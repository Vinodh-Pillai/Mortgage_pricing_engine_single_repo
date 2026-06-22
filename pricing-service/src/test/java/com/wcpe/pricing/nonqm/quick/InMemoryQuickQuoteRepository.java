package com.wcpe.pricing.nonqm.quick;

import com.wcpe.pricing.nonqm.quick.NonQmQuickPricerApi.QuickQuoteRepository;
import com.wcpe.pricing.nonqm.quick.NonQmQuickPricerApi.QuickQuoteResult;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryQuickQuoteRepository implements QuickQuoteRepository {
    private final Map<String, QuickQuoteResult> quotes = new ConcurrentHashMap<>();

    @Override
    public void save(QuickQuoteResult result) {
        quotes.put(result.quickQuoteId(), result);
    }

    @Override
    public Optional<QuickQuoteResult> findById(String quickQuoteId) {
        return Optional.ofNullable(quotes.get(quickQuoteId));
    }
}
