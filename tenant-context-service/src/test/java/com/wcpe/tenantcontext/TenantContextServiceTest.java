package com.wcpe.tenantcontext;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

class TenantContextServiceTest {
    private final TenantContextService service = new TenantContextService();

    @Test
    void resolvesValidTenantContextWithActorPolicyAndCorrelationFields() {
        TenantContext context = service.resolve("tenant-alpha", validInput());

        assertThat(context.tenantId()).isEqualTo("tenant-alpha");
        assertThat(context.request().requestId()).isEqualTo("request-123");
        assertThat(context.request().traceId()).isEqualTo("trace:abc-123");
        assertThat(context.request().correlationId()).isEqualTo("correlation-123");
        assertThat(context.request().causationId()).isEqualTo("cause-123");
        assertThat(context.request().idempotencyKey()).isEqualTo("idem-123");
        assertThat(context.request().requestSource()).isEqualTo("partner-api");
        assertThat(context.actor().actorId()).isEqualTo("actor-123");
        assertThat(context.actor().actorType()).isEqualTo("USER");
        assertThat(context.roles()).containsExactly("pricing-analyst");
        assertThat(context.scopes()).containsExactly("tenant:context:read");
        assertThat(context.channel()).isEqualTo("retail");
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
        TenantContextInput input = new TenantContextInput(
            "tenant alpha", "request-1", "trace-1", "actor-1", "USER", List.of("role"),
            List.of("tenant:context:read"), "retail", "correlation-1", null, null, "api", List.of("tenant alpha"),
            "tenant alpha", "ACTIVE");

        assertThatThrownBy(() -> service.resolve("tenant alpha", input))
            .isInstanceOf(TenantContextValidationException.class)
            .extracting(error -> ((TenantContextValidationException) error).code())
            .isEqualTo("TENANT_CONTEXT_MALFORMED");
    }

    @Test
    void keepsTenantContextRequestScopedWithoutStaticSharedState() {
        TenantContext first = service.resolve("tenant-one", inputForTenant("tenant-one", "request-1", "trace-1", "correlation-1"));
        TenantContext second = service.resolve("tenant-two", inputForTenant("tenant-two", "request-2", "trace-2", "correlation-2"));

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

    @Test
    void failsClosedWhenPathTenantDiffersFromAllowedOrSelectedTenant() {
        assertThatThrownBy(() -> service.resolve("tenant-beta", validInput()))
            .isInstanceOf(TenantContextValidationException.class)
            .extracting(error -> ((TenantContextValidationException) error).code())
            .isEqualTo("TENANT_ACCESS_DENIED");
    }

    @Test
    void failsClosedWhenRequiredContextScopeIsMissing() {
        TenantContextInput missingScope = new TenantContextInput(
            "tenant-alpha", "request-123", "trace:abc-123", "actor-123", "USER", List.of("pricing-analyst"),
            List.of("other:scope"), "retail", "correlation-123", "cause-123", "idem-123", "partner-api",
            List.of("tenant-alpha"), "tenant-alpha", "ACTIVE");

        assertThatThrownBy(() -> service.resolve("tenant-alpha", missingScope))
            .isInstanceOf(TenantContextValidationException.class)
            .extracting(error -> ((TenantContextValidationException) error).code())
            .isEqualTo("TENANT_ACCESS_DENIED");
    }

    @Test
    void failsClosedForSuspendedTenantWithoutDefaultFallback() {
        TenantContextInput suspended = new TenantContextInput(
            "tenant-alpha", "request-123", "trace:abc-123", "actor-123", "USER", List.of("pricing-analyst"),
            List.of("tenant:context:read"), "retail", "correlation-123", "cause-123", "idem-123", "partner-api",
            List.of("tenant-alpha"), "tenant-alpha", "SUSPENDED");

        assertThatThrownBy(() -> service.resolve("tenant-alpha", suspended))
            .isInstanceOf(TenantContextValidationException.class)
            .extracting(error -> ((TenantContextValidationException) error).code())
            .isEqualTo("TENANT_SUSPENDED");
    }

    @Test
    void exposesImmutableRoleAndScopeCollections() {
        ArrayList<String> roles = new ArrayList<>(List.of("pricing-analyst"));
        ArrayList<String> scopes = new ArrayList<>(List.of("tenant:context:read"));
        TenantContextInput input = new TenantContextInput(
            "tenant-alpha", "request-123", "trace:abc-123", "actor-123", "USER", roles, scopes,
            "retail", "correlation-123", "cause-123", "idem-123", "partner-api", List.of("tenant-alpha"),
            "tenant-alpha", "ACTIVE");

        TenantContext context = service.resolve("tenant-alpha", input);
        roles.add("mutated-role");
        scopes.add("mutated-scope");

        assertThat(context.roles()).containsExactly("pricing-analyst");
        assertThat(context.scopes()).containsExactly("tenant:context:read");
        assertThatThrownBy(() -> context.roles().add("blocked"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    private static TenantContextInput validInput() {
        return new TenantContextInput(
            " tenant-alpha ",
            " request-123 ",
            " trace:abc-123 ",
            " actor-123 ",
            " USER ",
            List.of(" pricing-analyst "),
            List.of(" tenant:context:read "),
            " retail ",
            " correlation-123 ",
            " cause-123 ",
            " idem-123 ",
            " partner-api ",
            List.of(" tenant-alpha "),
            " tenant-alpha ",
            " ACTIVE "
        );
    }

    private static TenantContextInput inputForTenant(String tenantId, String requestId, String traceId, String correlationId) {
        return new TenantContextInput(
            tenantId, requestId, traceId, "actor-123", "USER", List.of("pricing-analyst"),
            List.of("tenant:context:read"), "retail", correlationId, "cause-123", "idem-123", "partner-api",
            List.of(tenantId), tenantId, "ACTIVE");
    }
}
