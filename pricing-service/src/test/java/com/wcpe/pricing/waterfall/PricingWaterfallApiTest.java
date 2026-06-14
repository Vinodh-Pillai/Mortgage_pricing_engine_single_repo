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
import com.wcpe.pricing.government.GovernmentPricingApi.GovernmentFeeFrequency;
import com.wcpe.pricing.government.GovernmentPricingApi.GovernmentFeeLineItem;
import com.wcpe.pricing.government.GovernmentPricingApi.GovernmentPriceOption;
import com.wcpe.pricing.government.GovernmentPricingApi.GovernmentProductCatalog;
import com.wcpe.pricing.government.GovernmentPricingApi.GovernmentProductType;
import com.wcpe.pricing.government.GovernmentPricingApi.LoanLimit;
import com.wcpe.pricing.homeequity.HomeEquityPricingApi.HomeEquityIndexCode;
import com.wcpe.pricing.homeequity.HomeEquityPricingApi.HomeEquityPriceResponse;
import com.wcpe.pricing.homeequity.HomeEquityPricingApi.HomeEquityProductType;
import com.wcpe.pricing.homeequity.HomeEquityPricingApi.HomeEquityWaterfallLine;
import com.wcpe.pricing.mi.MiPricingApi.MiPremiumType;
import com.wcpe.pricing.mi.MiPricingApi.MiPriceOption;
import com.wcpe.pricing.missingprice.MissingPriceHandlingApi.MissingPriceErrorResponse;
import com.wcpe.pricing.missingprice.MissingPriceHandlingApi.MissingPriceHandlingResponse;
import com.wcpe.pricing.missingprice.MissingPriceHandlingApi.MissingPriceIncidentStatus;
import com.wcpe.pricing.waterfall.PricingWaterfallApi.PricingWaterfallView;
import com.wcpe.pricing.waterfall.PricingWaterfallApi.PricingOutcomeContext;
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
        assertEquals(1, view.pricingOutcomeEvents().size());
        assertEquals("PricingOutcomeRecorded.v1", view.pricingOutcomeEvents().get(0).eventType());
        assertEquals(FINAL_PRICE_ID, view.pricingOutcomeEvents().get(0).outcomeId());
        assertEquals(new BigDecimal("6.12500"), view.pricingOutcomeEvents().get(0).noteRate());
        assertTrue(view.blockers().isEmpty());
    }

    @Test
    void pricingOutcomeEventCarriesProtectedClassProxiesAndControls() {
        PricingOutcomeContext context = new PricingOutcomeContext(UUID.fromString("44444444-4444-4444-4444-444444444444"),
                UUID.fromString("55555555-5555-5555-5555-555555555555"), "Black", "non-hispanic", "Female", 43,
                720, new BigDecimal("79.50"), new BigDecimal("34.25"), new BigDecimal("325000.00"), "purchase",
                "sfr", "primary", "CA", "retail", "conventional", "investor_a", 125);

        PricingWaterfallView view = api.assemble(TENANT, headers(PricingWaterfallApi.RESTRICTED_VALUE_PERMISSION),
                new WaterfallEvidence("run-test", baseSelection(), finalPrice(), null, null, context));

        assertEquals("BLACK", view.pricingOutcomeEvents().get(0).applicantRace());
        assertEquals("NON_HISPANIC", view.pricingOutcomeEvents().get(0).applicantEthnicity());
        assertEquals("FEMALE", view.pricingOutcomeEvents().get(0).applicantSex());
        assertEquals(720, view.pricingOutcomeEvents().get(0).fico());
        assertEquals(new BigDecimal("79.50"), view.pricingOutcomeEvents().get(0).ltv());
        assertEquals(125, view.pricingOutcomeEvents().get(0).marginBps());
    }

    @Test
    void waterfallIncludesMortgageInsuranceLineItemsAndReplayEvidence() {
        PricingWaterfallView view = api.assemble(TENANT, headers(PricingWaterfallApi.RESTRICTED_VALUE_PERMISSION),
                new WaterfallEvidence("run-test", baseSelection(), finalPriceWithMi(), null, null));

        assertEquals(1, view.miLineItems().size());
        assertEquals("MGIC", view.miLineItems().get(0).carrier());
        assertEquals("BPMI_MONTHLY", view.miLineItems().get(0).premiumType());
        assertEquals(new BigDecimal("153.33"), view.miLineItems().get(0).monthlyPremium());
        assertEquals("mi-version-mgic", view.miLineItems().get(0).versionRef());
        assertEquals("mi-replay-hash-1", view.miLineItems().get(0).replayHash());
    }

    @Test
    void waterfallIncludesGovernmentPricingLineItemsAndReplayEvidence() {
        PricingWaterfallView view = api.assemble(TENANT, headers(PricingWaterfallApi.RESTRICTED_VALUE_PERMISSION),
                new WaterfallEvidence("run-test", baseSelection(), finalPriceWithGovernment(), null, null));

        assertEquals(2, view.governmentLineItems().size());
        assertEquals("FHA", view.governmentLineItems().get(0).productType());
        assertEquals("FHA_UPFRONT_MIP", view.governmentLineItems().get(0).feeType());
        assertEquals(new BigDecimal("7000.00"), view.governmentLineItems().get(0).amount());
        assertEquals("gov-fee-replay-1", view.governmentLineItems().get(0).replayHash());
    }

    @Test
    void waterfallIncludesHomeEquityPricingEvidence() {
        PricingWaterfallView view = api.assemble(TENANT, headers(PricingWaterfallApi.RESTRICTED_VALUE_PERMISSION),
                new WaterfallEvidence("run-test", baseSelection(), finalPrice(), null, null, null, homeEquityPrice()));

        assertEquals("READY", view.status());
        assertEquals(2, view.homeEquityLineItems().size());
        assertEquals("INDEX_RATE", view.homeEquityLineItems().get(0).step());
        assertEquals("prime-feed:v1", view.homeEquityLineItems().get(0).configRef());
        assertEquals("DRAW_PAYMENT", view.homeEquityLineItems().get(1).step());
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

    private static FinalPriceResponse finalPriceWithMi() {
        return new FinalPriceResponse(FINAL_PRICE_ID, new BigDecimal("6.12500"), 30, new BigDecimal("100.00000"),
                List.of(), new BigDecimal("100.00000000"), List.of(), List.of(new MiPriceOption(
                        "MGIC", MiPremiumType.BPMI_MONTHLY, 25, new BigDecimal("0.46"), null,
                        new BigDecimal("0.00000000"), new BigDecimal("153.33"), BigDecimal.ZERO,
                        new BigDecimal("153.3300"), "mgic:row-1", "mi-version-mgic", "mi-replay-hash-1", 1,
                        List.of("ltv=90.00", "fico=740"))), new BigDecimal("100.12500"),
                List.of(
                        new FinalPriceLedgerEntry(1, "BASE_PRICE", new BigDecimal("100.00000000"), "START",
                                new BigDecimal("100.00000000"), "grid-version-1", "BASE_RATE_SELECTED", null),
                        new FinalPriceLedgerEntry(2, "MORTGAGE_INSURANCE", new BigDecimal("100.00000000"), "INCLUDE",
                                new BigDecimal("100.00000000"), "mi-version-mgic:mgic:row-1", "MI_MGIC_BPMI_MONTHLY", null),
                        new FinalPriceLedgerEntry(3, "ROUND_FINAL_PRICE", new BigDecimal("100.00000000"), "ROUND",
                                new BigDecimal("100.12500000"), "rounding-version-1:round-final", "ROUND_FINAL_PRICE",
                                java.math.RoundingMode.HALF_UP)),
                new VersionGraph(List.of("grid-version-1", "rounding-version-1", "mi-version-mgic"), "version-hash-mi"),
                "result-hash-mi", "pricing:final-price:tenant-a:scenario-hash-1");
    }

    private static FinalPriceResponse finalPriceWithGovernment() {
        GovernmentPriceOption government = new GovernmentPriceOption(
                new GovernmentProductCatalog(GovernmentProductType.FHA, "FHA-30", "GOV-INVESTOR", "RETAIL", 10,
                        "catalog:fha"),
                List.of(
                        new GovernmentFeeLineItem("FHA_UPFRONT_MIP", new BigDecimal("7000.00"),
                                new BigDecimal("1.75000000"), GovernmentFeeFrequency.UPFRONT,
                                "hud:mip:2026", "fha-mip-v1", "gov-fee-replay-1", List.of("countyFips=06037")),
                        new GovernmentFeeLineItem("FHA_ANNUAL_MIP", new BigDecimal("2650.00"),
                                new BigDecimal("0.66250000"), GovernmentFeeFrequency.ANNUAL,
                                "hud:mip:2026", "fha-mip-v1", "gov-fee-replay-2", List.of("countyFips=06037"))),
                new LoanLimit(new BigDecimal("524225.00"), "hud:2026:06037", "fha-limit-v1"),
                null, null, "gov-fha-v1", "gov-option-replay", List.of("productType=FHA"));
        return new FinalPriceResponse(FINAL_PRICE_ID, new BigDecimal("6.12500"), 30, new BigDecimal("100.00000"),
                List.of(), new BigDecimal("100.00000000"), List.of(), List.of(), List.of(government),
                new BigDecimal("100.12500"),
                List.of(
                        new FinalPriceLedgerEntry(1, "BASE_PRICE", new BigDecimal("100.00000000"), "START",
                                new BigDecimal("100.00000000"), "grid-version-1", "BASE_RATE_SELECTED", null),
                        new FinalPriceLedgerEntry(2, "GOVERNMENT_FEE", new BigDecimal("100.00000000"), "INCLUDE",
                                new BigDecimal("100.00000000"), "fha-mip-v1:hud:mip:2026", "FHA_UPFRONT_MIP", null),
                        new FinalPriceLedgerEntry(3, "ROUND_FINAL_PRICE", new BigDecimal("100.00000000"), "ROUND",
                                new BigDecimal("100.12500000"), "rounding-version-1:round-final", "ROUND_FINAL_PRICE",
                                java.math.RoundingMode.HALF_UP)),
                new VersionGraph(List.of("grid-version-1", "rounding-version-1", "gov-fha-v1"), "version-hash-gov"),
                "result-hash-gov", "pricing:final-price:tenant-a:scenario-hash-1");
    }

    private static HomeEquityPriceResponse homeEquityPrice() {
        return new HomeEquityPriceResponse(UUID.fromString("66666666-6666-6666-6666-666666666666"), TENANT,
                "scenario-heloc-1", HomeEquityProductType.HELOC, "PRICED", HomeEquityIndexCode.PRIME,
                new BigDecimal("8.50000"), 125, 200, new BigDecimal("80.00000"), new BigDecimal("10.50000"),
                new BigDecimal("10.50000"), new BigDecimal("12.50000"), new BigDecimal("16.50000"),
                new BigDecimal("525.00"), new BigDecimal("1349.17"), List.of(), List.of(),
                List.of(
                        new HomeEquityWaterfallLine("INDEX_RATE", BigDecimal.ZERO, new BigDecimal("8.50000"),
                                "prime-feed:v1", "PRIME"),
                        new HomeEquityWaterfallLine("DRAW_PAYMENT", new BigDecimal("60000.00"),
                                new BigDecimal("525.00"), "home-equity-config:v1:drawPeriodMonths=120",
                                "INTEREST_ONLY_DRAW_PERIOD")),
                List.of("home-equity-config:v1", "rate-index:v1"), "heloc-result-hash", "corr-heloc-1");
    }
}
