package com.wcpe.auditreplay.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wcpe.auditreplay.domain.OutboxEvent;
import com.wcpe.auditreplay.repository.OutboxEventRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuditOutboxControllerTest {

    private final OutboxEventRepository repository = mock(OutboxEventRepository.class);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AuditOutboxController(repository)).build();

    @Test
    void getsTenantScopedEvent() throws Exception {
        UUID tenantId = UUID.randomUUID();
        OutboxEvent event = event(tenantId);
        when(repository.findByTenantIdAndId(tenantId, event.getId())).thenReturn(Optional.of(event));

        mockMvc.perform(get("/api/v1/tenants/{tenantId}/audit/outbox-events/{eventId}", tenantId, event.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(tenantId.toString()))
                .andExpect(jsonPath("$.eventType").value("outbox_pattern.completed.v1"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void retryRequiresFailedEvent() throws Exception {
        UUID tenantId = UUID.randomUUID();
        OutboxEvent event = event(tenantId);
        when(repository.findByTenantIdAndId(any(), any())).thenReturn(Optional.of(event));

        mockMvc.perform(post("/api/v1/tenants/{tenantId}/audit/outbox-events/{eventId}/retry", tenantId, event.getId())
                        .header("Idempotency-Key", "retry-1"))
                .andExpect(status().isConflict());

        event.markInFlight();
        event.markFailed("BROKER_DOWN", "broker unavailable", Instant.parse("2026-06-06T19:00:00Z"), 3);

        mockMvc.perform(post("/api/v1/tenants/{tenantId}/audit/outbox-events/{eventId}/retry", tenantId, event.getId())
                        .header("Idempotency-Key", "retry-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    private OutboxEvent event(UUID tenantId) {
        return OutboxEvent.createPending(
                tenantId,
                "audit",
                "aggregate-1",
                1L,
                "outbox_pattern.completed.v1",
                1,
                "tenant:event:1",
                "tenant:aggregate-1",
                "{\"id\":\"aggregate-1\"}".getBytes(StandardCharsets.UTF_8),
                "{\"tenantId\":\"tenant\"}".getBytes(StandardCharsets.UTF_8),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "actor-1",
                "idem-1");
    }
}
