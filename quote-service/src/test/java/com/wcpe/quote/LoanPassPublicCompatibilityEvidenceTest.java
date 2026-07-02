package com.wcpe.quote;

import static org.assertj.core.api.Assertions.assertThat;

import com.wcpe.quote.LoanPassQuoteModels.CatalogSnapshot;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.core.io.ClassPathResource;

class LoanPassPublicCompatibilityEvidenceTest {
    private static final UUID SYNTHETIC_TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void localOpenApiStatesPublicSwaggerDiffBlocker() throws Exception {
        ClassPathResource contract = new ClassPathResource("openapi/loanpass-quote-api.yml");
        String yaml = new String(contract.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertThat(yaml)
            .contains("local structural compatibility evidence only")
            .contains("must not be treated as a")
            .contains("byte-for-byte public LoanPass swagger diff");
        assertThat(Path.of("src/test/resources/loanpass/public-swagger.json"))
            .as("Approved public LoanPass swagger/schema artifact is not committed; do not fetch it over network in tests")
            .doesNotExist();
    }

    @Test
    void syntheticLoaderRecordsSeedMetadataAndDevOnlyPolicy() {
        InMemoryLoanPassCatalogRepository repository = new InMemoryLoanPassCatalogRepository();
        LoanPassSyntheticCatalogLoader loader = new LoanPassSyntheticCatalogLoader(
            repository,
            Clock.fixed(Instant.parse("2026-06-28T00:00:00Z"), ZoneOffset.UTC),
            SYNTHETIC_TENANT,
            3,
            "increment-5-seed"
        );

        loader.run(new DefaultApplicationArguments());

        CatalogSnapshot snapshot = repository.snapshot;
        assertThat(snapshot.synthetic()).isTrue();
        assertThat(snapshot.seed()).isEqualTo("increment-5-seed");
        assertThat(snapshot.generatorVersion()).isEqualTo("loanpass-synthetic-dev-v1");
        assertThat(snapshot.schemaVersion()).isEqualTo("loanpass-public-concept-aligned-v1");
        assertThat(snapshot.metadata())
            .containsEntry("devOnly", "true")
            .containsEntry("operationConcepts", "execute-summary,execute-product");
        assertThat(snapshot.products()).hasSize(3);
        assertThat(snapshot.products()).allSatisfy(product -> assertThat(product.sourceRefs())
            .containsEntry("source", "synthetic-dev-only")
            .containsEntry("generatorVersion", "loanpass-synthetic-dev-v1"));
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
