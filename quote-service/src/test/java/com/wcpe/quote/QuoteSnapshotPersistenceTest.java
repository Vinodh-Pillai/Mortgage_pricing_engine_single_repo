package com.wcpe.quote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class QuoteSnapshotPersistenceTest {
    @Test
    void persistsImmutableSnapshotWhenReadyQuoteIsCreated() {
        InMemoryQuoteSnapshotRepository snapshots = new InMemoryQuoteSnapshotRepository();
        QuoteApplicationService service = serviceWithSnapshots(snapshots);

        Quote quote = service.createQuote(QuoteTestSupport.request("snapshot-ready"));

        QuoteSnapshot snapshot = snapshots.findByQuoteId(quote.tenantId(), quote.quoteId()).orElseThrow();
        assertThat(snapshot.tenantId()).isEqualTo(quote.tenantId());
        assertThat(snapshot.quoteId()).isEqualTo(quote.quoteId());
        assertThat(snapshot.quoteVersion()).isEqualTo(quote.version());
        assertThat(snapshot.replayHash()).isEqualTo(quote.replayHash());
        assertThat(snapshot.canonicalRequest()).containsEntry("scenarioVersion", Integer.toString(quote.scenarioVersion()));
        assertThat(snapshot.canonicalResponse()).containsEntry("status", QuoteStatus.READY.name());
        assertThat(snapshot.inputVersionSet()).containsEntry("pricingVersion", "pricing-v1");
        assertThat(snapshot.retentionUntil()).isEqualTo(Instant.parse("2033-06-02T12:00:00Z"));
        assertThat(service.outboxEvents()).anySatisfy(event -> assertThat(event.eventType()).isEqualTo("quote.snapshot_created.v1"));
        assertThat(service.auditEntries()).anySatisfy(entry -> assertThat(entry.action()).isEqualTo("QUOTE_SNAPSHOT_CREATED"));
    }

    @Test
    void validatesGoldenSnapshotCanonicalRequestResponseAndHash() throws Exception {
        InMemoryQuoteSnapshotRepository snapshots = new InMemoryQuoteSnapshotRepository();
        Quote quote = serviceWithSnapshots(snapshots).createQuote(QuoteTestSupport.request("snapshot-golden"));
        QuoteSnapshot snapshot = snapshots.findByQuoteId(quote.tenantId(), quote.quoteId()).orElseThrow();

        String golden = Files.readString(Path.of("src/test/resources/golden/pii08/snapshot-ready-quote.json"));

        assertThat(golden).contains("\"status\": \"READY\"");
        assertThat(snapshot.canonicalRequest()).containsEntry("scenarioVersion", "7");
        assertThat(snapshot.canonicalResponse()).containsEntry("status", "READY");
        assertThat(golden).contains("\"outputDigestAlgorithm\": \"SHA-256(canonicalResponse)\"");
        assertThat(snapshot.outputDigest()).isEqualTo(ReplayHash.sha256(snapshot.canonicalResponse().toString()));
        assertThat(snapshot.replayHash()).isEqualTo(quote.replayHash());
    }

    @Test
    void returnsSameSnapshotForIdempotentQuoteReplay() {
        InMemoryQuoteSnapshotRepository snapshots = new InMemoryQuoteSnapshotRepository();
        QuoteApplicationService service = serviceWithSnapshots(snapshots);

        Quote first = service.createQuote(QuoteTestSupport.request("snapshot-idempotent"));
        Quote second = service.createQuote(QuoteTestSupport.request("snapshot-idempotent"));

        QuoteSnapshot snapshot = snapshots.findByQuoteId(first.tenantId(), first.quoteId()).orElseThrow();
        assertThat(second.quoteId()).isEqualTo(first.quoteId());
        assertThat(snapshot.replayHash()).isEqualTo(first.replayHash());
        assertThat(service.outboxEvents().stream().filter(event -> event.eventType().equals("quote.snapshot_created.v1"))).hasSize(1);
    }

    @Test
    void rejectsRepositoryOverwriteForExistingSnapshot() {
        InMemoryQuoteSnapshotRepository snapshots = new InMemoryQuoteSnapshotRepository();
        Quote quote = serviceWithSnapshots(snapshots).createQuote(QuoteTestSupport.request("snapshot-immutable"));
        QuoteSnapshot snapshot = snapshots.findByQuoteId(quote.tenantId(), quote.quoteId()).orElseThrow();

        assertThatThrownBy(() -> snapshots.saveNew(snapshot))
            .isInstanceOf(QuoteCreateException.class)
            .hasMessageContaining("already exists");
    }

    @Test
    void exportsSnapshotOnlyWhenRedactionProfileIsConfigured() {
        QuoteApplicationService service = serviceWithSnapshots(new InMemoryQuoteSnapshotRepository());
        Quote quote = service.createQuote(QuoteTestSupport.request("snapshot-export"));

        assertThatThrownBy(() -> service.exportQuoteSnapshot(quote.tenantId(), quote.quoteId(), " ", false, "actor-1", "corr-export"))
            .isInstanceOf(QuoteCreateException.class)
            .hasMessageContaining("redaction profile");

        QuoteSnapshotExport export = service.exportQuoteSnapshot(
            quote.tenantId(),
            quote.quoteId(),
            "audit-safe-redaction-v1",
            false,
            "actor-1",
            "corr-export"
        );

        assertThat(export.snapshotId()).isNotNull();
        assertThat(export.redactionProfile()).isEqualTo("audit-safe-redaction-v1");
        assertThat(service.auditEntries()).anySatisfy(entry -> assertThat(entry.action()).isEqualTo("QUOTE_SNAPSHOT_EXPORTED"));
    }

    private static QuoteApplicationService serviceWithSnapshots(InMemoryQuoteSnapshotRepository snapshots) {
        return new QuoteApplicationService(
            new InMemoryQuoteRepository(),
            new InMemoryQuoteJobRepository(),
            snapshots,
            QuoteTestSupport.dependenciesWithPolicy(),
            new InMemoryQuoteCache(),
            new BestExecutionRanker(),
            QuoteTestSupport.CLOCK
        );
    }
}
