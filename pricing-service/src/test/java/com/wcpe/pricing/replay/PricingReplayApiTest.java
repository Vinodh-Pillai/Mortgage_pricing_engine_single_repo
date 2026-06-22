package com.wcpe.pricing.replay;

import com.wcpe.pricing.replay.PricingReplayApi.DiffSeverity;
import com.wcpe.pricing.replay.PricingReplayApi.PricingCalculationSnapshot;
import com.wcpe.pricing.replay.PricingReplayApi.PricingLedgerEntry;
import com.wcpe.pricing.replay.PricingReplayApi.PricingReplayErrorCode;
import com.wcpe.pricing.replay.PricingReplayApi.PricingReplayException;
import com.wcpe.pricing.replay.PricingReplayApi.PricingReplayHeaders;
import com.wcpe.pricing.replay.PricingReplayApi.PricingReplayRequest;
import com.wcpe.pricing.replay.PricingReplayApi.PricingReplayResponse;
import com.wcpe.pricing.replay.PricingReplayApi.PricingReplayStatus;
import com.wcpe.pricing.replay.PricingReplayApi.ReplayMismatchClass;
import com.wcpe.pricing.replay.PricingReplayApi.ReplayMode;
import com.wcpe.pricing.replay.PricingReplayApi.ReplaySourceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PricingReplayApiTest {
    private static final String TENANT = "tenant-a";
    private static final String SOURCE_ID = "final-price-1";

    private InMemoryPricingReplayRepository repository;
    private PricingReplayApi api;

    @BeforeEach
    void setUp() {
        repository = new InMemoryPricingReplayRepository();
        api = new PricingReplayApi(repository);
    }

    @Test
    void BasePricingReplayService_replaysFromSnapshotOnly() {
        PricingCalculationSnapshot expected = snapshot(null, null, null);
        repository.saveSnapshot(expected);

        PricingReplayResponse response = api.replay(TENANT, runHeaders(), request(null));

        assertEquals(PricingReplayStatus.MATCHED, response.status());
        assertEquals(expected.resultHash(), response.replayHash());
        assertEquals(expected.ledgerHash(), response.ledgerHash());
        assertTrue(response.diff().isEmpty());
        assertEquals("pricing.replay-completed.v1", repository.events().get(0).eventType());
        assertEquals("BASE_PRICING_REPLAY_COMPLETED", repository.audits().get(0).action());
    }

    @Test
    void PricingReplayRun_persistsDiffEvidence() {
        repository.saveSnapshot(snapshot("different-result-hash", null, Map.of("roundedFinalPrice", "100.1250")));

        PricingReplayResponse response = api.replay(TENANT, runHeaders(), request(null));
        PricingReplayResponse stored = api.get(TENANT, response.replayRunId(), readHeaders());

        assertEquals(PricingReplayStatus.MISMATCHED, response.status());
        assertEquals(ReplayMismatchClass.SCALE_ONLY_DRIFT, response.mismatchClass());
        assertFalse(stored.diff().isEmpty());
        assertTrue(stored.evidenceArtifactRef().startsWith("pricing-replay:"));
        assertFalse(repository.diffs().isEmpty());
    }

    @Test
    void PricingReplay_ignoresCurrentPublishedGrid() {
        PricingCalculationSnapshot expected = snapshot(null, null, null);
        repository.saveSnapshot(expected);

        PricingReplayResponse response = api.replay(TENANT, runHeaders(), request("current-grid-hash-that-must-not-be-used"));

        assertEquals(PricingReplayStatus.MISMATCHED, response.status());
        assertEquals(ReplayMismatchClass.RESULT_HASH_MISMATCH, response.mismatchClass());
        assertEquals(expected.resultHash(), response.replayHash());
    }

    @Test
    void ReplayMismatchClassifier_detectsScaleOnlyDrift() {
        repository.saveSnapshot(snapshot("different-result-hash", null, Map.of("roundedFinalPrice", "100.1250")));

        PricingReplayResponse response = api.replay(TENANT, runHeaders(), request(null));

        assertEquals(ReplayMismatchClass.SCALE_ONLY_DRIFT, response.mismatchClass());
        assertTrue(response.diff().stream().anyMatch(diff -> diff.classification() == ReplayMismatchClass.SCALE_ONLY_DRIFT
                && diff.severity() == DiffSeverity.ERROR));
    }

    @Test
    void ReplayMismatchClassifier_detectsVersionGraphMismatch() {
        repository.saveSnapshot(snapshot("different-result-hash", "expected-version-graph-hash", null));

        PricingReplayResponse response = api.replay(TENANT, runHeaders(), request(null));

        assertEquals(PricingReplayStatus.MISMATCHED, response.status());
        assertEquals(ReplayMismatchClass.VERSION_GRAPH_MISMATCH, response.mismatchClass());
        assertTrue(response.diff().stream()
                .anyMatch(diff -> diff.classification() == ReplayMismatchClass.VERSION_GRAPH_MISMATCH));
    }

    @Test
    void PricingReplayTenantIsolationTest() {
        repository.saveSnapshot(snapshot(null, null, null));

        PricingReplayException exception = assertThrows(PricingReplayException.class,
                () -> api.replay("tenant-b", runHeaders(), request(null)));

        assertEquals(PricingReplayErrorCode.REPLAY_SOURCE_NOT_FOUND, exception.code());
    }

    @Test
    void pricingReplayRequiresRunPermission() {
        repository.saveSnapshot(snapshot(null, null, null));
        PricingReplayHeaders headers = new PricingReplayHeaders(Set.of(PricingReplayApi.PRICING_REPLAY_READ_PERMISSION),
                "actor-1", "corr-1", "idem-1");

        PricingReplayException exception = assertThrows(PricingReplayException.class,
                () -> api.replay(TENANT, headers, request(null)));

        assertEquals(PricingReplayErrorCode.REPLAY_FORBIDDEN, exception.code());
    }

    @Test
    void pricingReplayRejectsUnsupportedSnapshotSchema() {
        PricingCalculationSnapshot unsupported = new PricingCalculationSnapshot(TENANT, ReplaySourceType.FINAL_PRICE,
                SOURCE_ID, "scenario-hash-1", "version-graph-hash", null, "row-hash-1", null,
                "rounding-policy-1", null, canonicalInput(), resultValues(), null, ledger(), "snapshot-result-hash",
                "snapshot-ledger-hash", false);
        repository.saveSnapshot(unsupported);

        PricingReplayException exception = assertThrows(PricingReplayException.class,
                () -> api.replay(TENANT, runHeaders(), request(null)));

        assertEquals(PricingReplayErrorCode.REPLAY_SCHEMA_UNSUPPORTED, exception.code());
    }

    private PricingCalculationSnapshot snapshot(String resultHashOverride, String expectedVersionGraphHash,
            Map<String, String> expectedResultValues) {
        String ledgerHash = stableHash("pricing-replay-ledger", ledger());
        String resultHash = stableHash("pricing-replay-result", TENANT, SOURCE_ID, ReplaySourceType.FINAL_PRICE,
                "scenario-hash-1", "version-graph-hash", "row-hash-1", "rounding-policy-1", canonicalInput(),
                resultValues(), ledgerHash);
        return new PricingCalculationSnapshot(TENANT, ReplaySourceType.FINAL_PRICE, SOURCE_ID, "scenario-hash-1",
                "version-graph-hash", expectedVersionGraphHash, "row-hash-1", null, "rounding-policy-1", null,
                canonicalInput(), resultValues(), expectedResultValues, ledger(),
                resultHashOverride == null ? resultHash : resultHashOverride, ledgerHash, true);
    }

    private static Map<String, String> canonicalInput() {
        return Map.of(
                "sourceSnapshotRef", "snapshot-final-price-1",
                "versionGraphHash", "version-graph-hash",
                "selectedRowHash", "row-hash-1");
    }

    private static Map<String, String> resultValues() {
        return Map.of("roundedFinalPrice", "100.12500", "selectedNoteRate", "6.12500");
    }

    private static List<PricingLedgerEntry> ledger() {
        return List.of(
                new PricingLedgerEntry(1, "BASE_PRICE", "100.00000", "START", "100.00000", "grid-version-1",
                        "BASE_RATE_SELECTED"),
                new PricingLedgerEntry(2, "ROUND_FINAL_PRICE", "100.12500", "ROUND", "100.12500",
                        "rounding-policy-1", "ROUND_FINAL_PRICE"));
    }

    private static PricingReplayRequest request(String expectedResultHash) {
        return new PricingReplayRequest(ReplaySourceType.FINAL_PRICE, SOURCE_ID, expectedResultHash,
                ReplayMode.VERIFY, null);
    }

    private static PricingReplayHeaders runHeaders() {
        return new PricingReplayHeaders(Set.of(PricingReplayApi.PRICING_REPLAY_RUN_PERMISSION), "actor-1", "corr-1",
                "idem-1");
    }

    private static PricingReplayHeaders readHeaders() {
        return new PricingReplayHeaders(Set.of(PricingReplayApi.PRICING_REPLAY_READ_PERMISSION), "actor-1", "corr-1",
                null);
    }

    private static String stableHash(Object... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Object value : values) {
                digest.update(canonical(value).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static String canonical(Object value) {
        if (value instanceof Map<?, ?> map) {
            TreeMap<String, String> sorted = new TreeMap<>();
            map.forEach((key, mapValue) -> sorted.put(String.valueOf(key), canonical(mapValue)));
            return sorted.toString();
        }
        if (value instanceof List<?> list) {
            return list.stream().map(PricingReplayApiTest::canonical).toList().toString();
        }
        return String.valueOf(value);
    }
}
