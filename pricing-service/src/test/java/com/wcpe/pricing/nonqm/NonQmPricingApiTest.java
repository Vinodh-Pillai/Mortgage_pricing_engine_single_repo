package com.wcpe.pricing.nonqm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.wcpe.pricing.nonqm.NonQmPricingApi.EligibilityDecision;
import com.wcpe.pricing.nonqm.NonQmPricingApi.EligibilityStatus;
import com.wcpe.pricing.nonqm.NonQmPricingApi.NonQmBatchPriceResult;
import com.wcpe.pricing.nonqm.NonQmPricingApi.NonQmImportResult;
import com.wcpe.pricing.nonqm.NonQmPricingApi.NonQmMarginPolicy;
import com.wcpe.pricing.nonqm.NonQmPricingApi.NonQmPriceResult;
import com.wcpe.pricing.nonqm.NonQmPricingApi.NonQmPricingAdjustmentRef;
import com.wcpe.pricing.nonqm.NonQmPricingApi.NonQmPricingHeaders;
import com.wcpe.pricing.nonqm.NonQmPricingApi.NonQmPricingRequest;
import com.wcpe.pricing.nonqm.NonQmPricingApi.NonQmProductType;
import com.wcpe.pricing.nonqm.NonQmPricingApi.NonQmRateRow;
import com.wcpe.pricing.nonqm.NonQmPricingApi.NonQmRateSheet;
import com.wcpe.pricing.nonqm.NonQmPricingApi.LocGrowthPolicy;
import com.wcpe.pricing.nonqm.NonQmPricingApi.MipBasis;
import com.wcpe.pricing.nonqm.NonQmPricingApi.MipPolicy;
import com.wcpe.pricing.nonqm.NonQmPricingApi.PaymentOption;
import com.wcpe.pricing.nonqm.NonQmPricingApi.PrincipalLimitFactor;
import com.wcpe.pricing.nonqm.NonQmPricingApi.PrincipalLimitTable;
import com.wcpe.pricing.nonqm.NonQmPricingApi.RateSheetSource;
import com.wcpe.pricing.nonqm.NonQmPricingApi.RateSheetStatus;
import com.wcpe.pricing.nonqm.NonQmPricingApi.ReversePricingBreakdown;
import com.wcpe.pricing.nonqm.NonQmPricingApi.ReversePricingInputs;
import com.wcpe.pricing.nonqm.NonQmPricingApi.ReverseProgramConfig;
import com.wcpe.pricing.nonqm.NonQmPricingApi.ReverseProgramType;
import com.wcpe.pricing.nonqm.NonQmPricingApi.ServicingFeeSetAsidePolicy;
import com.wcpe.pricing.nonqm.NonQmPricingApi.StaticNonQmRateSheetResolver;

class NonQmPricingApiTest {
    private static final String TENANT = "tenant-a";
    private static final Instant AS_OF = Instant.parse("2026-06-13T00:00:00Z");

    @Test
    void dscrPricingSelectsConfiguredTierRowAndAppliesAdjustmentAndMarginWaterfall() {
        NonQmPricingApi api = new NonQmPricingApi(new StaticNonQmRateSheetResolver(List.of(dscrSheet())));

        NonQmPriceResult result = api.price(TENANT, headers(), dscrRequest(Map.of(
                "dscrTier", "DSCR_1_00_1_15",
                "ficoBand", "720_739",
                "ltvBand", "70_75",
                "term", "30Y",
                "incomeMethod", "LEASE_RENT")));

        assertEquals("PRICED", result.status());
        assertEquals("dscr-row-1", result.rowId());
        assertEquals(new BigDecimal("7.25000"), result.baseNoteRate());
        assertEquals(new BigDecimal("7.62500"), result.finalNoteRate());
        assertEquals(new BigDecimal("99.87500"), result.finalPrice());
        assertTrue(result.waterfall().lines().stream().anyMatch(line -> "BASE_RATE_SHEET_ROW".equals(line.step())
                && "dscr-rate-sheet:v1:dscr-row-1".equals(line.configRef())));
        assertTrue(result.waterfall().lines().stream().anyMatch(line -> "PII_33_ADJUSTMENT".equals(line.step())
                && "DSCR_RATIO_SPECIALTY_PREMIUM".equals(line.reasonCode())));
        assertTrue(result.waterfall().lines().stream().anyMatch(line -> "PII_34_MARGIN".equals(line.step())
                && "NON_QM_CAPITAL_MARKETS_MARGIN".equals(line.reasonCode())));
        assertTrue(result.versionRefs().contains("pii33-nonqm-adjustments:v1:dscr-ratio"));
        assertTrue(result.versionRefs().contains("nonqm-margin:v1"));
        assertNotNull(result.resultHash());
    }

