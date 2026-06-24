package com.wcpe.quote;

import com.wcpe.quote.LoanPassQuoteModels.CatalogProduct;
import com.wcpe.quote.LoanPassQuoteModels.CatalogSnapshot;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

public class LoanPassSyntheticCatalogLoader implements ApplicationRunner {
    private static final String GENERATOR_VERSION = "loanpass-synthetic-dev-v1";
    private final LoanPassQuoteCatalogRepository repository;
    private final Clock clock;
    private final UUID tenantId;
    private final int productCount;
    private final String seed;

    public LoanPassSyntheticCatalogLoader(LoanPassQuoteCatalogRepository repository, Clock clock, UUID tenantId, int productCount, String seed) {
        this.repository = repository;
        this.clock = clock;
        this.tenantId = tenantId;
        this.productCount = productCount;
        this.seed = seed;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<CatalogProduct> products = products();
        String payloadHash = ReplayHash.sha256(tenantId + ":" + GENERATOR_VERSION + ":" + seed + ":" + productCount);
        repository.saveSnapshot(new CatalogSnapshot(
            tenantId,
            "synthetic-dev-" + seed,
            "synthetic-dev-only-loanpass-shape",
            true,
            GENERATOR_VERSION,
            seed,
            "loanpass-public-concept-aligned-v1",
            clock.instant(),
            payloadHash,
            products,
            Map.of(
                "devOnly", "true",
                "docsUrl", "https://docs.loanpass.io/public-api/index.html",
                "operationConcepts", "execute-summary,execute-product",
                "capturedRunId", seed
            )
        ));
    }

    private List<CatalogProduct> products() {
        List<CatalogProduct> products = new ArrayList<>();
        List<Integer> locks = List.of(15, 30, 45, 60);
        for (int i = 0; i < productCount; i++) {
            String status = switch (i % 10) {
                case 0 -> "approved";
                case 1 -> "error";
                case 2 -> "no_pricing";
                default -> "rejected";
            };
            products.add(new CatalogProduct(
                "synthetic-product-" + i,
                "Synthetic Dev Product " + i,
                "Synthetic Investor " + (i % 7),
                "Synthetic Type " + (i % 13),
                status,
                locks,
                new BigDecimal("6.00000").add(BigDecimal.valueOf(i % 8).movePointLeft(3)),
                new BigDecimal("10000.0000").subtract(BigDecimal.valueOf(i % 25)),
                Map.of("syntheticRule", "dev-only-shape-metadata-not-production-eligibility"),
                status.equals("approved") ? List.of("synthetic-dev-only-stipulation") : List.of(),
                status.equals("rejected") ? List.of("synthetic-dev-only-rejection") : List.of(),
                Map.of("source", "synthetic-dev-only", "generatorVersion", GENERATOR_VERSION)
            ));
        }
        return List.copyOf(products);
    }
}
