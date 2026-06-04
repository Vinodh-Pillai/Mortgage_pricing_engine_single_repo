package com.wcpe.pricing.baserate;

import com.wcpe.pricing.baserate.BaseRateSelectionApi.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BaseRateSelectionService Tests")
class BaseRateSelectionServiceTest {

    private InMemoryBaseRateSelectionRepository repository;
    private BaseRateSelectionApi api;

    private static final String TENANT_A = "tenant-a";
    private static final String TENANT_B = "tenant-b";
    private static final String PRODUCT = "CONVENTIONAL";
    private static final String INVESTOR = "FANNIE";
    private static final String CHANNEL = "RETAIL";
    private static final Instant AS_OF = Instant.parse("2026-06-01T00:00:00Z");

    @BeforeEach
    void setUp() {
        repository = new InMemoryBaseRateSelectionRepository();
        api = new BaseRateSelectionApi(repository);
    }

    /* ── helpers ── */

    private UUID addPublishedGridVersion(String tenant, Instant effectiveFrom, Instant effectiveTo) {
        UUID id = UUID.randomUUID();
        repository.addGridVersion(new BasePricingGridVersion(
                id, tenant, PRODUCT, INVESTOR, CHANNEL, 1,
                GridVersionStatus.PUBLISHED,
                effectiveFrom, effectiveTo,
                "digest-1", "approver-1", Instant.now(), Instant.now(), Instant.now()));
        return id;
    }

    private void addGridRow(UUID gridVersionId, String tenant, int lockPeriodDays,
                            BigDecimal noteRate, BigDecimal basePrice) {
        repository.addGridRow(new BasePricingGridRow(
                UUID.randomUUID(), tenant, gridVersionId, lockPeriodDays,
                noteRate, basePrice,
                Map.of(), "row-hash", Instant.now()));
    }

    private BaseRateSelectionHeaders validWriteHeaders() {
        return new BaseRateSelectionHeaders(
                Set.of(BaseRateSelectionApi.BASE_RATE_WRITE_PERMISSION),
                "actor-1", "corr-1", "idem-1");
    }

    private BaseRateSelectionRequest validRequest() {
        return new BaseRateSelectionRequest(
                "sc-1", "hash-1", PRODUCT, INVESTOR, CHANNEL,
                30, AS_OF, null, "policy-1");
    }

    /* ── tests ── */

