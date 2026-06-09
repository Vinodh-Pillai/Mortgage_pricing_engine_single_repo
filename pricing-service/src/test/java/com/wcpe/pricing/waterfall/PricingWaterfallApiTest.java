package com.wcpe.pricing.waterfall;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.wcpe.pricing.baserate.BaseRateSelectionApi.BaseRateSelectionResponse;
import com.wcpe.pricing.baserate.BaseRateSelectionApi.CandidateRate;
import com.wcpe.pricing.baserate.BaseRateSelectionApi.LedgerEntry;
import com.wcpe.pricing.finalprice.FinalPriceApi.FinalPriceLedgerEntry;
import com.wcpe.pricing.finalprice.FinalPriceApi.FinalPriceResponse;
import com.wcpe.pricing.finalprice.FinalPriceApi.VersionGraph;
import com.wcpe.pricing.missingprice.MissingPriceHandlingApi.MissingPriceErrorResponse;
import com.wcpe.pricing.missingprice.MissingPriceHandlingApi.MissingPriceHandlingResponse;
import com.wcpe.pricing.missingprice.MissingPriceHandlingApi.MissingPriceIncidentStatus;
import com.wcpe.pricing.waterfall.PricingWaterfallApi.PricingWaterfallView;
import com.wcpe.pricing.waterfall.PricingWaterfallApi.WaterfallEvidence;
import com.wcpe.pricing.waterfall.PricingWaterfallApi.WaterfallHeaders;

class PricingWaterfallApiTest {
    private static final String TENANT = "tenant-a";
    private static final UUID SELECTION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID GRID_VERSION_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID FINAL_PRICE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private final PricingWaterfallApi api = new PricingWaterfallApi();

    @Test
    void waterfallShowsBackendOwnedRefsLedgerAndHashesWhenAuthorized() {
        PricingWaterfallView view = api.assemble(TENANT, headers(PricingWaterfallApi.RESTRICTED_VALUE_PERMISSION),
                new WaterfallEvidence("run-test", baseSelection(), finalPrice(), null, null));

        assertEquals("READY", view.status());
        assertEquals(SELECTION_ID, view.baseSelectionId());
        assertEquals(GRID_VERSION_ID.toString(), view.gridVersionRef());
        assertEquals("6.12500", view.selectedNoteRate().value());
        assertEquals("100.12500", view.roundedFinalPrice().value());
        assertFalse(view.roundedFinalPrice().redacted());
        assertEquals(2, view.ledger().size());
        assertEquals("ROUND_FINAL_PRICE", view.ledger().get(1).step());
        assertEquals(List.of("grid-version-1", "rounding-version-1"), view.versionRefs());
        assertEquals("result-hash-1", view.resultHash());
        assertEquals("version-hash-1", view.versionGraphHash());
        assertNotNull(view.evidenceHash());
        assertTrue(view.blockers().isEmpty());
    }

    @Test
    void waterfallRedactsRestrictedValuesWithoutLeakingNumbers() {
        PricingWaterfallView view = api.assemble(TENANT, headers(),
                new WaterfallEvidence("run-test", baseSelection(), finalPrice(), null, null));

        assertNull(view.selectedNoteRate().value());
        assertTrue(view.selectedNoteRate().redacted());
        assertTrue(view.selectedNoteRate().reason().contains("restricted.read"));
        assertNull(view.ledger().get(0).outputValue().value());
        assertTrue(view.ledger().get(0).outputValue().redacted());
    }

    @Test
    void waterfallRecordsExplicitBlockersWhenEvidenceIsMissingOrBlocked() {
        MissingPriceErrorResponse error = new MissingPriceErrorResponse("PRICE_GRID_MISSING",
                "no active pricing grid is available for the requested context",
                "publish an active grid or change the pricing as-of context", "missing-price-incident:required", "corr-1", 422);
        MissingPriceHandlingResponse missing = new MissingPriceHandlingResponse(UUID.randomUUID(),
                MissingPriceIncidentStatus.OPEN, 1, "missing price incident created", error, "audit:missing-price", "replay:missing-price", "corr-1");

        PricingWaterfallView view = api.assemble(TENANT, headers(),
                new WaterfallEvidence("run-test", null, null, missing, null));

        assertEquals("BLOCKED", view.status());
        assertEquals(3, view.blockers().size());
        assertTrue(view.blockers().stream().anyMatch(blocker -> "BASE_SELECTION_MISSING".equals(blocker.code())));
        assertTrue(view.blockers().stream().anyMatch(blocker -> "FINAL_PRICE_MISSING".equals(blocker.code())));
        assertTrue(view.blockers().stream().anyMatch(blocker -> "PRICE_GRID_MISSING".equals(blocker.code())));
        assertEquals("audit:missing-price", view.missingPriceAuditRef());
    }

    private static WaterfallHeaders headers(String... extraPermissions) {
        Set<String> permissions = new java.util.HashSet<>();
        permissions.add(PricingWaterfallApi.WATERFALL_READ_PERMISSION);
        permissions.addAll(List.of(extraPermissions));
        return new WaterfallHeaders(permissions, "actor-1", "corr-1");
    }

    private static BaseRateSelectionResponse baseSelection() {
        return new BaseRateSelectionResponse(SELECTION_ID, GRID_VERSION_ID, new BigDecimal("6.12500"),
                new BigDecimal("100.00000"),
                List.of(new CandidateRate(new BigDecimal("6.12500"), new BigDecimal("100.00000"), 1, "GRID_MATCH")),
                30, Instant.parse("2026-06-01T00:00:00Z"),
                List.of(new LedgerEntry("RATE_SELECTION", "Selected backend-owned base price", new BigDecimal("100.00000"), "GRID_MATCH")),
                List.of(), "selection-hash-1");
    }

    private static FinalPriceResponse finalPrice() {
        return new FinalPriceResponse(FINAL_PRICE_ID, new BigDecimal("6.12500"), 30, new BigDecimal("100.00000"),
                List.of(), new BigDecimal("100.00000000"), List.of(), new BigDecimal("100.12500"),
                List.of(
                        new FinalPriceLedgerEntry(1, "BASE_PRICE", new BigDecimal("100.00000000"), "START",
                                new BigDecimal("100.00000000"), "grid-version-1", "BASE_RATE_SELECTED", null),
                        new FinalPriceLedgerEntry(2, "ROUND_FINAL_PRICE", new BigDecimal("100.00000000"), "ROUND",
                                new BigDecimal("100.12500000"), "rounding-version-1:round-final", "ROUND_FINAL_PRICE",
                                java.math.RoundingMode.HALF_UP)),
                new VersionGraph(List.of("grid-version-1", "rounding-version-1"), "version-hash-1"),
                "result-hash-1", "pricing:final-price:tenant-a:scenario-hash-1");
    }
}
