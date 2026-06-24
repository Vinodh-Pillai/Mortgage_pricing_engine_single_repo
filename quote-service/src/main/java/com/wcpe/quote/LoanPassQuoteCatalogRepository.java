package com.wcpe.quote;

import com.wcpe.quote.LoanPassQuoteModels.CatalogSnapshot;
import java.util.Optional;
import java.util.UUID;

public interface LoanPassQuoteCatalogRepository {
    Optional<CatalogSnapshot> activeSnapshot(UUID tenantId);

    CatalogSnapshot saveSnapshot(CatalogSnapshot snapshot);
}
