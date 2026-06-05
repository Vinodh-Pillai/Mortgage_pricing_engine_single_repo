package com.wcpe.quote.api;

import com.wcpe.quote.ComparisonViewConfig;
import com.wcpe.quote.Quote;
import com.wcpe.quote.QuoteApplicationService;
import com.wcpe.quote.QuoteComparisonExport;
import com.wcpe.quote.QuoteComparisonResponse;
import com.wcpe.quote.QuoteCreateRequest;
import java.util.Set;
import java.util.UUID;

public class QuoteController {
    private final QuoteApplicationService applicationService;

    public QuoteController(QuoteApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    public QuoteCreateResponse postTenantQuote(UUID tenantId, String idempotencyKey, String correlationId, QuoteCreateRequest body) {
        QuoteCreateRequest requestScopedBody = new QuoteCreateRequest(
            tenantId,
            body.scenarioId(),
            body.scenarioVersion(),
            body.requestedLockPeriods(),
            body.filters(),
            body.presentationCurrency(),
            body.clientContext(),
            body.actorId(),
            idempotencyKey,
            correlationId,
            body.effectiveDate()
        );
        return QuoteCreateResponse.from(applicationService.createQuote(requestScopedBody));
    }

    public QuoteCreateResponse getTenantQuote(UUID tenantId, UUID quoteId) {
        return QuoteCreateResponse.from(applicationService.getQuote(tenantId, quoteId));
    }

    public QuoteComparisonResponse getTenantQuoteComparison(
        UUID tenantId,
        UUID quoteId,
        ComparisonViewConfig config,
        Set<String> allowedFields,
        String actorId,
        String correlationId
    ) {
        return applicationService.compareQuoteOptions(tenantId, quoteId, config, allowedFields, actorId, correlationId);
    }

    public QuoteComparisonExport postTenantQuoteComparisonExport(
        UUID tenantId,
        UUID quoteId,
        String idempotencyKey,
        ComparisonViewConfig config,
        Set<String> allowedFields,
        String actorId,
        String correlationId,
        String format
    ) {
        return applicationService.exportComparison(tenantId, quoteId, config, allowedFields, actorId, correlationId, idempotencyKey, format);
    }
}
