package com.wcpe.tenantcontext;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TenantContextServiceTest {
    private final TenantContextService service = new TenantContextService();

    @Test
    void normalizesValidTenantContextWithTraceableRequestContext() {
        TenantContext context = service.normalize(new TenantContextInput(
            " tenant-alpha ",
            " request-123 ",
            " trace:abc-123 "
        ));

        assertThat(context.tenantId()).isEqualTo("tenant-alpha");
        assertThat(context.request().requestId()).isEqualTo("request-123");
        assertThat(context.request().traceId()).isEqualTo("trace:abc-123");
    }

    @Test
    void failsDeterministicallyWhenTenantContextInputIsMissing() {
        assertThatThrownBy(() -> service.normalize(null))
            .isInstanceOf(TenantContextValidationException.class)
            .extracting(error -> ((TenantContextValidationException) error).code())
            .isEqualTo("TENANT_CONTEXT_MISSING");
    }

    @Test
    void failsDeterministicallyWhenTenantContextInputIsMalformed() {
        assertThatThrownBy(() -> service.normalize(new TenantContextInput("tenant alpha", "request-1", "trace-1")))
            .isInstanceOf(TenantContextValidationException.class)
            .extracting(error -> ((TenantContextValidationException) error).code())
            .isEqualTo("TENANT_CONTEXT_MALFORMED");
    }

    @Test
    void keepsTenantContextRequestScopedWithoutStaticSharedState() {
        TenantContext first = service.normalize(new TenantContextInput("tenant-one", "request-1", "trace-1"));
        TenantContext second = service.normalize(new TenantContextInput("tenant-two", "request-2", "trace-2"));

        assertThat(first).isNotEqualTo(second);
        assertThat(first.tenantId()).isEqualTo("tenant-one");
        assertThat(second.tenantId()).isEqualTo("tenant-two");
        assertThat(first.request().requestId()).isEqualTo("request-1");
        assertThat(second.request().requestId()).isEqualTo("request-2");
    }

    @Test
    void doesNotInventDefaultTenantValues() {
        assertThatThrownBy(() -> service.normalize(new TenantContextInput(null, "request-1", "trace-1")))
            .isInstanceOf(TenantContextValidationException.class)
            .extracting(error -> ((TenantContextValidationException) error).code())
            .isEqualTo("TENANT_CONTEXT_MISSING");
    }
}
