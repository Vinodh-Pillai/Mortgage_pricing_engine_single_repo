package com.wcpe.quote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class QuoteTenantAuthorizationIntegrationTest {
    @Test
    void quoteLaunchOnlyRanksAuthorizedProducts() {
        TenantProductAuthorizationFilter filter = new TenantProductAuthorizationFilter(tenant -> List.of(
            new TenantProductAuthorizationRule("product-B", "investor-B", "retail", "ACTIVE", null)
        ));
        QuoteDependencies dependencies = new AuthorizingDependencies(QuoteTestSupport.dependenciesWithPolicy(), filter);
        QuoteApplicationService service = QuoteTestSupport.service(dependencies, new InMemoryQuoteCache());

        Quote quote = service.createQuote(QuoteTestSupport.request("auth-filtered"));

        assertThat(quote.options()).hasSize(1);
        assertThat(quote.options().get(0).productId()).isEqualTo("product-B");
    }

    @Test
    void quoteLaunchFailsWhenTenantHasNoAuthorizedProducts() {
        TenantProductAuthorizationFilter filter = new TenantProductAuthorizationFilter(tenant -> List.of());
        QuoteDependencies dependencies = new AuthorizingDependencies(QuoteTestSupport.dependenciesWithPolicy(), filter);
        QuoteApplicationService service = QuoteTestSupport.service(dependencies, new InMemoryQuoteCache());

        assertThatThrownBy(() -> service.createQuote(QuoteTestSupport.request("auth-empty")))
            .isInstanceOf(QuoteCreateException.class)
            .satisfies(ex -> assertThat(((QuoteCreateException) ex).code()).isEqualTo("NO_AUTHORIZED_PRODUCTS_FOR_TENANT"));
    }

    private record AuthorizingDependencies(QuoteDependencies delegate, TenantProductAuthorizationFilter filter) implements QuoteDependencies {
        @Override
        public java.util.Optional<RankingPolicy> rankingPolicyFor(QuoteCreateRequest request) {
            return delegate.rankingPolicyFor(request);
        }

        @Override
        public List<QuoteCandidate> candidatesFor(QuoteCreateRequest request) {
            return delegate.candidatesFor(request);
        }

        @Override
        public List<QuoteCandidate> authorizedCandidatesFor(QuoteCreateRequest request, List<QuoteCandidate> candidates) {
            return filter.filterAuthorized(request.tenantId(), candidates, firstChannel(request));
        }

        @Override
        public String eligibilityVersion() {
            return delegate.eligibilityVersion();
        }

        @Override
        public String pricingVersion() {
            return delegate.pricingVersion();
        }

        @Override
        public String adjustmentVersion() {
            return delegate.adjustmentVersion();
        }

        @Override
        public String marginVersion() {
            return delegate.marginVersion();
        }

        private static String firstChannel(QuoteCreateRequest request) {
            return request.filters().channels().isEmpty() ? null : request.filters().channels().get(0);
        }
    }
}
