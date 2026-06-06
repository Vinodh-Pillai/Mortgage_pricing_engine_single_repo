package com.wcpe.quote.api;

import com.wcpe.quote.ComparisonViewConfig;
import com.wcpe.quote.Quote;
import com.wcpe.quote.QuoteApplicationService;
import com.wcpe.quote.QuoteComparisonExport;
import com.wcpe.quote.QuoteComparisonResponse;
import com.wcpe.quote.QuoteCreateRequest;
import com.wcpe.quote.QuoteExplanationResponse;
import com.wcpe.quote.RankingPreviewRequest;
import com.wcpe.quote.RankingPreviewResponse;
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

    public RankingPreviewResponse postTenantRankingPreview(UUID tenantId, String correlationId, RankingPreviewRequest body) {
        RankingPreviewRequest tenantScopedBody = new RankingPreviewRequest(
            tenantId,
            body.actorId(),
            correlationId,
            body.policyRef(),
            body.candidates()
        );
        return applicationService.previewRanking(tenantScopedBody);
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

    public QuoteExplanationResponse getTenantQuoteOptionExplanation(
        UUID tenantId,
        UUID quoteId,
        UUID optionId,
        Set<String> allowedFields,
        String actorId,
        String correlationId
    ) {
        return applicationService.explainQuoteOption(tenantId, quoteId, optionId, allowedFields, actorId, correlationId);
    }
}