    @Test
    void allRequestedNonQmProductTypesUseRegisteredConfiguredDimensionStrategies() {
        for (NonQmProductType type : NonQmProductType.values()) {
            NonQmPricingApi api = new NonQmPricingApi(new StaticNonQmRateSheetResolver(List.of(genericSheet(type))));

            NonQmPriceResult result = api.price(TENANT, headers(), genericRequest(type, factsFor(type)));

            assertEquals("PRICED", result.status(), type.name());
            assertEquals(type, result.productType());
            assertEquals(type.name() + "-row", result.rowId());
            assertFalse(result.waterfall().lines().isEmpty());
        }
    }

    @Test
    void constructionPricingUsesLtcReservesDrawScheduleAndWaterfallEvidence() {
        NonQmPricingApi api = new NonQmPricingApi(new StaticNonQmRateSheetResolver(List.of(constructionSheet())));

        NonQmPriceResult result = api.price(TENANT, headers(), new NonQmPricingRequest(TENANT, "scenario-construction-1",
                NonQmProductType.CONSTRUCTION, "INV-CONSTRUCTION", "BROKER", AS_OF,
                Map.of("projectType", "GROUND_UP", "ltcBand", "LTC_65_70", "reserveBand", "RESERVE_6_9_MONTHS",
                        "builderStatus", "APPROVED", "drawScheduleStatus", "COMPLETE", "term", "12M", "prepayPenalty", "NONE"),
                Map.of("construction.landOrPurchaseCost", new BigDecimal("170000.00"),
                        "construction.hardCostBudget", new BigDecimal("110000.00"),
                        "construction.softCostBudget", new BigDecimal("20000.00"),
                        "construction.loanAmount", new BigDecimal("210000.00"),
                        "construction.completionReserve", new BigDecimal("25000.00"),
                        "construction.interestReserve", new BigDecimal("18000.00"),
                        "construction.drawScheduleTotal", new BigDecimal("130000.00"),
                        "construction.drawCount", new BigDecimal("4")),
                eligibility(), marginPolicies()));

        assertEquals("PRICED", result.status());
        assertEquals("construction-row-1", result.rowId());
        assertTrue(result.waterfall().lines().stream().anyMatch(line -> "CONSTRUCTION_LTC".equals(line.reasonCode())));
        assertTrue(result.waterfall().lines().stream().anyMatch(line -> "CONSTRUCTION_DRAW_SCHEDULE".equals(line.reasonCode())));
        assertTrue(result.waterfall().lines().stream().anyMatch(line -> "BASE_RATE_SHEET_ROW".equals(line.step())));
    }

    @Test
    void fixFlipPricingUsesAfterRepairValueLtarvAndInterestReserveRiskTerms() {
        NonQmPricingApi api = new NonQmPricingApi(new StaticNonQmRateSheetResolver(List.of(fixFlipSheet())));

        NonQmPriceResult result = api.price(TENANT, headers(), new NonQmPricingRequest(TENANT, "scenario-fix-flip-1",
                NonQmProductType.FIX_FLIP, "INV-FIXFLIP", "BROKER", AS_OF,
                Map.of("ltarvBand", "LTARV_65_70", "rehabBudgetBand", "REHAB_40_60K", "drawScheduleStatus", "COMPLETE",
                        "term", "12M", "exitStrategy", "SALE", "prepayPenalty", "SHORT_TERM_PREPAY"),
                Map.of("fixFlip.purchasePrice", new BigDecimal("250000.00"),
                        "fixFlip.rehabBudget", new BigDecimal("50000.00"),
                        "fixFlip.afterRepairValue", new BigDecimal("400000.00"),
                        "fixFlip.loanAmount", new BigDecimal("280000.00"),
                        "fixFlip.termMonths", new BigDecimal("12"),
                        "fixFlip.drawScheduleTotal", new BigDecimal("50000.00"),
                        "fixFlip.drawCount", new BigDecimal("3")),
                eligibility(), marginPolicies()));

        assertEquals("PRICED", result.status());
        assertEquals("fix-flip-row-1", result.rowId());
        assertTrue(result.waterfall().lines().stream().anyMatch(line -> "FIX_FLIP_LTARV".equals(line.reasonCode())));
        assertTrue(result.waterfall().lines().stream().anyMatch(line -> "FIX_FLIP_DRAW_SCHEDULE".equals(line.reasonCode())));
        assertTrue(result.waterfall().lines().stream().anyMatch(line -> "FIX_FLIP_PREPAY_SHORT_TERM".equals(line.reasonCode())));
    }

