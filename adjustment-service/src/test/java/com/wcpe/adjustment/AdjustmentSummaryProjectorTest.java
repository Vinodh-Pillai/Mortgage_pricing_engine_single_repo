package com.wcpe.adjustment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wcpe.adjustment.AdjustmentSummaryProjector.AdjustmentSummary;
import com.wcpe.adjustment.AdjustmentSummaryProjector.AdjustmentSummaryLine;
import com.wcpe.adjustment.AdjustmentSummaryProjector.AdjustmentSummaryRequest;
import com.wcpe.adjustment.AdjustmentSummaryProjector.ConflictSeverity;
import com.wcpe.adjustment.AdjustmentSummaryProjector.FormulaDisplay;
import com.wcpe.adjustment.AdjustmentSummaryProjector.LineStatus;
import com.wcpe.adjustment.AdjustmentSummaryProjector.RedactedInputSummary;
import com.wcpe.adjustment.AdjustmentSummaryProjector.ReplayReference;
import com.wcpe.adjustment.AdjustmentSummaryProjector.SummaryConflict;
import com.wcpe.adjustment.AdjustmentSummaryProjector.SummaryStatus;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class AdjustmentSummaryProjectorTest {
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000610");
    private static final Instant GENERATED_AT = Instant.parse("2026-04-01T00:00:01Z");
    private final AdjustmentSummaryProjector projector = new AdjustmentSummaryProjector();

    @Test
    void totalsUseOnlyAppliedRoundedLinesAndPreserveWaterfallOrder() {
        AdjustmentSummary summary = projector.project(request(List.of(
            line("fee-1", 3, "BORROWER", "Borrower fee", "0.000000", "0.00", "125.10", LineStatus.APPLIED),
            line("llpa-1", 1, "LLPA", "FICO/LTV LLPA", "0.125000", "12.5000", "0.00", LineStatus.APPLIED),
            line("state-suppressed", 2, "STATE", "Suppressed state line", "0.250000", "25.0000", "50.00", LineStatus.SUPPRESSED)
        ), new BigDecimal("100.000000"), new BigDecimal("100.125000"), List.of(), List.of(), List.of(replay(true))));

        assertThat(summary.status()).isEqualTo(SummaryStatus.READY);
        assertThat(summary.totals().totalPoints()).isEqualByComparingTo("0.125000");
        assertThat(summary.totals().totalBps()).isEqualByComparingTo("12.5000");
        assertThat(summary.totals().borrowerTotal()).isEqualByComparingTo("125.10");
        assertThat(summary.categories()).extracting("categoryCode").containsExactly("LLPA", "STATE", "BORROWER");
        assertThat(summary.categories().get(1).subtotalPoints()).isZero();
    }

    @Test
    void blockedConflictsPreventReadyStatusAndEmitAuditEventMetadata() {
        AdjustmentSummary summary = projector.project(request(List.of(
            line("llpa-1", 1, "LLPA", "FICO/LTV LLPA", "0.125000", "12.5000", "0.00", LineStatus.APPLIED)
        ), new BigDecimal("100.000000"), new BigDecimal("100.125000"), List.of(
            new SummaryConflict("conflict-1", ConflictSeverity.BLOCKING, "CONFIGURED_REASON", "Configured blocker")
        ), List.of(), List.of(replay(true))));

        assertThat(summary.status()).isEqualTo(SummaryStatus.BLOCKED_CONFLICTS);
        assertThat(summary.event().eventType()).isEqualTo("QuoteAdjustmentSummaryGenerated.v1");
        assertThat(summary.event().eventKey()).isEqualTo(TENANT_ID + ":quote-PII-06-S10");
        assertThat(summary.audit().action()).isEqualTo("ADJUSTMENT_SUMMARY_VIEW_COMPLETED");
        assertThat(summary.audit().replayHash()).hasSize(64);
    }

    @Test
    void staleConfigReplayMismatchAndFinalPriceMismatchExposeNonReadyStates() {
        assertThat(projector.project(request(List.of(
            line("llpa-1", 1, "LLPA", "FICO/LTV LLPA", "0.125000", "12.5000", "0.00", LineStatus.APPLIED)
        ), new BigDecimal("100.000000"), new BigDecimal("100.125000"), List.of(), List.of("config version is stale"), List.of(replay(true)))).status())
            .isEqualTo(SummaryStatus.STALE_CONFIGURATION);

        assertThat(projector.project(request(List.of(
            line("llpa-1", 1, "LLPA", "FICO/LTV LLPA", "0.125000", "12.5000", "0.00", LineStatus.APPLIED)
        ), new BigDecimal("100.000000"), new BigDecimal("100.125000"), List.of(), List.of(), List.of(replay(false)))).status())
            .isEqualTo(SummaryStatus.REPLAY_MISMATCH);

        assertThat(projector.project(request(List.of(
            line("llpa-1", 1, "LLPA", "FICO/LTV LLPA", "0.125000", "12.5000", "0.00", LineStatus.APPLIED)
        ), new BigDecimal("100.000000"), new BigDecimal("100.250000"), List.of(), List.of(), List.of(replay(true)))).status())
            .isEqualTo(SummaryStatus.WARNINGS);
    }

    @Test
    void redactsSensitiveSourceInputsAndFailsClosedForEmptyLineSet() {
        RedactedInputSummary redacted = RedactedInputSummary.of(Map.of(
            "ficoScore", "720",
            "loanPurpose", "purchase",
            "borrowerName", "Jane Borrower",
            "cash_out_amount", "25000"
        ));

        assertThat(redacted.values()).containsEntry("ficoScore", "REDACTED");
        assertThat(redacted.values()).containsEntry("borrowerName", "REDACTED");
        assertThat(redacted.values()).containsEntry("cash_out_amount", "REDACTED");
        assertThat(redacted.values()).containsEntry("loanPurpose", "purchase");
        assertThatThrownBy(() -> projector.project(request(List.of(), BigDecimal.ONE, BigDecimal.ONE, List.of(), List.of(), List.of())))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("at least one summary line");
    }

    @Test
    void goldenFixturesMatchSummaryAndEventContracts() throws IOException {
        String fixture = Files.readString(Path.of("src/test/resources/golden/PII-06-S10-summary/adjustment-summary-response.json"));
        AdjustmentSummary summary = projector.project(request(List.of(
            line("llpa-1", 1, "LLPA", "FICO/LTV LLPA", "0.125000", "12.5000", "0.00", LineStatus.APPLIED),
            line("fee-1", 2, "BORROWER", "Borrower fee", "0.000000", "0.00", "125.10", LineStatus.APPLIED)
        ), new BigDecimal("100.000000"), new BigDecimal("100.125000"), List.of(), List.of(), List.of(replay(true))));

        assertThat(summary.quoteId()).isEqualTo(jsonString(fixture, "quoteId"));
        assertThat(summary.status().name()).isEqualTo(jsonString(fixture, "status"));
        assertThat(summary.totals().totalPoints()).isEqualByComparingTo(jsonString(fixture, "totalPoints"));
        assertThat(summary.totals().borrowerTotal()).isEqualByComparingTo(jsonString(fixture, "borrowerTotal"));

        String eventFixture = Files.readString(Path.of("src/test/resources/golden/PII-06-S10-summary/summary-generated-event-v1.json"));
        assertThat(summary.event().eventType()).isEqualTo(jsonString(eventFixture, "eventType"));
        assertThat(summary.event().sourceService()).isEqualTo(jsonString(eventFixture, "sourceService"));
    }

    private static AdjustmentSummaryRequest request(
        List<AdjustmentSummaryLine> lines,
        BigDecimal basePrice,
        BigDecimal finalAdjustedPrice,
        List<SummaryConflict> conflicts,
        List<String> staleConfigWarnings,
        List<ReplayReference> replayReferences
    ) {
        return new AdjustmentSummaryRequest(
            TENANT_ID,
            UUID.fromString("20000000-0000-0000-0000-000000000610"),
            "quote-PII-06-S10",
            "scenario-PII-06-S10",
            "pricing-run-PII-06-S10",
            "actor-1",
            basePrice,
            finalAdjustedPrice,
            "input-snapshot-hash-PII-06-S10",
            lines,
            conflicts,
            staleConfigWarnings,
            replayReferences,
            GENERATED_AT,
            "correlation-PII-06-S10"
        );
    }

    private static AdjustmentSummaryLine line(String id, int sequence, String category, String label, String points, String bps, String money, LineStatus status) {
        return new AdjustmentSummaryLine(
            id,
            sequence,
            category,
            label,
            RedactedInputSummary.of(Map.of("ficoScore", "720", "loanPurpose", "purchase")),
            new FormulaDisplay("Persisted configured ledger value", "PERSISTED_LEDGER"),
            new BigDecimal(points),
            new BigDecimal(points),
            new BigDecimal(points),
            new BigDecimal(bps),
            new BigDecimal(money),
            status,
            "CONFIGURED_REASON",
            "configured-rule",
            "config-v1",
            null,
            GENERATED_AT,
            status == LineStatus.SUPPRESSED ? "configured conflict suppression" : "",
            "configured-ledger-source"
        );
    }

    private static ReplayReference replay(boolean matches) {
        return new ReplayReference("configured-ledger-source", "config-hash", "replay-hash", matches);
    }

    private static String jsonString(String json, String key) {
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"").matcher(json);
        assertThat(matcher.find()).as("fixture contains string key %s", key).isTrue();
        return matcher.group(1);
    }
}
