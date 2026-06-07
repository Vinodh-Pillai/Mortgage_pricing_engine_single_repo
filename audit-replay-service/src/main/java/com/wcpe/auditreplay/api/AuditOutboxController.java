package com.wcpe.auditreplay.api;

import com.wcpe.auditreplay.domain.OutboxEvent;
import com.wcpe.auditreplay.domain.OutboxEventStatus;
import com.wcpe.auditreplay.repository.OutboxEventRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/audit/outbox-events")
public class AuditOutboxController {

    private static final int MAX_PAGE_SIZE = 100;

    private final OutboxEventRepository repository;

    public AuditOutboxController(OutboxEventRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<OutboxEventResponse> list(
            @PathVariable UUID tenantId,
            @RequestParam(required = false) OutboxEventStatus status,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String aggregateId,
            @RequestParam(required = false) UUID correlationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        int boundedSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        return repository.findByTenantIdOrderByCreatedAtDesc(tenantId, PageRequest.of(Math.max(0, page), boundedSize))
                .stream()
                .filter(event -> status == null || event.getStatus() == status)
                .filter(event -> eventType == null || eventType.equals(event.getEventType()))
                .filter(event -> aggregateId == null || aggregateId.equals(event.getAggregateId()))
                .filter(event -> correlationId == null || correlationId.equals(event.getCorrelationId()))
                .map(OutboxEventResponse::from)
                .toList();
    }

    @GetMapping("/{eventId}")
    @Transactional(readOnly = true)
    public OutboxEventResponse get(@PathVariable UUID tenantId, @PathVariable UUID eventId) {
        return repository.findByTenantIdAndId(tenantId, eventId)
                .map(OutboxEventResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Outbox event not found"));
    }

    @PostMapping("/{eventId}/retry")
    @Transactional
    public OutboxEventResponse retry(
            @PathVariable UUID tenantId,
            @PathVariable UUID eventId,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key is required");
        }
        OutboxEvent event = repository.findByTenantIdAndId(tenantId, eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Outbox event not found"));
        if (event.getStatus() != OutboxEventStatus.FAILED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only failed outbox events can be retried");
        }
        event.queueRetry(Instant.now());
        return OutboxEventResponse.from(event);
    }

    public record OutboxEventResponse(
            UUID id,
            UUID tenantId,
            String aggregateType,
            String aggregateId,
            Long aggregateVersion,
            String eventType,
            Integer eventVersion,
            String eventKey,
            String partitionKey,
            OutboxEventStatus status,
            int attemptCount,
            Instant nextAttemptAt,
            String lastErrorCode,
            UUID correlationId,
            UUID causationId,
            String actorId,
            String idempotencyKey,
            Instant createdAt,
            Instant publishedAt,
            String integrityHash) {
        static OutboxEventResponse from(OutboxEvent event) {
            return new OutboxEventResponse(
                    event.getId(),
                    event.getTenantId(),
                    event.getAggregateType(),
                    event.getAggregateId(),
                    event.getAggregateVersion(),
                    event.getEventType(),
                    event.getEventVersion(),
                    event.getEventKey(),
                    event.getPartitionKey(),
                    event.getStatus(),
                    event.getAttemptCount(),
                    event.getNextAttemptAt(),
                    event.getLastErrorCode(),
                    event.getCorrelationId(),
                    event.getCausationId(),
                    event.getActorId(),
                    event.getIdempotencyKey(),
                    event.getCreatedAt(),
                    event.getPublishedAt(),
                    event.getIntegrityHash());
        }
    }
}
