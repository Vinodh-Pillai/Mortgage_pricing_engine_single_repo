package com.wcpe.quote;

import java.util.Optional;
import java.util.UUID;

public interface QuoteSnapshotRepository {
    Optional<QuoteSnapshot> findByQuoteId(UUID tenantId, UUID quoteId);

    QuoteSnapshot saveNew(QuoteSnapshot snapshot);
}
