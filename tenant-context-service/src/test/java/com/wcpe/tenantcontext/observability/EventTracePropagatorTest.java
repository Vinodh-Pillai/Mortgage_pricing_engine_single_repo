package com.wcpe.tenantcontext.observability;

import static org.assertj.core.api.Assertions.*;

import com.wcpe.tenantcontext.ActorRef;
import com.wcpe.tenantcontext.RequestContext;
import com.wcpe.tenantcontext.TenantContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class EventTracePropagatorTest {
    private final EventTracePropagator propagator = new EventTracePropagator();

    @Test
    void returnsCorrelationAndCausationHeadersForDownstreamBoundaries() {
        Map<String, String> headers = propagator.headers(context("corr-1", "cause-1"), "event-1");

        assertThat(headers).containsEntry("X-Correlation-ID", "corr-1")
            .containsEntry("X-Causation-ID", "cause-1")
            .containsKey("traceparent");
    }

    @Test
    void usesBoundaryIdAsCausationWhenRequestCausationIsMissing() {
        assertThat(propagator.causationId(context("corr-1", " "), "event-123")).isEqualTo("event-123");
    }

    private static TenantContext context(String correlationId, String causationId) {
        return new TenantContext("tenant-alpha", new RequestContext("request-1", "trace-abc-123", correlationId,
            causationId, "idem-1", "api"), new ActorRef("actor-1", "SERVICE_ACCOUNT"), List.of("ops"),
            List.of("tenant:context:read"), "api");
    }
}
