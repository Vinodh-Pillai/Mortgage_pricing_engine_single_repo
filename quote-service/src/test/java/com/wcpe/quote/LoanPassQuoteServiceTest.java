package com.wcpe.quote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wcpe.quote.LoanPassQuoteModels.CatalogProduct;
import com.wcpe.quote.LoanPassQuoteModels.CatalogSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LoanPassQuoteServiceTest {
    @Test
    void executeSummaryReturnsLoanPassCompatibleCountsAndWarmBenchmark() {
        InMemoryLoanPassCatalogRepository repository = new InMemoryLoanPassCatalogRepository(snapshot());
        LoanPassQuoteService service = new LoanPassQuoteService(repository, new LoanPassWarmEvaluator());

        var response = service.executeSummary(Map.of(), QuoteTestSupport.TENANT, "corr-lpq");

        assertThat(response.success()).isTrue();
        assertThat(response.operation()).isEqualTo("execute-summary");
        assertThat(response.statusCounts())
            .containsEntry("approved", 1L)
            .containsEntry("rejected", 1L)
            .containsEntry("error", 1L)
            .containsEntry("no_pricing", 2L);
        assertThat(response.lockPeriods()).containsExactly(30, 45, 60);
        assertThat(response.products()).hasSize(5);
        assertThat(response.products())
            .filteredOn(product -> product.productId().equals("lp-product-4") || product.productId().equals("lp-product-5"))
            .extracting("status")
            .containsOnly("no_pricing");
        assertThat(response.benchmark().p99Micros()).isGreaterThanOrEqualTo(0L);
        assertThat(response.versionMetadata()).containsEntry("fieldPolicy", "concept-aligned-only-public-evidence-no-unverified-fields");
    }

    @Test
    void executeProductReturnsDetailFromDurableSnapshotOnly() {
        InMemoryLoanPassCatalogRepository repository = new InMemoryLoanPassCatalogRepository(snapshot());
        LoanPassQuoteService service = new LoanPassQuoteService(repository, new LoanPassWarmEvaluator());

        var response = service.executeProduct(Map.of("productId", "lp-product-1"), QuoteTestSupport.TENANT, "corr-product");

        assertThat(response.operation()).isEqualTo("execute-product");
        assertThat(response.productId()).isEqualTo("lp-product-1");
        assertThat(response.success()).isTrue();
        assertThat(response.status()).isEqualTo("approved");
        assertThat(response.rates()).hasSize(2);
        assertThat(response.stipulations()).contains("dev-only-income-documentation");
        assertThat(response.calculations()).containsEntry("calculationPolicy", "synthetic-dev-only-no-production-rules");
        assertThat(response.versionMetadata()).containsEntry("payloadHash", "hash-lpq-02");
    }

    @Test
    void executeProductDoesNotReturnSuccessfulExecutableRatesForNonExecutableProducts() {
        InMemoryLoanPassCatalogRepository repository = new InMemoryLoanPassCatalogRepository(snapshot());
        LoanPassQuoteService service = new LoanPassQuoteService(repository, new LoanPassWarmEvaluator());

        assertNonExecutable(service, "lp-product-2", "rejected", "source-status:rejected");
        assertNonExecutable(service, "lp-product-3", "error", "source-status:error");
        assertNonExecutable(service, "lp-product-4", "no_pricing", "missing:noteRatePercent");
        assertNonExecutable(service, "lp-product-5", "no_pricing", "missing:priceBps");
    }

    @Test
    void executeSummaryFailsClosedWhenDurableCatalogMissing() {
        LoanPassQuoteService service = new LoanPassQuoteService(new InMemoryLoanPassCatalogRepository(null), new LoanPassWarmEvaluator());

        assertThatThrownBy(() -> service.executeSummary(Map.of(), QuoteTestSupport.TENANT, "corr-missing"))
            .isInstanceOf(QuoteCreateException.class)
            .hasMessageContaining("No durable LoanPass-compatible quote catalog snapshot");
    }

    private CatalogSnapshot snapshot() {
        List<CatalogProduct> products = new ArrayList<>();
        products.add(new CatalogProduct("lp-product-1", "LP Product 1", "Investor A", "Conforming", "approved",
            List.of(30, 60), new BigDecimal("6.12500"), new BigDecimal("10025.0000"),
            Map.of("syntheticRule", "dev-only"), List.of("dev-only-income-documentation"), List.of(), Map.of("source", "unit-test")));
        products.add(new CatalogProduct("lp-product-2", "LP Product 2", "Investor B", "Jumbo", "rejected",
            List.of(30), new BigDecimal("6.50000"), new BigDecimal("9990.0000"),
            Map.of("syntheticRule", "dev-only"), List.of(), List.of("dev-only-rejection"), Map.of("source", "unit-test")));
        products.add(new CatalogProduct("lp-product-3", "LP Product 3", "Investor A", "Conforming", "error",
            List.of(45), new BigDecimal("6.25000"), new BigDecimal("10000.0000"),
            Map.of(), List.of(), List.of(), Map.of("source", "unit-test")));
        products.add(new CatalogProduct("lp-product-4", "LP Product 4", "Investor A", "Conforming", "approved",
            List.of(45), null, new BigDecimal("10000.0000"),
            Map.of(), List.of(), List.of(), Map.of("source", "unit-test")));
        products.add(new CatalogProduct("lp-product-5", "LP Product 5", "Investor A", "Conforming", "approved",
            List.of(45), new BigDecimal("6.25000"), null,
            Map.of(), List.of(), List.of(), Map.of("source", "unit-test")));
        return new CatalogSnapshot(
            QuoteTestSupport.TENANT,
            "snapshot-lpq-02",
            "synthetic-dev-only-loanpass-shape",
            true,
            "loanpass-synthetic-dev-v1",
            "lpq-02-test",
            "loanpass-public-concept-aligned-v1",
            Instant.parse("2026-06-23T00:00:00Z"),
            "hash-lpq-02",
            products,
            Map.of("devOnly", "true")
        );
    }

    private void assertNonExecutable(LoanPassQuoteService service, String productId, String expectedStatus, String expectedError) {
        var response = service.executeProduct(Map.of("productId", productId), QuoteTestSupport.TENANT, "corr-product");

        assertThat(response.success()).isFalse();
        assertThat(response.status()).isEqualTo(expectedStatus);
        assertThat(response.rates()).isEmpty();
        assertThat(response.errors()).contains(expectedError);
        assertThat(response.calculations()).containsKey("nonExecutableReason");
    }

    private record InMemoryLoanPassCatalogRepository(CatalogSnapshot snapshot) implements LoanPassQuoteCatalogRepository {
        @Override
        public Optional<CatalogSnapshot> activeSnapshot(UUID tenantId) {
            return snapshot == null || !snapshot.tenantId().equals(tenantId) ? Optional.empty() : Optional.of(snapshot);
        }

        @Override
        public CatalogSnapshot saveSnapshot(CatalogSnapshot snapshot) {
            return snapshot;
        }
    }
}
