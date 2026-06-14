package com.wcpe.pricing.nonqm.quick;

import com.wcpe.pricing.nonqm.NonQmPricingApi;
import com.wcpe.pricing.nonqm.NonQmPricingApi.EligibilityDecision;
import com.wcpe.pricing.nonqm.NonQmPricingApi.EligibilityStatus;
import com.wcpe.pricing.nonqm.NonQmPricingApi.NonQmMarginPolicy;
import com.wcpe.pricing.nonqm.NonQmPricingApi.NonQmPricingAdjustmentRef;
import com.wcpe.pricing.nonqm.NonQmPricingApi.NonQmProductType;
import com.wcpe.pricing.nonqm.NonQmPricingApi.NonQmRateRow;
import com.wcpe.pricing.nonqm.NonQmPricingApi.NonQmRateSheet;
import com.wcpe.pricing.nonqm.NonQmPricingApi.RateSheetSource;
import com.wcpe.pricing.nonqm.NonQmPricingApi.RateSheetStatus;
import com.wcpe.pricing.nonqm.quick.NonQmQuickPricerApi.InMemoryQuickQuoteRepository;
import com.wcpe.pricing.nonqm.quick.NonQmQuickPricerApi.NonQmQuickQuoteHeaders;
import com.wcpe.pricing.nonqm.quick.NonQmQuickPricerApi.NonQmQuickQuoteRequest;
import com.wcpe.pricing.nonqm.quick.NonQmQuickPricerApi.QuickQuoteResult;
import com.wcpe.pricing.nonqm.quick.NonQmQuickPricerApi.QuickQuoteValidationException;
import com.wcpe.pricing.nonqm.quick.NonQmQuickPricerApi.ScenarioReference;
import com.wcpe.pricing.nonqm.quick.NonQmQuickPricerApi.StaticQuickCandidateProvider;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NonQmQuickPricerApiTest {
    private static final String TENANT = "tenant-a";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-13T19:00:00Z"), ZoneOffset.UTC);

    @Test
    void dscrQuickQuoteUsesConfiguredNonQmPricingCoreAndPersistsPreliminaryResult() {
        InMemoryQuickQuoteRepository repository = new InMemoryQuickQuoteRepository();
        NonQmQuickPricerApi api = api(repository, new StaticQuickCandidateProvider(List.of(dscrSheet())), eligible());

        QuickQuoteResult result = api.quote(TENANT, headers("corr-dscr"), dscrRequest());

        assertEquals("PRELIMINARY", result.status());
        assertEquals("MISS", result.cacheStatus());
        assertTrue(result.preliminary());
        assertTrue(result.latencyBudgetMet());
        assertEquals(1, result.offers().size());
        assertEquals("INV-DSCR-30", result.offers().get(0).productCode());
        assertEquals(new BigDecimal("7.37500"), result.offers().get(0).rate());
        assertEquals(new BigDecimal("99.75000"), result.offers().get(0).price());
        assertTrue(result.assumptions().stream().anyMatch(assumption -> assumption.mustConfirmBeforeLock()
                && "REQUIRED_FOR_FULL_INTAKE".equals(assumption.source())));
        assertTrue(repository.findById(result.quickQuoteId()).isPresent());
    }

    @Test
    void repeatedCommonScenarioUsesCachedPricingLookupButCreatesDistinctQuote() {
        AtomicInteger eligibilityCalls = new AtomicInteger();
        NonQmQuickPricerApi api = api(new InMemoryQuickQuoteRepository(), new StaticQuickCandidateProvider(List.of(dscrSheet())),
                (request, candidate, facts) -> {
                    eligibilityCalls.incrementAndGet();
                    return eligible();
                });

        QuickQuoteResult first = api.quote(TENANT, headers("corr-cache-1"), dscrRequest());
        QuickQuoteResult second = api.quote(TENANT, headers("corr-cache-2"), dscrRequest());

        assertEquals("MISS", first.cacheStatus());
        assertEquals("HIT", second.cacheStatus());
        assertEquals(1, eligibilityCalls.get());
        assertNotNull(second.quickQuoteId());
        assertFalse(first.quickQuoteId().equals(second.quickQuoteId()));
        assertEquals(first.offers().get(0).resultHash(), second.offers().get(0).resultHash());
    }

    @Test
    void hardStopEligibilityReturnsVisibleNoOfferWithoutPricingOffer() {
        NonQmQuickPricerApi api = api(new InMemoryQuickQuoteRepository(), new StaticQuickCandidateProvider(List.of(dscrSheet())),
                new EligibilityDecision(EligibilityStatus.INELIGIBLE, "eligibility:quick:1", "MIN_DSCR_NOT_MET"));

        QuickQuoteResult result = api.quote(TENANT, headers("corr-hard-stop"), dscrRequest());

        assertEquals("NO_OFFER", result.status());
        assertTrue(result.offers().isEmpty());
        assertTrue(result.blockers().stream().anyMatch(blocker -> "NON_QM_NOT_ELIGIBLE".equals(blocker.code())
                && "eligibility:quick:1".equals(blocker.sourceRef())));
    }

    @Test
    void bankStatementAndAssetDepletionPrecomputedScenariosPriceThroughCore() {
        NonQmQuickPricerApi api = api(new InMemoryQuickQuoteRepository(),
                new StaticQuickCandidateProvider(List.of(bankStatementSheet(), assetDepletionSheet())), eligible());

        QuickQuoteResult bank = api.quote(TENANT, headers("corr-bank"), bankStatementRequest());
        QuickQuoteResult asset = api.quote(TENANT, headers("corr-asset"), assetDepletionRequest());

        assertEquals("PRELIMINARY", bank.status());
        assertEquals("INV-BANK-STMT", bank.offers().get(0).productCode());
        assertEquals("PRELIMINARY", asset.status());
        assertEquals("INV-ASSET-DEP", asset.offers().get(0).productCode());
        assertTrue(asset.assumptions().stream().anyMatch(assumption -> assumption.code().startsWith("ASSET_DEPLETION")));
    }

    @Test
    void continueToFullScenarioCreatesDraftWithQuickFactsAndConfirmationTasks() {
        RecordingScenarioClient scenarioClient = new RecordingScenarioClient();
        NonQmQuickPricerApi api = new NonQmQuickPricerApi(new StaticQuickCandidateProvider(List.of(dscrSheet())),
                (request, candidate, facts) -> eligible(), new NonQmQuickPricerApi.RequestSuppliedTierResolver(),
                new InMemoryQuickQuoteRepository(), scenarioClient, CLOCK);
        QuickQuoteResult quote = api.quote(TENANT, headers("corr-continue"), dscrRequest());

        ScenarioReference scenario = api.continueToFullScenario(TENANT, quote.quickQuoteId(), headers("corr-continue-2"));

        assertEquals(quote.quickQuoteId(), scenario.sourceQuickQuoteId());
        assertEquals("scenario-" + quote.quickQuoteId(), scenario.scenarioId());
        assertFalse(scenario.confirmationTasks().isEmpty());
        assertEquals(quote.quickQuoteId(), scenarioClient.lastQuickQuoteId);
    }

    @Test
    void missingMinimalInputsFailBeforeCandidateOrPricingWork() {
        NonQmQuickPricerApi api = api(new InMemoryQuickQuoteRepository(), new StaticQuickCandidateProvider(List.of(dscrSheet())), eligible());
        NonQmQuickQuoteRequest invalid = new NonQmQuickQuoteRequest(TENANT, "BROKER", NonQmProductType.DSCR,
                BigDecimal.ZERO, 720, "TX", Map.of(), Map.of(), marginPolicies());

        assertThrows(QuickQuoteValidationException.class, () -> api.quote(TENANT, headers("corr-invalid"), invalid));
    }

    @Test
    void uiConfigurationExposesQuickPricerFormsAndResultCardFields() {
        NonQmQuickPricerApi api = new NonQmQuickPricerApi();

        var ui = api.uiConfiguration();

        assertEquals("/pricing/non-qm/quick", ui.route());
        assertTrue(ui.forms().stream().anyMatch(form -> "DSCR".equals(form.productType()) && form.pricingEnabled()));
        assertTrue(ui.forms().stream().anyMatch(form -> "BANK_STATEMENT".equals(form.productType()) && form.pricingEnabled()));
        assertTrue(ui.forms().stream().anyMatch(form -> "ASSET_DEPLETION".equals(form.productType()) && form.pricingEnabled()));
        assertTrue(ui.forms().stream().anyMatch(form -> "FIX_FLIP".equals(form.productType()) && !form.pricingEnabled()));
        assertTrue(ui.resultCardFields().contains("continueFullApplication"));
    }

    private static NonQmQuickPricerApi api(InMemoryQuickQuoteRepository repository,
            StaticQuickCandidateProvider candidateProvider, EligibilityDecision eligibilityDecision) {
        return api(repository, candidateProvider, (request, candidate, facts) -> eligibilityDecision);
    }

    private static NonQmQuickPricerApi api(InMemoryQuickQuoteRepository repository,
            StaticQuickCandidateProvider candidateProvider, NonQmQuickPricerApi.QuickEligibilityAdapter eligibilityAdapter) {
        return new NonQmQuickPricerApi(candidateProvider, eligibilityAdapter, new NonQmQuickPricerApi.RequestSuppliedTierResolver(),
                repository, new NonQmQuickPricerApi.InMemoryScenarioDraftClient(), CLOCK);
    }

    private static NonQmQuickQuoteHeaders headers(String correlationId) {
        return new NonQmQuickQuoteHeaders(Set.of(NonQmPricingApi.NON_QM_PRICE_PERMISSION), "actor-1", correlationId);
    }

    private static EligibilityDecision eligible() {
        return new EligibilityDecision(EligibilityStatus.ELIGIBLE, "eligibility:quick:passed", "NON_QM_ELIGIBLE");
    }

    private static NonQmQuickQuoteRequest dscrRequest() {
        return new NonQmQuickQuoteRequest(TENANT, "BROKER", NonQmProductType.DSCR, new BigDecimal("500000"), 724, "TX",
                Map.of("dscrTier", "DSCR_1_00_1_15", "ficoBand", "720_739", "ltvBand", "70_75", "term", "30Y"),
                Map.of("nonQm.dscr.ratio", new BigDecimal("1.08")), marginPolicies());
    }

    private static NonQmQuickQuoteRequest bankStatementRequest() {
        return new NonQmQuickQuoteRequest(TENANT, "BROKER", NonQmProductType.BANK_STATEMENT, new BigDecimal("450000"), 705, "FL",
                Map.of("statementType", "PERSONAL", "statementMonths", "24", "ficoBand", "700_719", "ltvBand", "65_70",
                        "incomeTrend", "DECLINING"),
                Map.of("qualifyingMonthlyIncome", new BigDecimal("18000")), marginPolicies());
    }

    private static NonQmQuickQuoteRequest assetDepletionRequest() {
        return new NonQmQuickQuoteRequest(TENANT, "BROKER", NonQmProductType.ASSET_DEPLETION, new BigDecimal("350000"), 733, "CA",
                Map.of("assetType", "LIQUID", "assetIncomeMethod", "AMORTIZED", "seasoningBand", "12_24", "ficoBand", "720_739",
                        "ltvBand", "60_65"),
                Map.of("verifiedAssets", new BigDecimal("1600000")), marginPolicies());
    }

    private static Map<String, NonQmMarginPolicy> marginPolicies() {
        return Map.of("nonqm-margin:v1", new NonQmMarginPolicy("nonqm-margin:v1",
                new BigDecimal("0.12500"), BigDecimal.ZERO, "NON_QM_QUICK_MARGIN"));
    }

    private static NonQmRateSheet dscrSheet() {
        return new NonQmRateSheet("quick-dscr:v1", "INV-A", "BROKER", NonQmProductType.DSCR, 1,
                LocalDate.parse("2026-06-01"), RateSheetStatus.PUBLISHED,
                List.of(new NonQmRateRow("dscr-row-quick", new BigDecimal("7.25000"), new BigDecimal("99.75000"),
                        Map.of("dscrTier", "DSCR_1_00_1_15", "ficoBand", "720_739", "ltvBand", "70_75", "term", "30Y"),
                        Map.of(), "INV-DSCR-30", "QUICK_DSCR_MATCH")),
                List.of(), "nonqm-margin:v1", RateSheetSource.INTERNAL);
    }

    private static NonQmRateSheet bankStatementSheet() {
        return new NonQmRateSheet("quick-bank:v1", "INV-BANK", "BROKER", NonQmProductType.BANK_STATEMENT, 1,
                LocalDate.parse("2026-06-01"), RateSheetStatus.PUBLISHED,
                List.of(new NonQmRateRow("bank-row-quick", new BigDecimal("8.12500"), new BigDecimal("99.12500"),
                        Map.of("statementType", "PERSONAL", "statementMonths", "24", "ficoBand", "700_719", "ltvBand", "65_70"),
                        Map.of(), "INV-BANK-STMT", "QUICK_BANK_STATEMENT_MATCH")),
                List.of(new NonQmPricingAdjustmentRef("bank-income-trend", "pii33-nonqm-adjustments:v1",
                        "incomeTrend", "DECLINING", new BigDecimal("0.05000"), BigDecimal.ZERO, 10,
                        "BANK_STATEMENT_INCOME_TREND")),
                "nonqm-margin:v1", RateSheetSource.INTERNAL);
    }

    private static NonQmRateSheet assetDepletionSheet() {
        return new NonQmRateSheet("quick-asset:v1", "INV-ASSET", "BROKER", NonQmProductType.ASSET_DEPLETION, 1,
                LocalDate.parse("2026-06-01"), RateSheetStatus.PUBLISHED,
                List.of(new NonQmRateRow("asset-row-quick", new BigDecimal("8.50000"), new BigDecimal("98.87500"),
                        Map.of("assetType", "LIQUID", "assetIncomeMethod", "AMORTIZED", "seasoningBand", "12_24",
                                "ficoBand", "720_739", "ltvBand", "60_65"),
                        Map.of(), "INV-ASSET-DEP", "QUICK_ASSET_DEPLETION_MATCH")),
                List.of(), "nonqm-margin:v1", RateSheetSource.INTERNAL);
    }

    private static final class RecordingScenarioClient implements NonQmQuickPricerApi.ScenarioDraftClient {
        private String lastQuickQuoteId;

        @Override
        public ScenarioReference createFromQuickQuote(QuickQuoteResult result) {
            lastQuickQuoteId = result.quickQuoteId();
            return new ScenarioReference("scenario-" + result.quickQuoteId(), result.quickQuoteId(), result.assumptions());
        }
    }
}