    @Test
    @DisplayName("selects configured policy candidate")
    void BaseRateSelectionService_selectsConfiguredPolicyCandidate() {
        UUID gridId = addPublishedGridVersion(TENANT_A,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2027-01-01T00:00:00Z"));

        addGridRow(gridId, TENANT_A, 30, new BigDecimal("6.12500"), new BigDecimal("100.00000"));
        addGridRow(gridId, TENANT_A, 30, new BigDecimal("5.87500"), new BigDecimal("110.00000"));

        BaseRateSelectionResponse response = api.selectRate(TENANT_A, validWriteHeaders(), validRequest());

        assertNotNull(response.selectionId());
        assertEquals(gridId, response.gridVersionId());
        assertEquals(2, response.candidateRates().size());
        assertNotNull(response.resultHash());
        assertTrue(response.ledger().size() >= 3);
    }

    @Test
    @DisplayName("rejects ambiguous version")
    void BaseRateSelectionService_rejectsAmbiguousVersion() {
        // Two published versions overlapping for the same effective window
        addPublishedGridVersion(TENANT_A,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2027-01-01T00:00:00Z"));
        addPublishedGridVersion(TENANT_A,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2027-01-01T00:00:00Z"));

        assertThrows(BaseRateSelectionConflictException.class, () ->
                api.selectRate(TENANT_A, validWriteHeaders(), validRequest()));
    }

    @Test
    @DisplayName("rejects unconfigured lock period")
    void LockPeriodPolicy_rejectsUnconfiguredLock() {
        UUID gridId = addPublishedGridVersion(TENANT_A,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2027-01-01T00:00:00Z"));

        // Only 30-day rows exist; request 60-day
        addGridRow(gridId, TENANT_A, 30, new BigDecimal("6.12500"), new BigDecimal("100.00000"));

        BaseRateSelectionRequest request60 = new BaseRateSelectionRequest(
                "sc-1", "hash-1", PRODUCT, INVESTOR, CHANNEL,
                60, AS_OF, null, "policy-1");

        assertThrows(BaseRateSelectionNotFoundException.class, () ->
                api.selectRate(TENANT_A, validWriteHeaders(), request60));
    }

    @Test
    @DisplayName("BigDecimal precision preserved in candidates")
    void BigDecimal_precision_preserved_in_candidates() {
        UUID gridId = addPublishedGridVersion(TENANT_A,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2027-01-01T00:00:00Z"));

        BigDecimal rawRate = new BigDecimal("6.12500123");
        BigDecimal rawPrice = new BigDecimal("100.50000987");
        addGridRow(gridId, TENANT_A, 30, rawRate, rawPrice);

        BaseRateSelectionResponse response = api.selectRate(TENANT_A, validWriteHeaders(), validRequest());

        assertEquals(BigDecimal.valueOf(6, 5), response.selectedNoteRate(),
                "Note rate should be scaled to 5 decimal places");
        assertEquals(BigDecimal.valueOf(100, 5).add(new BigDecimal("0.50001")), response.selectedBasePrice(),
                "Base price should be scaled to 5 decimal places");

        // Verify CandidateRate constructor normalization
        CandidateRate candidate = new CandidateRate(rawRate, rawPrice, 1, "GRID_MATCH");
        assertEquals(5, candidate.noteRate().scale());
        assertEquals(5, candidate.basePrice().scale());
    }

    @Test
    @DisplayName("value objects validate on construction")
    void value_objects_validate_on_construction() {
        // TenantId rejects blank
        assertThrows(BaseRateSelectionValidationException.class, () -> new TenantId(""));
        assertThrows(BaseRateSelectionValidationException.class, () -> new TenantId(null));

        // LockPeriodDays rejects non-positive
        assertThrows(BaseRateSelectionValidationException.class, () -> new LockPeriodDays(0));
        assertThrows(BaseRateSelectionValidationException.class, () -> new LockPeriodDays(-1));
        new LockPeriodDays(1); // positive is fine

        // NoteRate rejects null
        assertThrows(BaseRateSelectionValidationException.class, () -> new NoteRate(null));
        // NoteRate normalizes scale
        assertEquals(5, new NoteRate(new BigDecimal("5.123")).value().scale());

        // Price rejects null
        assertThrows(BaseRateSelectionValidationException.class, () -> new Price(null));

        // ProductCode rejects blank
        assertThrows(BaseRateSelectionValidationException.class, () -> new ProductCode("  "));

        // InvestorCode rejects blank
        assertThrows(BaseRateSelectionValidationException.class, () -> new InvestorCode(""));

        // ChannelCode rejects blank
        assertThrows(BaseRateSelectionValidationException.class, () -> new ChannelCode(null));

        // ScenarioHash rejects blank
        assertThrows(BaseRateSelectionValidationException.class, () -> new ScenarioHash(""));

        // PricingVersionRef rejects blank
        assertThrows(BaseRateSelectionValidationException.class, () -> new PricingVersionRef(""));

        // AsOfInstant rejects null
        assertThrows(BaseRateSelectionValidationException.class, () -> new AsOfInstant(null));
    }

    @Test
    @DisplayName("tenant isolation enforced on grid lookup")
    void tenant_isolation_enforced_on_grid_lookup() {
        // Grid for tenant-b only
        UUID gridB = addPublishedGridVersion(TENANT_B,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2027-01-01T00:00:00Z"));
        addGridRow(gridB, TENANT_B, 30, new BigDecimal("6.00000"), new BigDecimal("100.00000"));

        // Tenant-a should not see tenant-b's grid
        assertThrows(BaseRateSelectionGridNotFoundException.class, () ->
                api.selectRate(TENANT_A, validWriteHeaders(), validRequest()));
    }

    @Test
    @DisplayName("idempotency key required for headers")
    void idempotency_key_required_for_headers() {
        BaseRateSelectionHeaders missingIdem = new BaseRateSelectionHeaders(
                Set.of(BaseRateSelectionApi.BASE_RATE_WRITE_PERMISSION),
                "actor-1", "corr-1", null); // no idempotency key

        UUID gridId = addPublishedGridVersion(TENANT_A,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2027-01-01T00:00:00Z"));
        addGridRow(gridId, TENANT_A, 30, new BigDecimal("6.12500"), new BigDecimal("100.00000"));

        assertThrows(BaseRateSelectionValidationException.class, () ->
                api.selectRate(TENANT_A, missingIdem, validRequest()));
    }
}
