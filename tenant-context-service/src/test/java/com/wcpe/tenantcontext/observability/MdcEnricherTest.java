package com.wcpe.tenantcontext.observability;

import static org.assertj.core.api.Assertions.*;

import com.wcpe.tenantcontext.ActorRef;
import com.wcpe.tenantcontext.RequestContext;
import com.wcpe.tenantcontext.TenantContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class MdcEnricherTest {
    private final MdcEnricher enricher = new MdcEnricher();

    @Test
    void emitsBoundedStructuredCorrelationFields() {
        Map<String, String> fields = enricher.fields(context(), Map.of("error_code", "TENANT_ACCESS_DENIED"));

        assertThat(fields).containsEntry("service", "tenant-context-service")
            .containsEntry("tenant_id", "tenant-alpha")
            .containsEntry("correlation_id", "corr-1")
            .containsEntry("causation_id", "cause-1")
            .containsEntry("trace_id", "trace-1")
            .containsEntry("actor_type", "USER")
            .containsEntry("error_code", "TENANT_ACCESS_DENIED");
    }

    @Test
    void redactsSecretsPiiAuthorizationHeadersCookiesAndRawJwtValues() {
        Map<String, String> fields = enricher.fields(context(), Map.of(
            "Authorization", "Bearer secret-token",
            "Cookie", "SESSION=abc",
            "borrowerName", "Ada Lovelace",
            "message", "safe summary"
        ));

        assertThat(fields).containsEntry("Authorization", LogRedactionPolicy.REDACTED)
            .containsEntry("Cookie", LogRedactionPolicy.REDACTED)
            .containsEntry("borrowerName", LogRedactionPolicy.REDACTED)
            .containsEntry("message", "safe summary");
        assertThat(fields.toString()).doesNotContain("secret-token").doesNotContain("Ada Lovelace");
    }

    private static TenantContext context() {
        return new TenantContext("tenant-alpha", new RequestContext("request-1", "trace-1", "corr-1", "cause-1",
            "idem-1", "api"), new ActorRef("actor-1", "USER"), List.of("support"), List.of("tenant:context:read"),
            "support-api");
    }
}