    @Test
    void rentalPortfolioPricingUsesPortfolioDscrBlanketLoanAndCrossCollateralFacts() {
        NonQmPricingApi api = new NonQmPricingApi(new StaticNonQmRateSheetResolver(List.of(rentalPortfolioSheet())));

        NonQmPriceResult result = api.price(TENANT, headers(), new NonQmPricingRequest(TENANT, "scenario-rental-portfolio-1",
                NonQmProductType.RENTAL_PORTFOLIO, "INV-PORTFOLIO", "BROKER", AS_OF,
                Map.of("entityType", "LLC", "portfolioDscrBand", "DSCR_1_15_1_30", "propertyCountBand", "3_5",
                        "crossCollateral", "true", "guarantorType", "PERSONAL", "blanketLoan", "true",
                        "propertyScheduleStatus", "COMPLETE", "prepayPenalty", "5_4_3"),
                Map.of("rentalPortfolio.noi", new BigDecimal("120000.00"),
                        "rentalPortfolio.debtService", new BigDecimal("100000.00"),
                        "rentalPortfolio.loanAmount", new BigDecimal("650000.00"),
                        "rentalPortfolio.totalCollateralValue", new BigDecimal("1000000.00"),
                        "rentalPortfolio.propertyCount", new BigDecimal("4")),
                eligibility(), marginPolicies()));

        assertEquals("PRICED", result.status());
        assertEquals("rental-portfolio-row-1", result.rowId());
        assertTrue(result.waterfall().lines().stream().anyMatch(line -> "RENTAL_PORTFOLIO_DSCR".equals(line.reasonCode())));
        assertTrue(result.waterfall().lines().stream().anyMatch(line -> "RENTAL_PORTFOLIO_CROSS_COLLATERAL".equals(line.reasonCode())));
        assertTrue(result.waterfall().lines().stream().anyMatch(line -> "RENTAL_PORTFOLIO_BLANKET_LTV".equals(line.reasonCode())));
    }

    @Test
    void fixFlipPricingBlocksWhenArvIsMissing() {
        NonQmPricingApi api = new NonQmPricingApi(new StaticNonQmRateSheetResolver(List.of(fixFlipSheet())));

        NonQmPriceResult result = api.price(TENANT, headers(), new NonQmPricingRequest(TENANT, "scenario-fix-flip-missing-arv",
                NonQmProductType.FIX_FLIP, "INV-FIXFLIP", "BROKER", AS_OF,
                Map.of("ltarvBand", "LTARV_65_70", "rehabBudgetBand", "REHAB_40_60K", "drawScheduleStatus", "COMPLETE",
                        "term", "12M", "exitStrategy", "SALE"),
                Map.of("fixFlip.purchasePrice", new BigDecimal("250000.00"),
                        "fixFlip.rehabBudget", new BigDecimal("50000.00"),
                        "fixFlip.loanAmount", new BigDecimal("280000.00"),
                        "fixFlip.drawScheduleTotal", new BigDecimal("50000.00"),
                        "fixFlip.drawCount", new BigDecimal("3")),
                eligibility(), marginPolicies()));

        assertEquals("BLOCKED", result.status());
        assertEquals("FIX_FLIP_FACT_INVALID", result.blockers().get(0).code());
        assertTrue(result.blockers().get(0).message().contains("fixFlip.afterRepairValue"));
    }

    @Test
    void missingRateSheetReturnsRateSheetMissingWithoutFabricatedPrice() {
        NonQmPricingApi api = new NonQmPricingApi(new StaticNonQmRateSheetResolver(List.of()));

        NonQmPriceResult result = api.price(TENANT, headers(), dscrRequest(Map.of(
                "dscrTier", "DSCR_1_00_1_15",
                "ficoBand", "720_739",
                "ltvBand", "70_75")));

        assertEquals("BLOCKED", result.status());
        assertEquals("RATE_SHEET_MISSING", result.blockers().get(0).code());
        assertEquals(null, result.finalNoteRate());
        assertEquals(null, result.finalPrice());
    }

