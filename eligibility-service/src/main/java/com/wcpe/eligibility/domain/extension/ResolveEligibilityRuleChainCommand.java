package com.wcpe.eligibility.domain.extension;

import com.wcpe.eligibility.domain.models.ProductFamily;
import com.wcpe.eligibility.domain.models.QuoteType;

import java.time.LocalDate;
import java.util.UUID;

public record ResolveEligibilityRuleChainCommand(
    ProductFamily productFamily,
    QuoteType quoteType,
    UUID tenantId,
    LocalDate asOfDate
) {}
