package com.wcpe.auditreplay.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.wcpe.auditreplay.CorrelationContext;
import com.wcpe.auditreplay.application.QuoteReplayCommand;
import com.wcpe.auditreplay.application.QuoteReplayResult;
import com.wcpe.auditreplay.application.QuoteReplayService;
import com.wcpe.auditreplay.domain.QuoteReplayMode;
import com.wcpe.auditreplay.domain.QuoteReplayRun;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class QuoteReplayController {

    private final QuoteReplayService quoteReplayService;

    public QuoteReplayController(QuoteReplayService quoteReplayService) {
        this.quoteReplayService = quoteReplayService;
    }

    @PostMapping("/api/v1/tenants/{tenantId}/quote-replays")
    @Transactional
    public QuoteReplayResponse create(
            @PathVariable UUID tenantId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody QuoteReplayRequest request) {
        requireAuthorization(authorization);
        requireText(idempotencyKey, "Idempotency-Key is required");
        try {
            return QuoteReplayResponse.from(quoteReplayService.startReplay(request.toCommand(tenantId, idempotencyKey)));
        } catch (QuoteReplayService.IdempotencyConflictException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage(), ex);
        } catch (QuoteReplayService.QuoteReplayNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @GetMapping("/api/v1/tenants/{tenantId}/quote-replays/{runId}")
    @Transactional(readOnly = true)
    public QuoteReplayResponse get(
            @PathVariable UUID tenantId,
            @PathVariable UUID runId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        requireAuthorization(authorization);
        try {
            return QuoteReplayResponse.from(quoteReplayService.getReplay(tenantId, runId));
        } catch (QuoteReplayService.QuoteReplayNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
    }

    @GetMapping("/api/v1/tenants/{tenantId}/quote-replays/{runId}/diff")
    @Transactional(readOnly = true)
    public JsonNode diff(
            @PathVariable UUID tenantId,
            @PathVariable UUID runId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        requireAuthorization(authorization);
        try {
            return quoteReplayService.getDiff(tenantId, runId);
        } catch (QuoteReplayService.QuoteReplayNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
    }

    public record QuoteReplayRequest(
            UUID sourceAuditRecordId,
            String sourceQuoteId,
            QuoteReplayMode mode,
            String reason,
            String requestedBy,
            String expectedHash) {

        QuoteReplayCommand toCommand(UUID tenantId, String idempotencyKey) {
            CorrelationContext.Data context = CorrelationContext.get();
            if (context == null || context.correlationId() == null || context.requestId() == null) {
                throw new IllegalArgumentException("CorrelationContext is required");
            }
            return new QuoteReplayCommand(
                    tenantId,
                    sourceAuditRecordId,
                    sourceQuoteId,
                    mode,
                    reason,
                    requestedBy,
                    expectedHash,
                    context.correlationId().getValue(),
                    UUID.fromString(context.requestId().toString()),
                    idempotencyKey);
        }
    }

    public record QuoteReplayResponse(
            UUID runId,
            UUID tenantId,
            String sourceQuoteId,
            UUID sourceAuditRecordId,
            String requestedBy,
            String reason,
            String mode,
            String status,
            String originalHash,
            String replayHash,
            String mismatchCode,
            String eventSequenceRef,
            String startedAt,
            String completedAt,
            String evidenceExportRef,
            JsonNode diff,
            String correlationId) {

        static QuoteReplayResponse from(QuoteReplayResult result) {
            QuoteReplayRun run = result.run();
            return new QuoteReplayResponse(
                    run.getRunId(),
                    run.getTenantId(),
                    run.getSourceQuoteId(),
                    run.getSourceAuditRecordId(),
                    run.getRequestedBy(),
                    run.getReason(),
                    run.getMode().name(),
                    run.getStatus().name(),
                    run.getOriginalHash(),
                    run.getReplayHash(),
                    run.getMismatchCode(),
                    run.getEventSequenceRef(),
                    run.getStartedAt().toString(),
                    run.getCompletedAt().toString(),
                    result.evidenceExportRef(),
                    result.diff(),
                    run.getCorrelationId().toString());
        }
    }

    private static void requireAuthorization(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authorization header is required");
        }
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
    }
}