    @Test
    void eligibilityServiceBlockedDecisionPreventsPricing() {
        NonQmPricingApi api = new NonQmPricingApi(new StaticNonQmRateSheetResolver(List.of(dscrSheet())));
        NonQmPricingRequest request = new NonQmPricingRequest(TENANT, "scenario-dscr-1", NonQmProductType.DSCR,
                "INV-A", "BROKER", AS_OF, Map.of("dscrTier", "DSCR_1_00_1_15", "ficoBand", "720_739", "ltvBand", "70_75"),
                Map.of(), new EligibilityDecision(EligibilityStatus.INELIGIBLE, "eligibility:nonqm:1", "MIN_DSCR_NOT_MET"),
                marginPolicies());

        NonQmPriceResult result = api.price(TENANT, headers(), request);

        assertEquals("BLOCKED", result.status());
        assertEquals("NON_QM_NOT_ELIGIBLE", result.blockers().get(0).code());
        assertEquals("eligibility:nonqm:1", result.blockers().get(0).sourceRef());
    }

    @Test
    void priceBatchRanksBestExecutionForNonQmProducts() {
        NonQmPricingApi api = new NonQmPricingApi(new StaticNonQmRateSheetResolver(List.of(dscrSheet(), betterDscrSheet())));

        NonQmBatchPriceResult batch = api.priceBatch(TENANT, headers(), List.of(
                dscrRequest(Map.of("dscrTier", "DSCR_1_00_1_15", "ficoBand", "720_739", "ltvBand", "70_75", "term", "30Y", "incomeMethod", "LEASE_RENT")),
                new NonQmPricingRequest(TENANT, "scenario-dscr-2", NonQmProductType.DSCR, "INV-B", "BROKER", AS_OF,
                        Map.of("dscrTier", "DSCR_1_00_1_15", "ficoBand", "720_739", "ltvBand", "70_75"),
                        Map.of(), eligibility(), marginPolicies())));

        assertEquals(2, batch.results().size());
        assertEquals(batch.bestExecutionPriceId(), batch.results().get(0).priceId());
        assertEquals(new BigDecimal("7.00000"), batch.results().get(0).baseNoteRate());
        assertEquals("INV-B", batch.results().get(0).investorCode());
    }

    @Test
    void ppeImportExportPreservesTierKeysAndPriceValues() {
        NonQmPricingApi api = new NonQmPricingApi(new StaticNonQmRateSheetResolver(List.of()));

        NonQmImportResult imported = api.importRateSheet("ob-nonqm-1", "INV-A", "BROKER", NonQmProductType.BANK_STATEMENT,
                4, LocalDate.parse("2026-06-01"), RateSheetSource.OPTIMAL_BLUE,
                List.of(Map.of(
                        "rowId", "ob-row-1",
                        "rate", "8.12500",
                        "price", "99.37500",
                        "tier.statementType", "PERSONAL",
                        "tier.statementMonths", "24",
                        "tier.ficoBand", "700_719",
                        "tier.ltvBand", "65_70",
                        "investorProductCode", "INV-BANK-STMT")),
                "nonqm-margin:v1");

        assertTrue(imported.blockers().isEmpty());
        Map<String, String> exported = api.exportRateSheet(imported.rateSheet(), RateSheetSource.LOANPASS).get(0);

        assertEquals("LOANPASS", exported.get("format"));
        assertEquals("8.12500", exported.get("noteRate"));
        assertEquals("99.37500", exported.get("basePrice"));
        assertEquals("PERSONAL", exported.get("tier.statementType"));
        assertEquals("24", exported.get("tier.statementMonths"));
        assertEquals("INV-BANK-STMT", exported.get("investorProductCode"));
    }

    @Test
    void hecmReversePricingCalculatesPlfMipServicingSetAsideAndLocProceeds() {
        NonQmPricingApi api = new NonQmPricingApi(new StaticNonQmRateSheetResolver(List.of()));

        ReversePricingBreakdown breakdown = api.priceReverse(hecmConfig(), new ReversePricingInputs(72,
                new BigDecimal("500000.00"), new BigDecimal("100000.00"), new BigDecimal("3.00000"),
                new BigDecimal("2.00000"), PaymentOption.LINE_OF_CREDIT, "CA", "SFR", 120));

        assertTrue(breakdown.blockers().isEmpty());
        assertEquals(new BigDecimal("5.00000"), breakdown.expectedRate());
        assertEquals(new BigDecimal("0.500000"), breakdown.principalLimitFactor().factor());
        assertEquals(new BigDecimal("250000.00"), breakdown.principalLimit());
        assertEquals(new BigDecimal("10000.00"), breakdown.mip().initialMip());
        assertEquals(new BigDecimal("1250.00"), breakdown.mip().annualMipEstimate());
        assertEquals(new BigDecimal("4200.00"), breakdown.servicingFeeSetAside().amount());
        assertEquals(new BigDecimal("132800.00"), breakdown.netAvailable());
        assertEquals(PaymentOption.LINE_OF_CREDIT, breakdown.paymentOptionEstimate().option());
        assertEquals(new BigDecimal("0.00500"), breakdown.paymentOptionEstimate().locGrowthRate());
        assertTrue(breakdown.auditRefs().contains("hecm-plf-2026:v3"));
    }

