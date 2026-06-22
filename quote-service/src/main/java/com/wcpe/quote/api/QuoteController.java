package com.wcpe.quote.api;

import com.wcpe.quote.ComparisonViewConfig;
import com.wcpe.quote.Quote;
import com.wcpe.quote.QuoteApplicationService;
import com.wcpe.quote.QuoteComparisonExport;
import com.wcpe.quote.QuoteComparisonResponse;
import com.wcpe.quote.QuoteCreateRequest;
import com.wcpe.quote.QuoteCreateException;
import com.wcpe.quote.QuoteExplanationResponse;
import com.wcpe.quote.QuoteJobResponse;
import com.wcpe.quote.QuoteJobStartRequest;
import com.wcpe.quote.OutboxEvent;
import com.wcpe.quote.QuoteSnapshot;
import com.wcpe.quote.QuoteSnapshotExport;
import com.wcpe.quote.QuoteEventReplayResult;
import com.wcpe.quote.RankingPreviewRequest;
import com.wcpe.quote.RankingPreviewResponse;
import com.wcpe.quote.QuoteSelectionResponse;
import com.wcpe.quote.SelectQuoteOptionCommand;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}")
public class QuoteController {
    private final QuoteApplicationService applicationService;

    public QuoteController(QuoteApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping("/quotes")
    public QuoteCreateResponse postTenantQuote(
        @PathVariable UUID tenantId,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
        @RequestBody QuoteCreateRequest body
    ) {
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

    @GetMapping("/quotes/{quoteId}")
    public QuoteCreateResponse getTenantQuote(@PathVariable UUID tenantId, @PathVariable UUID quoteId) {
        return QuoteCreateResponse.from(applicationService.getQuote(tenantId, quoteId));
    }

    @PostMapping("/quote-jobs")
    public QuoteJobResponse postTenantQuoteJob(
        @PathVariable UUID tenantId,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
        @RequestParam(defaultValue = "false") boolean preferAsync,
        @RequestBody QuoteJobStartRequest body
    ) {
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

    @GetMapping("/quote-jobs/{jobId}")
    public QuoteJobResponse getTenantQuoteJob(@PathVariable UUID tenantId, @PathVariable UUID jobId) {
        return QuoteJobResponse.from(applicationService.getQuoteJob(tenantId, jobId));
    }

    @PostMapping("/quote-jobs/{jobId}/cancel")
    public QuoteJobResponse postTenantQuoteJobCancel(
        @PathVariable UUID tenantId,
        @PathVariable UUID jobId,
        @RequestHeader(value = "X-Actor-Id", required = false) String actorId,
        @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId
    ) {
        return QuoteJobResponse.from(applicationService.cancelQuoteJob(tenantId, jobId, actorId, correlationId));
    }

    @PostMapping("/quotes/ranking-preview")
    public RankingPreviewResponse postTenantRankingPreview(
        @PathVariable UUID tenantId,
        @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
        @RequestBody RankingPreviewRequest body
    ) {
        RankingPreviewRequest tenantScopedBody = new RankingPreviewRequest(
            tenantId,
            body.actorId(),
            correlationId,
            body.policyRef(),
            body.candidates()
        );
        return applicationService.previewRanking(tenantScopedBody);
    }

    @GetMapping("/quotes/{quoteId}/comparison")
    public QuoteComparisonResponse getTenantQuoteComparison(
        @PathVariable UUID tenantId,
        @PathVariable UUID quoteId,
        ComparisonViewConfig config,
        @RequestParam(required = false) Set<String> allowedFields,
        @RequestHeader(value = "X-Actor-Id", required = false) String actorId,
        @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId
    ) {
        return applicationService.compareQuoteOptions(tenantId, quoteId, config, allowedFields, actorId, correlationId);
    }

    @PostMapping("/quotes/{quoteId}/comparison/export")
    public QuoteComparisonExport postTenantQuoteComparisonExport(
        @PathVariable UUID tenantId,
        @PathVariable UUID quoteId,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @RequestBody ComparisonViewConfig config,
        @RequestParam(required = false) Set<String> allowedFields,
        @RequestHeader(value = "X-Actor-Id", required = false) String actorId,
        @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
        @RequestParam(defaultValue = "json") String format
    ) {
        return applicationService.exportComparison(tenantId, quoteId, config, allowedFields, actorId, correlationId, idempotencyKey, format);
    }

    @GetMapping("/quotes/{quoteId}/options/{optionId}/explanation")
    public QuoteExplanationResponse getTenantQuoteOptionExplanation(
        @PathVariable UUID tenantId,
        @PathVariable UUID quoteId,
        @PathVariable UUID optionId,
        @RequestParam(required = false) Set<String> allowedFields,
        @RequestHeader(value = "X-Actor-Id", required = false) String actorId,
        @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId
    ) {
        return applicationService.explainQuoteOption(tenantId, quoteId, optionId, allowedFields, actorId, correlationId);
    }

    @GetMapping("/quotes/{quoteId}/snapshot")
    public QuoteSnapshot getTenantQuoteSnapshot(
        @PathVariable UUID tenantId,
        @PathVariable UUID quoteId,
        @RequestHeader(value = "X-Actor-Id", required = false) String actorId,
        @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId
    ) {
        return applicationService.getQuoteSnapshot(tenantId, quoteId, actorId, correlationId);
    }

    @PostMapping("/quotes/{quoteId}/snapshot/export")
    public QuoteSnapshotExport postTenantQuoteSnapshotExport(
        @PathVariable UUID tenantId,
        @PathVariable UUID quoteId,
        @RequestParam String redactionProfile,
        @RequestParam(defaultValue = "false") boolean nonRedacted,
        @RequestHeader(value = "X-Actor-Id", required = false) String actorId,
        @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId
    ) {
        return applicationService.exportQuoteSnapshot(tenantId, quoteId, redactionProfile, nonRedacted, actorId, correlationId);
    }

    @PostMapping("/quotes/{quoteId}/selection")
    public QuoteSelectionResponse postTenantQuoteSelection(
        @PathVariable UUID tenantId,
        @PathVariable UUID quoteId,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
        @RequestBody SelectQuoteOptionCommand body
    ) {
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

    @GetMapping("/quotes/{quoteId}/events")
    public List<OutboxEvent> getTenantQuoteEvents(@PathVariable UUID tenantId, @PathVariable UUID quoteId) {
        return applicationService.quoteEvents(tenantId, quoteId);
    }

    @PostMapping("/quote-events/{eventId}/replay")
    public QuoteEventReplayResult postTenantQuoteEventReplay(
        @PathVariable UUID tenantId,
        @PathVariable String eventId,
        @RequestHeader(value = "X-Actor-Id", required = false) String actorId,
        @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
        @RequestParam String reasonForAccess
    ) {
        return applicationService.replayQuoteEvent(tenantId, eventId, actorId, correlationId, reasonForAccess);
    }

    @ExceptionHandler(QuoteCreateException.class)
    public ResponseEntity<Map<String, String>> quoteError(QuoteCreateException ex) {
        HttpStatus status = "NOT_FOUND".equals(ex.code()) ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(Map.of("code", ex.code(), "message", ex.getMessage()));
    }
}
