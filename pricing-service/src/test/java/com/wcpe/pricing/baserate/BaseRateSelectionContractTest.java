package com.wcpe.pricing.baserate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.*;
import com.wcpe.pricing.baserate.BaseRateSelectionApi.*;
import org.junit.jupiter.api.*;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract tests for BaseRateSelectionApi using synthetic grid fixtures only.
 * No real mortgage rates, thresholds, fees, or business constants.
 */
@DisplayName("BaseRate Selection Contract")
class BaseRateSelectionContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static Path contractsDir;

    private InMemoryBaseRateSelectionRepository repository;
    private BaseRateSelectionApi api;

    @BeforeAll
    static void loadContractsDir() {
        contractsDir = Paths.get("contracts", "pricing-service").toAbsolutePath();
    }

    @BeforeEach
    void setUp() {
        repository = new InMemoryBaseRateSelectionRepository();
        api = new BaseRateSelectionApi(repository);
    }

    /* ── test: POST 200 happy path matches contract schema ── */

    @Test
    @DisplayName("base_rate_selection_post_200")
    void base_rate_selection_post_200() throws Exception {
        UUID gridId = addPublishedGridVersion(repository, TENANT_A, PRODUCT, INVESTOR, CHANNEL);

        repository.addGridRow(new BasePricingGridRow(
                UUID.randomUUID(), TENANT_A, gridId, 30,
                new BigDecimal("SYNTH_6.12500"), new BigDecimal("SYNTH_100.00000"),
                Set.of(), "synth-row-hash", java.time.Instant.parse("2026-01-01T00:00:00Z")));

        BaseRateSelectionResponse response = api.selectRate(
                TENANT_A,
                validWriteHeaders(),
                validRequest());

        // Serialise the response payload
        String responseJson = MAPPER.writeValueAsString(response);

        // Verify it contains the expected top-level fields from the contract response schema
        assertTrue(responseJson.contains("\"selectionId\""));
        assertTrue(responseJson.contains("\"gridVersionId\""));
        assertTrue(responseJson.contains("\"selectedNoteRate\""));
        assertTrue(responseJson.contains("\"selectedBasePrice\""));
        assertTrue(responseJson.contains("\"candidateRates\""));
        assertTrue(responseJson.contains("\"lockPeriodDays\""));
        assertTrue(responseJson.contains("\"ledger\""));
        assertTrue(responseJson.contains("\"warnings\""));
        assertTrue(responseJson.contains("\"resultHash\""));

        assertNotNull(response.selectionId());
        assertNotNull(response.resultHash());
        assertTrue(response.ledger().size() >= 3);
        assertTrue(response.candidateRates().size() >= 1);
    }

    /* ── test: grid not found returns 422-style error ── */

    @Test
    @DisplayName("base_rate_selection_grid_not_found_422")
    void base_rate_selection_grid_not_found_422() {
        // No grid version seeded
        Exception ex = assertThrows(BaseRateSelectionGridNotFoundException.class, () ->
                api.selectRate(TENANT_A, validWriteHeaders(), validRequest()));

        String message = ex.getMessage();
        assertNotNull(message);
        assertTrue(message.contains("no published grid version found"),
                "Error message should indicate grid not found: " + message);
    }

    /* ── test: stale/ambiguous version returns 409-style error ── */

    @Test
    @DisplayName("base_rate_selection_stale_version_409")
    void base_rate_selection_stale_version_409() {
        // Two overlapping published versions
        addPublishedGridVersion(repository, TENANT_A, PRODUCT, INVESTOR, CHANNEL);
        addPublishedGridVersion(repository, TENANT_A, PRODUCT, INVESTOR, CHANNEL);

        Exception ex = assertThrows(BaseRateSelectionConflictException.class, () ->
                api.selectRate(TENANT_A, validWriteHeaders(), validRequest()));

        String message = ex.getMessage();
        assertNotNull(message);
        assertTrue(message.contains("ambiguous"),
                "Error message should indicate ambiguous versions: " + message);
    }

    /* ── helpers ── */

    private static final String TENANT_A = "tenant-a";
    private static final String PRODUCT = "CONVENTIONAL";
    private static final String INVESTOR = "FANNIE";
    private static final String CHANNEL = "RETAIL";

    private static java.util.UUID addPublishedGridVersion(
            InMemoryBaseRateSelectionRepository repo,
            String tenant, String product, String investor, String channel) {
        java.util.UUID id = java.util.UUID.randomUUID();
        repo.addGridVersion(new BasePricingGridVersion(
                id, tenant, product, investor, channel, 1,
                GridVersionStatus.PUBLISHED,
                java.time.Instant.parse("2026-01-01T00:00:00Z"),
                java.time.Instant.parse("2027-01-01T00:00:00Z"),
                "digest-1", "approver-1",
                java.time.Instant.now(), java.time.Instant.now(), java.time.Instant.now()));
        return id;
    }

    private static BaseRateSelectionHeaders validWriteHeaders() {
        return new BaseRateSelectionHeaders(
                Set.of(BaseRateSelectionApi.BASE_RATE_WRITE_PERMISSION),
                "actor-1", "corr-1", "idem-1");
    }

    private static BaseRateSelectionRequest validRequest() {
        return new BaseRateSelectionRequest(
                "sc-1", "hash-1", PRODUCT, INVESTOR, CHANNEL,
                30, java.time.Instant.parse("2026-06-01T00:00:00Z"), null, "policy-1");
    }
}