    @Test
    void missingReversePlfReturnsBlockerWithoutFabricatedProceeds() {
        NonQmPricingApi api = new NonQmPricingApi(new StaticNonQmRateSheetResolver(List.of()));

        ReversePricingBreakdown breakdown = api.priceReverse(hecmConfig(), new ReversePricingInputs(55,
                new BigDecimal("500000.00"), BigDecimal.ZERO, new BigDecimal("9.00000"), new BigDecimal("2.00000"),
                PaymentOption.LUMP_SUM, "CA", "SFR", 0));

        assertEquals("REVERSE_PLF_MISSING", breakdown.blockers().get(0).code());
        assertEquals(null, breakdown.principalLimit());
        assertEquals(null, breakdown.netAvailable());
    }

    @Test
    void reverseMortgageWaterfallSelectsConfiguredInvestorTierAndAuditsExpectedRate() {
        NonQmPricingApi api = new NonQmPricingApi(new StaticNonQmRateSheetResolver(List.of(reverseRateSheet())));

        NonQmPriceResult result = api.price(TENANT, headers(), reverseRequest());

        assertEquals("PRICED", result.status());
        assertEquals(NonQmProductType.REVERSE_MORTGAGE, result.productType());
        assertEquals("hecm-72-loc-row", result.rowId());
        assertEquals(new BigDecimal("5.25000"), result.finalNoteRate());
        assertTrue(result.waterfall().lines().stream().anyMatch(line -> "NON_QM_SPECIALTY_PREMIUM".equals(line.step())
                && "REVERSE_EXPECTED_RATE_INDEX_PLUS_MARGIN".equals(line.reasonCode())
                && line.configRef().endsWith("expectedRate=5.00000")));
    }

    private static NonQmPricingHeaders headers() {
        return new NonQmPricingHeaders(Set.of(NonQmPricingApi.NON_QM_PRICE_PERMISSION), "actor-1", "corr-nonqm-1");
    }

    private static EligibilityDecision eligibility() {
        return new EligibilityDecision(EligibilityStatus.ELIGIBLE, "eligibility:nonqm:passed", "NON_QM_ELIGIBLE");
    }

    private static NonQmPricingRequest dscrRequest(Map<String, String> facts) {
        return new NonQmPricingRequest(TENANT, "scenario-dscr-1", NonQmProductType.DSCR, "INV-A", "BROKER", AS_OF,
                facts, Map.of("nonQm.dscr.ratio", new BigDecimal("1.08")), eligibility(), marginPolicies());
    }

    private static NonQmPricingRequest genericRequest(NonQmProductType type, Map<String, String> facts) {
        return new NonQmPricingRequest(TENANT, "scenario-" + type.name(), type, "INV-" + type.name(), "BROKER", AS_OF,
                facts, numericFactsFor(type), eligibility(), marginPolicies());
    }

    private static NonQmPricingRequest reverseRequest() {
        return new NonQmPricingRequest(TENANT, "scenario-reverse-1", NonQmProductType.REVERSE_MORTGAGE,
                "INV-HECM", "BROKER", AS_OF,
                Map.of("reverse.programType", "HECM", "reverse.ageBand", "AGE_70_74", "reverse.equityBand", "EQ_50_60",
                        "reverse.loanAmountBand", "250K_300K", "reverse.paymentOption", "LINE_OF_CREDIT",
                        "reverse.state", "CA", "reverse.plfTableId", "hecm-plf-2026"),
                Map.of("reverse.indexRate", new BigDecimal("3.00000"), "reverse.margin", new BigDecimal("2.00000"),
                        "reverse.principalLimit", new BigDecimal("250000.00"), "reverse.initialMip", new BigDecimal("10000.00"),
                        "reverse.annualMipEstimate", new BigDecimal("1250.00"), "reverse.servicingFeeSetAside", new BigDecimal("4200.00"),
                        "reverse.netProceeds", new BigDecimal("132800.00")),
                eligibility(), marginPolicies());
    }

