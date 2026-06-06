package com.wcpe.quote.api;

import com.wcpe.quote.ComparisonViewConfig;
import com.wcpe.quote.Quote;
import com.wcpe.quote.QuoteApplicationService;
import com.wcpe.quote.QuoteComparisonExport;
import com.wcpe.quote.QuoteComparisonResponse;
import com.wcpe.quote.QuoteCreateRequest;
import com.wcpe.quote.QuoteExplanationResponse;
import com.wcpe.quote.QuoteJobResponse;
import com.wcpe.quote.QuoteJobStartRequest;
import com.wcpe.quote.QuoteSnapshot;
import com.wcpe.quote.QuoteSnapshotExport;
import com.wcpe.quote.RankingPreviewRequest;
import com.wcpe.quote.RankingPreviewResponse;
import com.wcpe.quote.QuoteSelectionResponse;
import com.wcpe.quote.SelectQuoteOptionCommand;
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

    public QuoteJobResponse postTenantQuoteJob(UUID tenantId, String idempotencyKey, String correlationId, boolean preferAsync, QuoteJobStartRequest body) {
        QuoteJobStartRequest tenantScopedBody = new QuoteJobStartRequest(
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
            body.effectiveDate(),
            preferAsync
        );
        return QuoteJobResponse.from(applicationService.startQuoteJob(tenantScopedBody));
    }

    public QuoteJobResponse getTenantQuoteJob(UUID tenantId, UUID jobId) {
        return QuoteJobResponse.from(applicationService.getQuoteJob(tenantId, jobId));
    }

    public QuoteJobResponse postTenantQuoteJobCancel(UUID tenantId, UUID jobId, String actorId, String correlationId) {
        return QuoteJobResponse.from(applicationService.cancelQuoteJob(tenantId, jobId, actorId, correlationId));
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

    public QuoteSnapshot getTenantQuoteSnapshot(UUID tenantId, UUID quoteId, String actorId, String correlationId) {
        return applicationService.getQuoteSnapshot(tenantId, quoteId, actorId, correlationId);
    }

    public QuoteSnapshotExport postTenantQuoteSnapshotExport(
        UUID tenantId,
        UUID quoteId,
        String redactionProfile,
        boolean nonRedacted,
        String actorId,
        String correlationId
    ) {
        return applicationService.exportQuoteSnapshot(tenantId, quoteId, redactionProfile, nonRedacted, actorId, correlationId);
    }

    public QuoteSelectionResponse postTenantQuoteSelection(UUID tenantId, UUID quoteId, String idempotencyKey, String correlationId, SelectQuoteOptionCommand body) {
        SelectQuoteOptionCommand tenantScopedBody = new SelectQuoteOptionCommand(
            tenantId,
            quoteId,
            body.optionId(),
            body.selectionIntent(),
            body.acknowledgements(),
            body.nonTopRankReason(),
            body.policy(),
            body.actorId(),
            idempotencyKey,
            correlationId,
            body.clientContext()
        );
        return QuoteSelectionResponse.from(applicationService.selectQuoteOption(tenantScopedBody));
    }
}
