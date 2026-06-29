package com.wcpe.quote;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.quote.LoanPassQuoteModels.CatalogSnapshot;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class LoanHouseCapturedCatalogLoaderTest {
    private static final UUID LOANHOUSE_TENANT = UUID.fromString("2aba740b-74ee-3068-a456-4df1e64b7c02");

    @Test
    void loadsAllCapturedLoanHouseProductsAsNonSyntheticSnapshot() {
        InMemoryLoanPassCatalogRepository repository = new InMemoryLoanPassCatalogRepository();
        LoanHouseCapturedCatalogLoader loader = loader(repository);

        CatalogSnapshot snapshot = loader.loadSnapshot();

        assertThat(snapshot.synthetic()).isFalse();
        assertThat(snapshot.sourceSystem()).isEqualTo(LoanHouseCapturedCatalogLoader.SOURCE_SYSTEM);
        assertThat(snapshot.metadata()).containsEntry("persistedProductCount", "1550");
        assertThat(snapshot.products()).hasSize(1550);
        assertThat(snapshot.products()).filteredOn(product -> "approved".equals(product.status())).hasSize(13);
        assertThat(snapshot.products()).filteredOn(product -> "100246".equals(product.productId())).singleElement().satisfies(product -> {
            assertThat(product.productName()).isEqualTo("Enhanced Doc 30 Yr Fixed");
            assertThat(product.investorName()).isEqualTo("Diamond");
            assertThat(product.lockPeriods()).containsExactly(60);
            assertThat(product.noteRatePercent()).isEqualByComparingTo("6.875");
            assertThat(product.priceBps()).isEqualByComparingTo("99.856");
            assertThat(product.sourceRefs()).containsEntry("monthly_pi", "2627.72");
        });
    }

    @Test
    void executeSummaryAndProductExposeCapturedRatePricePaymentLockAndSourceRefs() {
        InMemoryLoanPassCatalogRepository repository = new InMemoryLoanPassCatalogRepository();
        LoanHouseCapturedCatalogLoader loader = loader(repository);
        repository.saveSnapshot(loader.loadSnapshot());
        LoanPassQuoteService service = new LoanPassQuoteService(repository, new LoanPassWarmEvaluator());

        var summary = service.executeSummary(Map.of(), LOANHOUSE_TENANT, "corr-loanhouse-summary");
        var detail = service.executeProduct(Map.of("productId", "100246"), LOANHOUSE_TENANT, "corr-loanhouse-product");

        assertThat(summary.synthetic()).isFalse();
        assertThat(summary.productCount()).isEqualTo(1550);
        assertThat(summary.statusCounts()).containsEntry("approved", 13L).containsEntry("rejected", 1535L).containsEntry("error", 2L);
        assertThat(summary.products()).filteredOn(product -> "100246".equals(product.productId())).singleElement().satisfies(product -> {
            assertThat(product.rates()).singleElement().satisfies(rate -> assertThat(rate)
                .containsEntry("noteRatePercent", "6.875")
                .containsEntry("priceBps", "99.856"));
            assertThat(product.calculations()).containsEntry("monthlyPi", "2627.72").containsEntry("adjustedPrice", "99.856");
            assertThat(product.sourceRefs()).containsEntry("source_index", "67");
        });
        assertThat(detail.success()).isTrue();
        assertThat(detail.rates()).singleElement().satisfies(rate -> assertThat(rate)
            .containsEntry("lockPeriodDays", "60")
            .containsEntry("noteRatePercent", "6.875")
            .containsEntry("priceBps", "99.856"));
        assertThat(detail.calculations()).containsEntry("monthlyPi", "2627.72").containsEntry("sourcePayloadHash", repository.snapshot.payloadHash());
        assertThat(detail.sourceRefs()).containsEntry("source_index", "67").containsEntry("source_url", "https://lhposb2bbff1prod.loanhouse.us/api/v1/no-oauth/quickpricer/get-generic-quote-summary");
    }

    private LoanHouseCapturedCatalogLoader loader(InMemoryLoanPassCatalogRepository repository) {
        return new LoanHouseCapturedCatalogLoader(
            repository,
            new ObjectMapper(),
            Clock.fixed(Instant.parse("2026-06-28T00:00:00Z"), ZoneOffset.UTC),
            LOANHOUSE_TENANT,
            new ClassPathResource("loanhouse/product-records.json")
        );
    }

    private static final class InMemoryLoanPassCatalogRepository implements LoanPassQuoteCatalogRepository {
        private CatalogSnapshot snapshot;

        @Override
        public Optional<CatalogSnapshot> activeSnapshot(UUID tenantId) {
            return snapshot == null || !snapshot.tenantId().equals(tenantId) ? Optional.empty() : Optional.of(snapshot);
        }

        @Override
        public CatalogSnapshot saveSnapshot(CatalogSnapshot snapshot) {
            this.snapshot = snapshot;
            return snapshot;
        }
    }
}
