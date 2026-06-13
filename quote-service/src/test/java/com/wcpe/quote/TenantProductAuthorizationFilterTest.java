package com.wcpe.quote;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class TenantProductAuthorizationFilterTest {
    @Test
    void isAuthorizedHonorsWildcardRules() {
        TenantProductAuthorizationFilter filter = new TenantProductAuthorizationFilter(tenant -> List.of(
            new TenantProductAuthorizationRule("product-A", null, null, "ACTIVE", null)
        ));

        assertThat(filter.isAuthorized(QuoteTestSupport.TENANT, "product-A", "investor-A", "retail")).isTrue();
        assertThat(filter.isAuthorized(QuoteTestSupport.TENANT, "product-B", "investor-B", "retail")).isFalse();
    }

    @Test
    void filterAuthorizedKeepsOnlyTenantAuthorizedChannelCandidates() {
        TenantProductAuthorizationFilter filter = new TenantProductAuthorizationFilter(tenant -> List.of(
            new TenantProductAuthorizationRule("product-B", "investor-B", "retail", "ACTIVE", null)
        ));

        assertThat(filter.filterAuthorized(QuoteTestSupport.TENANT, List.of(QuoteTestSupport.candidate("A"), QuoteTestSupport.candidate("B")), "retail"))
            .extracting(QuoteCandidate::productId)
            .containsExactly("product-B");
    }

    @Test
    void pendingAndExpiredRulesDoNotAuthorize() {
        TenantProductAuthorizationFilter filter = new TenantProductAuthorizationFilter(tenant -> List.of(
            new TenantProductAuthorizationRule("product-A", "investor-A", "retail", "PENDING", null),
            new TenantProductAuthorizationRule("product-B", "investor-B", "retail", "ACTIVE", 1L)
        ));

        assertThat(filter.filterAuthorized(QuoteTestSupport.TENANT, List.of(QuoteTestSupport.candidate("A"), QuoteTestSupport.candidate("B")), "retail")).isEmpty();
    }

    @Test
    void authorizationChangedInvalidatesRules() {
        AtomicBoolean invalidated = new AtomicBoolean(false);
        TenantProductAuthorizationFilter filter = new TenantProductAuthorizationFilter(new TenantProductAuthorizationRules() {
            @Override
            public List<TenantProductAuthorizationRule> authorizationsFor(UUID tenantId) {
                return List.of();
            }

            @Override
            public void invalidate(UUID tenantId) {
                invalidated.set(QuoteTestSupport.TENANT.equals(tenantId));
            }
        });

        filter.onAuthorizationChanged(new TenantAuthorizationChangedEvent(QuoteTestSupport.TENANT));

        assertThat(invalidated).isTrue();
    }
}
