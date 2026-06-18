package com.wcpe.catalog.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class TenantProductAuthorizationServiceTest {
    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant NOW = Instant.parse("2026-06-13T12:00:00Z");

    @Test
    void authorizedProductAllowed() {
        TenantProductAuthorizationService service = service(List.of(active("CONF_30YR", "FNMA", "RETAIL", null)));

        assertThat(service.isAuthorized(TENANT, "CONF_30YR", "FNMA", "RETAIL")).isTrue();
        assertThat(service.isAuthorized(TENANT, "CONF_15YR", "FNMA", "RETAIL")).isFalse();
    }

    @Test
    void unauthorizedProductFiltered() {
        TenantProductAuthorizationService service = service(List.of(active("CONF_30YR", "FNMA", "RETAIL", null)));
        List<AuthorizedProductCandidate> candidates = List.of(
            new AuthorizedProductCandidate("CONF_30YR", "FNMA", "RETAIL"),
            new AuthorizedProductCandidate("JUMBO_30YR", "CHASE", "RETAIL")
        );

        assertThat(service.filterAuthorized(TENANT, candidates, "RETAIL"))
            .extracting(AuthorizedProductCandidate::productCode)
            .containsExactly("CONF_30YR");
    }

    @Test
    void investorChannelWildcard() {
        TenantProductAuthorizationService service = service(List.of(active("CONF_30YR", null, null, null)));

        assertThat(service.isAuthorized(TENANT, "CONF_30YR", "FNMA", "RETAIL")).isTrue();
        assertThat(service.isAuthorized(TENANT, "CONF_30YR", "FHLMC", "CORR")).isTrue();
    }

    @Test
    void expiredAndPendingAuthorizationFiltered() {
        TenantProductAuthorizationService service = service(List.of(
            active("CONF_30YR", "FNMA", "RETAIL", Instant.parse("2026-01-01T00:00:00Z")),
            pending("CONF_15YR", "FNMA", "RETAIL")
        ));

        assertThat(service.isAuthorized(TENANT, "CONF_30YR", "FNMA", "RETAIL")).isFalse();
        assertThat(service.isAuthorized(TENANT, "CONF_15YR", "FNMA", "RETAIL")).isFalse();
    }

    @Test
    void effectiveDatedAuthorizationIsDeterministicAsOf() {
        TenantProductAuthorizationService service = service(List.of(
            new TenantProductAuthorization(TENANT, "CONF_30YR", "FNMA", "RETAIL", "ACTIVE", Instant.parse("2026-02-01T00:00:00Z"), "admin", Instant.parse("2026-04-01T00:00:00Z"), "windowed")
        ));

        assertThat(service.isAuthorized(TENANT, "CONF_30YR", "FNMA", "RETAIL", Instant.parse("2026-01-31T23:59:59Z"))).isFalse();
        assertThat(service.isAuthorized(TENANT, "CONF_30YR", "FNMA", "RETAIL", Instant.parse("2026-03-01T00:00:00Z"))).isTrue();
        assertThat(service.isAuthorized(TENANT, "CONF_30YR", "FNMA", "RETAIL", Instant.parse("2026-04-01T00:00:00Z"))).isFalse();
    }

    @Test
    void missingAuthorizationSetFailsClosedForExplicitProductLookup() {
        TenantProductAuthorizationService service = service(List.of());

        assertThat(service.getAuthorizedRulesAsOf(TENANT, NOW)).isEmpty();
        assertThat(service.isAuthorized(TENANT, "CONF_30YR", "FNMA", "RETAIL", NOW)).isFalse();
    }

    @Test
    void cacheInvalidationOnChange() {
        AtomicInteger loads = new AtomicInteger();
        Cache<UUID, List<TenantProductAuthorization>> cache = Caffeine.newBuilder()
            .expireAfterWrite(TenantProductAuthorizationService.AUTH_CACHE_TTL)
            .build();
        TenantProductAuthorizationService service = new TenantProductAuthorizationService(
            null,
            cache,
            tenant -> {
                loads.incrementAndGet();
                return List.of(active("CONF_30YR", "FNMA", "RETAIL", null));
            },
            null
        );

        assertThat(service.isAuthorized(TENANT, "CONF_30YR", "FNMA", "RETAIL")).isTrue();
        assertThat(service.isAuthorized(TENANT, "CONF_30YR", "FNMA", "RETAIL")).isTrue();
        assertThat(loads).hasValue(1);

        service.onAuthorizationChanged(new TenantAuthorizationChangedEvent(TENANT));
        assertThat(service.isAuthorized(TENANT, "CONF_30YR", "FNMA", "RETAIL")).isTrue();
        assertThat(loads).hasValue(2);
        assertThat(service.cacheTtl()).isEqualTo(TenantProductAuthorizationService.AUTH_CACHE_TTL);
    }

    private static TenantProductAuthorizationService service(List<TenantProductAuthorization> authorizations) {
        return new TenantProductAuthorizationService(null, Caffeine.newBuilder().expireAfterWrite(TenantProductAuthorizationService.AUTH_CACHE_TTL).build(), tenant -> authorizations, null);
    }

    private static TenantProductAuthorization active(String product, String investor, String channel, Instant expiresAt) {
        return new TenantProductAuthorization(TENANT, product, investor, channel, "ACTIVE", NOW, "admin", expiresAt, null);
    }

    private static TenantProductAuthorization pending(String product, String investor, String channel) {
        return new TenantProductAuthorization(TENANT, product, investor, channel, "PENDING", NOW, "admin", null, null);
    }
}
