package com.wcpe.auditreplay.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.auditreplay.domain.OutboxEvent;
import com.wcpe.auditreplay.repository.OutboxEventRepository;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OutboxRecorderTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void persistsEventOnceForSameTenantEventKeyVersion() throws Exception {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        when(repository.findByTenantIdAndEventKeyAndEventVersion(any(), any(), any())).thenReturn(Optional.empty());
        when(repository.save(any(OutboxEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        OutboxRecorder recorder = new OutboxRecorder(repository, objectMapper);
        UUID tenantId = UUID.randomUUID();

        OutboxEvent event = recorder.record(new OutboxRecordCommand(
                tenantId,
                "quote",
                "quote-123",
                2L,
                "audit_record.created",
                1,
                "tenant:quote-123:2",
                "tenant:quote-123",
                Map.of("id", "quote-123", "status", "COMPLETED"),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "actor-1",
                "idem-quote-123",
                "audit-record-created-v1"));

        assertEquals(tenantId, event.getTenantId());
        assertEquals("tenant:quote-123:2", event.getEventKey());
        assertEquals("idem-quote-123", event.getIdempotencyKey());
        assertNotNull(event.getIntegrityHash());
        JsonNode headers = objectMapper.readTree(new String(event.getHeadersJson(), StandardCharsets.UTF_8));
        JsonNode envelope = objectMapper.readTree(new String(event.getPayloadJson(), StandardCharsets.UTF_8));
        assertEquals("audit_record.created", envelope.get("eventType").asText());
        assertNotNull(envelope.get("payloadHash").asText());
        assertEquals("audit_record.created", headers.get("x-event-type").asText());
        assertEquals(event.getIntegrityHash(), headers.get("x-integrity-hash").asText());
        verify(repository).save(any(OutboxEvent.class));
    }
}