    private static ReverseProgramConfig hecmConfig() {
        return new ReverseProgramConfig("HECM-STD", ReverseProgramType.HECM, "GNMA-HECM",
                new PrincipalLimitTable("hecm-plf-2026", 3,
                        List.of(new PrincipalLimitFactor(70, 74, new BigDecimal("4.50000"), new BigDecimal("5.50000"),
                                new BigDecimal("0.500000"), "hecm-plf-2026:v3:age70-74:rate4.5-5.5")),
                        "FHA-configured-table"),
                new MipPolicy("fha-mip-2026", MipBasis.MAX_CLAIM_AMOUNT, new BigDecimal("0.02000"), new BigDecimal("0.00500")),
                new LocGrowthPolicy("hecm-loc-growth-2026", new BigDecimal("0.00500"), 240),
                new ServicingFeeSetAsidePolicy("hecm-servicing-set-aside-2026", new BigDecimal("35.00"), 120),
                List.of(PaymentOption.LUMP_SUM, PaymentOption.LINE_OF_CREDIT, PaymentOption.TERM, PaymentOption.TENURE),
                null, new BigDecimal("3000.00"), Map.of("investor", "GNMA"));
    }

    private static Map<String, NonQmMarginPolicy> marginPolicies() {
        return Map.of("nonqm-margin:v1", new NonQmMarginPolicy("nonqm-margin:v1",
                new BigDecimal("0.25000"), new BigDecimal("0.12500"), "NON_QM_CAPITAL_MARKETS_MARGIN"));
    }

    private static NonQmRateSheet dscrSheet() {
        return new NonQmRateSheet("dscr-rate-sheet:v1", "INV-A", "BROKER", NonQmProductType.DSCR, 1,
                LocalDate.parse("2026-06-01"), RateSheetStatus.PUBLISHED,
                List.of(new NonQmRateRow("dscr-row-1", new BigDecimal("7.25000"), new BigDecimal("100.00000"),
                        Map.of("dscrTier", "DSCR_1_00_1_15", "ficoBand", "720_739", "ltvBand", "70_75", "term", "30Y"),
                        Map.of(), "INV-DSCR-30", "DSCR_TIER_MATCH")),
                List.of(new NonQmPricingAdjustmentRef("dscr-ratio", "pii33-nonqm-adjustments:v1", "incomeMethod", "LEASE_RENT",
                        new BigDecimal("0.12500"), new BigDecimal("-0.25000"), 10, "DSCR_RATIO_SPECIALTY_PREMIUM")),
                "nonqm-margin:v1", RateSheetSource.INTERNAL);
    }

    private static NonQmRateSheet betterDscrSheet() {
        return new NonQmRateSheet("dscr-rate-sheet:v2", "INV-B", "BROKER", NonQmProductType.DSCR, 2,
                LocalDate.parse("2026-06-01"), RateSheetStatus.PUBLISHED,
                List.of(new NonQmRateRow("dscr-row-better", new BigDecimal("7.00000"), new BigDecimal("99.50000"),
                        Map.of("dscrTier", "DSCR_1_00_1_15", "ficoBand", "720_739", "ltvBand", "70_75"),
                        Map.of(), "INV-B-DSCR", "DSCR_TIER_MATCH")),
                List.of(), "nonqm-margin:v1", RateSheetSource.INTERNAL);
    }

    private static NonQmRateSheet constructionSheet() {
        return new NonQmRateSheet("construction-rate-sheet:v1", "INV-CONSTRUCTION", "BROKER", NonQmProductType.CONSTRUCTION, 1,
                LocalDate.parse("2026-06-01"), RateSheetStatus.PUBLISHED,
                List.of(new NonQmRateRow("construction-row-1", new BigDecimal("9.25000"), new BigDecimal("98.50000"),
                        Map.of("projectType", "GROUND_UP", "ltcBand", "LTC_65_70", "reserveBand", "RESERVE_6_9_MONTHS",
                                "builderStatus", "APPROVED", "drawScheduleStatus", "COMPLETE", "term", "12M"),
                        Map.of(), "INV-CONSTRUCTION-12M", "CONSTRUCTION_PROJECT_MATCH")),
                List.of(), "nonqm-margin:v1", RateSheetSource.INTERNAL);
    }

