package com.wcpe.auditreplay.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.wcpe.auditreplay.CorrelationContext;
import com.wcpe.auditreplay.application.LockReplayCommand;
import com.wcpe.auditreplay.application.LockReplayResult;
import com.wcpe.auditreplay.application.LockReplayService;
import com.wcpe.auditreplay.domain.LockReplayMode;
import com.wcpe.auditreplay.domain.LockReplayRun;
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
public class LockReplayController {

    private final LockReplayService lockReplayService;

    public LockReplayController(LockReplayService lockReplayService) {
        this.lockReplayService = lockReplayService;
    }

    @PostMapping("/api/v1/tenants/{tenantId}/lock-replays")
    @Transactional
    public LockReplayResponse create(
            @PathVariable UUID tenantId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody LockReplayRequest request) {
        requireAuthorization(authorization);
        requireText(idempotencyKey, "Idempotency-Key is required");
        try {
            return LockReplayResponse.from(lockReplayService.startReplay(request.toCommand(tenantId, idempotencyKey)));
        } catch (LockReplayService.IdempotencyConflictException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage(), ex);
        } catch (LockReplayService.LockReplayNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @GetMapping("/api/v1/tenants/{tenantId}/lock-replays/{runId}")
    @Transactional(readOnly = true)
    public LockReplayResponse get(
            @PathVariable UUID tenantId,
            @PathVariable UUID runId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        requireAuthorization(authorization);
        try {
            return LockReplayResponse.from(lockReplayService.getReplay(tenantId, runId));
        } catch (LockReplayService.LockReplayNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
    }

    @GetMapping("/api/v1/tenants/{tenantId}/lock-replays/{runId}/diff")
    @Transactional(readOnly = true)
    public JsonNode diff(
            @PathVariable UUID tenantId,
            @PathVariable UUID runId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        requireAuthorization(authorization);
        try {
            return lockReplayService.getDiff(tenantId, runId);
        } catch (LockReplayService.LockReplayNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
    }

    public record LockReplayRequest(
            UUID sourceAuditRecordId,
            String sourceLockId,
            String decisionType,
            LockReplayMode mode,
            String reason,
            String requestedBy,
            String expectedHash) {

        LockReplayCommand toCommand(UUID tenantId, String idempotencyKey) {
            CorrelationContext.Data context = CorrelationContext.get();
            if (context == null || context.correlationId() == null || context.requestId() == null) {
                throw new IllegalArgumentException("CorrelationContext is required");
            }
            return new LockReplayCommand(
                    tenantId,
                    sourceAuditRecordId,
                    sourceLockId,
                    decisionType,
                    mode,
                    reason,
                    requestedBy,
                    expectedHash,
                    context.correlationId().getValue(),
                    UUID.fromString(context.requestId().toString()),
                    idempotencyKey);
        }
    }

    public record LockReplayResponse(
            UUID runId,
            UUID tenantId,
            String sourceLockId,
            UUID sourceAuditRecordId,
            String requestedBy,
            String reason,
            String mode,
            String decisionType,
            String status,
            String originalHash,
            String replayHash,
            String mismatchCode,
            String marketSnapshotRef,
            String extensionPolicyRef,
            String eventSequenceRef,
            String startedAt,
            String completedAt,
            String evidenceExportRef,
            JsonNode diff,
            String correlationId) {

        static LockReplayResponse from(LockReplayResult result) {
            LockReplayRun run = result.run();
            return new LockReplayResponse(
                    run.getRunId(),
                    run.getTenantId(),
                    run.getSourceLockId(),
                    run.getSourceAuditRecordId(),
                    run.getRequestedBy(),
                    run.getReason(),
                    run.getMode().name(),
                    run.getDecisionType(),
                    run.getStatus().name(),
                    run.getOriginalHash(),
                    run.getReplayHash(),
                    run.getMismatchCode(),
                    run.getMarketSnapshotRef(),
                    run.getExtensionPolicyRef(),
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