    private static NonQmRateSheet fixFlipSheet() {
        return new NonQmRateSheet("fix-flip-rate-sheet:v1", "INV-FIXFLIP", "BROKER", NonQmProductType.FIX_FLIP, 1,
                LocalDate.parse("2026-06-01"), RateSheetStatus.PUBLISHED,
                List.of(new NonQmRateRow("fix-flip-row-1", new BigDecimal("10.00000"), new BigDecimal("97.75000"),
                        Map.of("ltarvBand", "LTARV_65_70", "rehabBudgetBand", "REHAB_40_60K", "drawScheduleStatus", "COMPLETE",
                                "term", "12M", "exitStrategy", "SALE", "prepayPenalty", "SHORT_TERM_PREPAY"),
                        Map.of(), "INV-FIXFLIP-12M", "FIX_FLIP_PROJECT_MATCH")),
                List.of(new NonQmPricingAdjustmentRef("fix-flip-short-term-prepay", "pii44-business-purpose:v1",
                        "prepayPenalty", "SHORT_TERM_PREPAY", new BigDecimal("0.25000"), new BigDecimal("-0.37500"), 10,
                        "FIX_FLIP_PREPAY_SHORT_TERM")),
                "nonqm-margin:v1", RateSheetSource.INTERNAL);
    }

    private static NonQmRateSheet rentalPortfolioSheet() {
        return new NonQmRateSheet("rental-portfolio-rate-sheet:v1", "INV-PORTFOLIO", "BROKER",
                NonQmProductType.RENTAL_PORTFOLIO, 1, LocalDate.parse("2026-06-01"), RateSheetStatus.PUBLISHED,
                List.of(new NonQmRateRow("rental-portfolio-row-1", new BigDecimal("8.87500"), new BigDecimal("99.00000"),
                        Map.of("entityType", "LLC", "portfolioDscrBand", "DSCR_1_15_1_30", "propertyCountBand", "3_5",
                                "crossCollateral", "true", "guarantorType", "PERSONAL", "blanketLoan", "true"),
                        Map.of(), "INV-PORTFOLIO-BLANKET", "RENTAL_PORTFOLIO_MATCH")),
                List.of(), "nonqm-margin:v1", RateSheetSource.INTERNAL);
    }

    private static NonQmRateSheet reverseRateSheet() {
        return new NonQmRateSheet("reverse-hecm-rate-sheet:v1", "INV-HECM", "BROKER",
                NonQmProductType.REVERSE_MORTGAGE, 1, LocalDate.parse("2026-06-01"), RateSheetStatus.PUBLISHED,
                List.of(new NonQmRateRow("hecm-72-loc-row", new BigDecimal("5.00000"), new BigDecimal("100.00000"),
                        Map.of("reverse.programType", "HECM", "reverse.ageBand", "AGE_70_74", "reverse.equityBand", "EQ_50_60",
                                "reverse.loanAmountBand", "250K_300K", "reverse.paymentOption", "LINE_OF_CREDIT",
                                "reverse.state", "CA", "reverse.plfTableId", "hecm-plf-2026"),
                        Map.of("reverse.plf", new BigDecimal("0.500000")), "GNMA-HECM-LOC", "REVERSE_HECM_TIER_MATCH")),
                List.of(), "nonqm-margin:v1", RateSheetSource.INTERNAL);
    }

    private static NonQmRateSheet genericSheet(NonQmProductType type) {
        return new NonQmRateSheet(type.name() + "-rate-sheet:v1", "INV-" + type.name(), "BROKER", type, 1,
                LocalDate.parse("2026-06-01"), RateSheetStatus.PUBLISHED,
                List.of(new NonQmRateRow(type.name() + "-row", new BigDecimal("8.00000"), new BigDecimal("100.00000"),
                        factsFor(type), Map.of(), type.name() + "-INV-CODE", type.name() + "_MATCH")),
                adjustmentsFor(type), "nonqm-margin:v1", RateSheetSource.INTERNAL);
    }

    private static List<NonQmPricingAdjustmentRef> adjustmentsFor(NonQmProductType type) {
        if (type == NonQmProductType.BANK_STATEMENT) {
            return List.of(new NonQmPricingAdjustmentRef("bank-statement-income-trend", "pii33-nonqm-adjustments:v1",
                    "incomeTrend", "DECLINING", new BigDecimal("0.05000"), BigDecimal.ZERO, 10,
                    "BANK_STATEMENT_INCOME_TREND"));
        }
        if (type == NonQmProductType.ASSET_DEPLETION) {
            return List.of(new NonQmPricingAdjustmentRef("asset-depletion-seasoning", "pii33-nonqm-adjustments:v1",
                    "seasoningBand", "12_24", new BigDecimal("0.07500"), BigDecimal.ZERO, 10,
                    "ASSET_DEPLETION_SEASONING"));
        }
        return List.of();
    }

    private static Map<String, String> factsFor(NonQmProductType type) {
        return switch (type) {
            case DSCR -> Map.of("dscrTier", "DSCR_1_00_1_15", "ficoBand", "720_739", "ltvBand", "70_75");
            case CONSTRUCTION -> Map.of("projectType", "GROUND_UP", "ltcBand", "LTC_65_70", "reserveBand", "RESERVE_6_9_MONTHS",
                    "builderStatus", "APPROVED", "drawScheduleStatus", "COMPLETE");
            case FIX_FLIP -> Map.of("ltarvBand", "LTARV_65_70", "rehabBudgetBand", "REHAB_40_60K",
                    "drawScheduleStatus", "COMPLETE", "term", "12M", "exitStrategy", "SALE");
            case RENTAL_PORTFOLIO -> Map.of("entityType", "LLC", "portfolioDscrBand", "DSCR_1_15_1_30",
                    "propertyCountBand", "3_5", "crossCollateral", "true", "propertyScheduleStatus", "COMPLETE");
            case BUSINESS_PURPOSE -> Map.of("businessPurposeType", "INVESTMENT_PROPERTY", "entityType", "LLC", "ltvBand", "65_70");
            case BANK_STATEMENT -> Map.of("statementType", "PERSONAL", "statementMonths", "24", "ficoBand", "700_719",
                    "ltvBand", "65_70", "incomeTrend", "DECLINING");
            case ASSET_DEPLETION -> Map.of("assetType", "LIQUID", "assetIncomeMethod", "AMORTIZED", "seasoningBand", "12_24",
                    "ficoBand", "720_739", "ltvBand", "60_65");
            case NO_RATIO -> Map.of("ficoBand", "740_759", "ltvBand", "60_65", "occupancy", "INVESTMENT");
            case FOREIGN_NATIONAL -> Map.of("countryTier", "TIER_1", "ltvBand", "55_60", "creditProfile", "INTERNATIONAL_CREDIT");
            case ITIN -> Map.of("itinStatus", "VALID", "ficoBand", "700_719", "ltvBand", "65_70");
            case ONE099_ONLY -> Map.of("documentType", "1099", "businessHistoryBand", "24_PLUS_MONTHS", "ficoBand", "720_739",
                    "ltvBand", "70_75");
            case REVERSE_MORTGAGE -> Map.of("reverse.programType", "HECM", "reverse.ageBand", "70_74",
                    "reverse.equityBand", "50_60", "reverse.loanAmountBand", "LOW_BALANCE",
                    "reverse.paymentOption", "LINE_OF_CREDIT");
        };
    }

    private static Map<String, BigDecimal> numericFactsFor(NonQmProductType type) {
        return switch (type) {
            case CONSTRUCTION -> Map.of("construction.landOrPurchaseCost", new BigDecimal("170000.00"),
                    "construction.hardCostBudget", new BigDecimal("110000.00"),
                    "construction.softCostBudget", new BigDecimal("20000.00"),
                    "construction.loanAmount", new BigDecimal("210000.00"),
                    "construction.completionReserve", new BigDecimal("25000.00"),
                    "construction.interestReserve", new BigDecimal("18000.00"),
                    "construction.drawScheduleTotal", new BigDecimal("130000.00"),
                    "construction.drawCount", new BigDecimal("4"));
            case FIX_FLIP -> Map.of("fixFlip.purchasePrice", new BigDecimal("250000.00"),
                    "fixFlip.rehabBudget", new BigDecimal("50000.00"),
                    "fixFlip.afterRepairValue", new BigDecimal("400000.00"),
                    "fixFlip.loanAmount", new BigDecimal("280000.00"),
                    "fixFlip.termMonths", new BigDecimal("12"),
                    "fixFlip.drawScheduleTotal", new BigDecimal("50000.00"),
                    "fixFlip.drawCount", new BigDecimal("3"));
            case RENTAL_PORTFOLIO -> Map.of("rentalPortfolio.noi", new BigDecimal("120000.00"),
                    "rentalPortfolio.debtService", new BigDecimal("100000.00"),
                    "rentalPortfolio.loanAmount", new BigDecimal("650000.00"),
                    "rentalPortfolio.totalCollateralValue", new BigDecimal("1000000.00"),
                    "rentalPortfolio.propertyCount", new BigDecimal("4"));
            case REVERSE_MORTGAGE -> Map.of("reverse.indexRate", new BigDecimal("3.00000"),
                    "reverse.margin", new BigDecimal("2.00000"));
            default -> Map.of();
        };
    }
}
