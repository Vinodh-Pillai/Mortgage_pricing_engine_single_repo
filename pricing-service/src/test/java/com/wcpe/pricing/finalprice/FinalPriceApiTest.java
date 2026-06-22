package com.wcpe.pricing.finalprice;

import com.wcpe.pricing.finalprice.FinalPriceApi.AdjustmentRule;
import com.wcpe.pricing.finalprice.FinalPriceApi.CapFloorAction;
import com.wcpe.pricing.finalprice.FinalPriceApi.CapFloorRule;
import com.wcpe.pricing.finalprice.FinalPriceApi.FinalPriceErrorCode;
import com.wcpe.pricing.finalprice.FinalPriceApi.FinalPriceException;
import com.wcpe.pricing.finalprice.FinalPriceApi.FinalPriceHeaders;
import com.wcpe.pricing.finalprice.FinalPriceApi.FinalPriceRequest;
import com.wcpe.pricing.finalprice.FinalPriceApi.FinalPriceResponse;
import com.wcpe.pricing.finalprice.InMemoryFinalPriceRepository;
import com.wcpe.pricing.finalprice.FinalPriceApi.PricingConfigurationSnapshot;
import com.wcpe.pricing.finalprice.FinalPriceApi.ScenarioFacts;
import com.wcpe.pricing.finalprice.FinalPriceApi.SelectedBaseRate;
import com.wcpe.pricing.government.GovernmentPricingApi.FhaMipTable;
import com.wcpe.pricing.government.GovernmentPricingApi.GovernmentProductCatalog;
import com.wcpe.pricing.government.GovernmentPricingApi.GovernmentProductConfiguration;
import com.wcpe.pricing.government.GovernmentPricingApi.GovernmentProductType;
import com.wcpe.pricing.government.GovernmentPricingApi.LoanLimit;
import com.wcpe.pricing.mi.MiPricingApi.MiPremiumType;
import com.wcpe.pricing.mi.MiPricingApi.MiProgram;
import com.wcpe.pricing.mi.MiPricingApi.MiRateCard;
import com.wcpe.pricing.mi.MiPricingApi.MiRateRow;
import com.wcpe.pricing.rounding.api.RoundingPolicyApi;
import com.wcpe.pricing.rounding.api.RoundingPolicyApi.CreateRoundingPolicyRequest;
import com.wcpe.pricing.rounding.api.InMemoryRoundingPolicyRepository;
import com.wcpe.pricing.rounding.api.RoundingPolicyApi.RoundingHeaders;
import com.wcpe.pricing.rounding.api.RoundingPolicyApi.RoundingPolicyVersion;
import com.wcpe.pricing.rounding.api.RoundingPolicyApi.RoundingRule;
import com.wcpe.pricing.rounding.api.RoundingPolicyApi.RoundingSampleFixture;
import com.wcpe.pricing.rounding.api.RoundingPolicyApi.RoundingUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinalPriceApiTest {
    private static final String TENANT = "tenant-a";
    private static final UUID SELECTION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant AS_OF = Instant.parse("2026-06-01T00:00:00Z");

    private InMemoryFinalPriceRepository repository;
    private RoundingPolicyApi roundingPolicyApi;
    private SelectedBaseRate selection;
    private ScenarioFacts facts;
    private PricingConfigurationSnapshot configuration;
    private FinalPriceApi api;

    @BeforeEach
    void setUp() {
        repository = new InMemoryFinalPriceRepository();
        InMemoryRoundingPolicyRepository roundingRepository = new InMemoryRoundingPolicyRepository();
        roundingPolicyApi = new RoundingPolicyApi(roundingRepository);
        RoundingPolicyVersion roundingPolicy = createValidateAndPublishRoundingPolicy("round-final-price");
        selection = new SelectedBaseRate(TENANT, SELECTION_ID, new BigDecimal("6.12500"),
                new BigDecimal("100.00000"), 30, "grid-version-1", "selection-hash-1");
        facts = new ScenarioFacts(TENANT, "scenario-1", "scenario-hash-1", Map.of("occupancy", "PRIMARY"));
        configuration = new PricingConfigurationSnapshot(
                TENANT,
                List.of("adjustment-version-1", "cap-floor-version-1"),
                null,
                null,
                null,
                "BASE",
                "FINAL_PRICE",
                List.of(
                        new AdjustmentRule("adj-primary", "adjustment-version-1", "occupancy", "PRIMARY", null,
                                new BigDecimal("0.25000000"), 20, "OCCUPANCY_PRIMARY"),
                        new AdjustmentRule("adj-lock", "adjustment-version-1", null, null, 30,
                                new BigDecimal("-0.12500000"), 10, "LOCK_30")),
                List.of(new CapFloorRule("cap-floor-version-1", new BigDecimal("90.00000000"),
                        new BigDecimal("110.00000000"), "WITHIN_CONFIGURED_BOUNDS")));

        api = new FinalPriceApi(
                repository,
                (tenantId, selectionId) -> TENANT.equals(tenantId) && SELECTION_ID.equals(selectionId)
                        ? Optional.of(selection) : Optional.empty(),
                (tenantId, scenarioId, scenarioHash) -> TENANT.equals(tenantId) && facts.scenarioId().equals(scenarioId)
                        && facts.scenarioHash().equals(scenarioHash) ? Optional.of(facts) : Optional.empty(),
                (tenantId, versionRefs, asOf) -> TENANT.equals(tenantId)
                        && versionRefs.stream().anyMatch(configuration.versionRefs()::contains)
                        ? Optional.of(configuration) : Optional.empty(),
                roundingPolicyApi);
    }

    @Test
    void FinalPriceCalculator_appliesConfiguredPrecedence() {
        FinalPriceResponse response = api.calculate(TENANT, writeHeaders("idem-1"), request(false));

        assertEquals(new BigDecimal("100.12500"), response.roundedFinalPrice());
        assertEquals(List.of("adj-lock", "adj-primary"), response.adjustments().stream()
                .map(FinalPriceApi.AdjustmentResult::ruleId)
                .toList());
        assertEquals("pricing.final-price-calculated.v1", repository.events().get(0).eventType());
        assertTrue(response.cacheKey().startsWith("pricing:final-price:tenant-a:scenario-hash-1:"));
    }

    @Test
    void FinalPriceCalculator_usesBigDecimalScaleWithoutDouble() {
        selection = new SelectedBaseRate(TENANT, SELECTION_ID, new BigDecimal("6.12500123"),
                new BigDecimal("100.12345678"), 30, "grid-version-1", "selection-hash-1");
        configuration = new PricingConfigurationSnapshot(TENANT, List.of("adjustment-version-1"), null, null, null,
                "BASE", "FINAL_PRICE", List.of(new AdjustmentRule("adj-small", "adjustment-version-1", null, null,
                null, new BigDecimal("0.00000444"), 1, "SCALE_TEST")), List.of());

        FinalPriceResponse response = api.calculate(TENANT, writeHeaders("idem-scale"), request(false));

        assertEquals(5, response.selectedNoteRate().scale());
        assertEquals(5, response.basePrice().scale());
        assertEquals(8, response.subtotal().scale());
        assertEquals(5, response.roundedFinalPrice().scale());
        assertFalse(response.ledger().stream().anyMatch(entry -> String.valueOf(entry.outputValue()).contains("E")));
    }

    @Test
    void FinalPriceCalculator_failsOnMissingRequiredFact() {
        facts = new ScenarioFacts(TENANT, "scenario-1", "different-hash", Map.of());

        FinalPriceException exception = assertThrows(FinalPriceException.class,
                () -> api.calculate(TENANT, writeHeaders("idem-missing-facts"), request(false)));

        assertEquals(FinalPriceErrorCode.SCENARIO_FACT_MISSING, exception.code());
    }

    @Test
    void FinalPriceResultRepository_persistsLedgerAtomically() {
        FinalPriceResponse response = api.calculate(TENANT, writeHeaders("idem-persist"), request(false));

        FinalPriceResponse stored = repository.findById(response.finalPriceId()).orElseThrow().response();
        assertEquals(response.resultHash(), stored.resultHash());
        assertEquals(response.ledger(), stored.ledger());
        assertEquals(1, repository.events().size());
        assertEquals(1, repository.audits().size());
        assertEquals("FINAL_PRICE_CALCULATION_COMPLETED", repository.audits().get(0).action());
    }

    @Test
    void FinalPriceCache_invalidatesOnAdjustmentPublish() {
        FinalPriceResponse original = api.calculate(TENANT, writeHeaders("idem-cache-1"), request(false));
        configuration = new PricingConfigurationSnapshot(TENANT, List.of("adjustment-version-2"), null, null, null,
                "BASE", "FINAL_PRICE", List.of(new AdjustmentRule("adj-primary-v2", "adjustment-version-2",
                "occupancy", "PRIMARY", null, new BigDecimal("0.37500000"), 20, "OCCUPANCY_PRIMARY_V2")),
                List.of());

        FinalPriceResponse republished = api.calculate(TENANT, writeHeaders("idem-cache-2"), new FinalPriceRequest(
                SELECTION_ID, "scenario-1", "scenario-hash-1", List.of("adjustment-version-2"), AS_OF, "quote-1", false));

        assertNotEquals(original.versionGraph().hash(), republished.versionGraph().hash());
        assertNotEquals(original.cacheKey(), republished.cacheKey());
        assertEquals(new BigDecimal("100.37500"), republished.roundedFinalPrice());
    }

    @Test
    void FinalPriceTenantIsolationTest() {
        FinalPriceException exception = assertThrows(FinalPriceException.class,
                () -> api.calculate("tenant-b", writeHeaders("idem-tenant"), request(false)));

        assertEquals(FinalPriceErrorCode.BASE_RATE_SELECTION_REQUIRED, exception.code());
    }

    @Test
    void finalPriceRejectsIdempotencyConflict() {
        api.calculate(TENANT, writeHeaders("idem-conflict"), request(false));

        FinalPriceException exception = assertThrows(FinalPriceException.class,
                () -> api.calculate(TENANT, writeHeaders("idem-conflict"), new FinalPriceRequest(
                        SELECTION_ID, "scenario-1", "different-hash", List.of("adjustment-version-1"), AS_OF,
                        "quote-1", false)));

        assertEquals(FinalPriceErrorCode.IDEMPOTENCY_CONFLICT, exception.code());
    }

    @Test
    void finalPriceBlocksConfiguredCapFloorViolation() {
        configuration = new PricingConfigurationSnapshot(TENANT, List.of("adjustment-version-1"), null, null, null,
                "BASE", "FINAL_PRICE", List.of(new AdjustmentRule("adj-too-high", "adjustment-version-1", null,
                null, null, new BigDecimal("10.25000000"), 1, "TOO_HIGH")),
                List.of(new CapFloorRule("cap-floor-version-1", null, new BigDecimal("101.00000000"),
                        "CONFIGURED_MAX_PRICE_EXCEEDED")));

        FinalPriceException exception = assertThrows(FinalPriceException.class,
                () -> api.calculate(TENANT, writeHeaders("idem-cap"), request(false)));

        assertEquals(FinalPriceErrorCode.CAP_FLOOR_BLOCKED, exception.code());
    }

    @Test
    void PriceCapFloorEvaluator_appliesConfiguredAdjustAction() {
        configuration = new PricingConfigurationSnapshot(TENANT, List.of("adjustment-version-1", "cap-floor-version-adjust"), null, null, null,
                "BASE", "FINAL_PRICE", List.of(),
                List.of(new CapFloorRule("cap-floor-version-adjust", null, new BigDecimal("99.50000000"),
                        CapFloorAction.ADJUST, 10, "CONFIGURED_MAX_PRICE_ADJUSTED")), true);

        FinalPriceResponse response = api.calculate(TENANT, writeHeaders("idem-cap-adjust"), request(false));

        assertEquals(new BigDecimal("99.50000"), response.roundedFinalPrice());
        assertEquals(CapFloorAction.ADJUST, response.capFloorResults().get(0).action());
        assertTrue(response.capFloorResults().get(0).adjusted());
        assertTrue(response.ledger().stream().anyMatch(entry -> "CAP_FLOOR_CHECK".equals(entry.step())
                && "ADJUST".equals(entry.operation())));
    }

    @Test
    void PriceBoundaryPolicyValidator_rejectsConflictingRules() {
        FinalPriceException exception = assertThrows(FinalPriceException.class,
                () -> new CapFloorRule("cap-floor-conflict", new BigDecimal("101.00000000"),
                        new BigDecimal("100.00000000"), CapFloorAction.BLOCK, 1, "CONFLICTING_BOUNDS"));

        assertEquals(FinalPriceErrorCode.PRICE_BOUNDARY_CONFLICT, exception.code());
    }

    @Test
    void PriceBoundaryPolicyMissing_failsClosedWhenRequired() {
        configuration = new PricingConfigurationSnapshot(TENANT, List.of("adjustment-version-1", "cap-floor-version-required"), null, null,
                null, "BASE", "FINAL_PRICE", List.of(), List.of(), true);

        FinalPriceException exception = assertThrows(FinalPriceException.class,
                () -> api.calculate(TENANT, writeHeaders("idem-cap-missing"), request(false)));

        assertEquals(FinalPriceErrorCode.PRICE_BOUNDARY_POLICY_MISSING, exception.code());
    }

    @Test
    void adjustmentLinesToLedger() {
        FinalPriceApi realAdjustmentApi = new FinalPriceApi(
                repository,
                (tenantId, selectionId) -> TENANT.equals(tenantId) && SELECTION_ID.equals(selectionId)
                        ? Optional.of(selection) : Optional.empty(),
                (tenantId, scenarioId, scenarioHash) -> TENANT.equals(tenantId) && facts.scenarioId().equals(scenarioId)
                        && facts.scenarioHash().equals(scenarioHash) ? Optional.of(facts) : Optional.empty(),
                (tenantId, versionRefs, asOf) -> TENANT.equals(tenantId)
                        && versionRefs.stream().anyMatch(configuration.versionRefs()::contains)
                        ? Optional.of(configuration) : Optional.empty(),
                roundingPolicyApi,
                request -> new FinalPriceApi.AdjustmentCalculationResult(
                        request.basePriceDecision().scenarioId(),
                        request.basePriceDecision().basePriceId(),
                        List.of(
                                new FinalPriceApi.AdjustmentLine("LLPA-FICO", 0.25000000, "FICO_720_739", "adjustment-service",
                                        "POINTS_DELTA", "rule-fico", "rulebook-v1", true, "FICO 720-739", "audit:rule-fico", List.of("representativeFico=725")),
                                new FinalPriceApi.AdjustmentLine("LLPA-CASHOUT", -0.12500000, "CASH_OUT_REFI", "adjustment-service",
                                        "POINTS_DELTA", "rule-cashout", "rulebook-v1", true, "Cash-out refi", "audit:rule-cashout", List.of("cashOutFlag=true"))),
                        0.12500000,
                        "rulebook-v1",
                        "real-rule-book",
                        List.of("audit:rule-fico", "audit:rule-cashout"),
                        "adjustment-hash-1",
                        Map.of("POINTS_DELTA", new BigDecimal("0.12500000")),
                        false));

        FinalPriceResponse response = realAdjustmentApi.calculate(TENANT, writeHeaders("idem-real-adjustments"), request(false));

        List<FinalPriceApi.FinalPriceLedgerEntry> adjustmentSteps = response.ledger().stream()
                .filter(entry -> "ADJUSTMENT".equals(entry.step()))
                .toList();
        assertEquals(2, adjustmentSteps.size());
        assertEquals(new BigDecimal("100.00000000"), adjustmentSteps.get(0).inputValue());
        assertEquals(new BigDecimal("100.25000000"), adjustmentSteps.get(0).outputValue());
        assertEquals("FICO_720_739", adjustmentSteps.get(0).reasonCode());
        assertEquals("SUB", adjustmentSteps.get(1).operation());
        assertEquals(new BigDecimal("100.12500000"), adjustmentSteps.get(1).outputValue());
        assertEquals(List.of("representativeFico=725"), response.adjustments().get(0).conditions());
    }

    @Test
    void mortgageInsuranceFactorParticipatesInFinalPriceAndLedger() {
        facts = new ScenarioFacts(TENANT, "scenario-1", "scenario-hash-1", Map.of(
                "occupancy", "PRIMARY",
                "loanType", "CONVENTIONAL",
                "ltv", "90.00",
                "fico", "740",
                "loanAmount", "400000.00",
                "miCoveragePercent", "25"));
        configuration = new PricingConfigurationSnapshot(
                TENANT,
                List.of("adjustment-version-1", "mi-version-radian"),
                null,
                null,
                null,
                "BASE",
                "FINAL_PRICE",
                List.of(),
                List.of(),
                List.of(new MiRateCard("card-radian", "Radian", "mi-version-radian", List.of(new MiRateRow(
                        "row-lpmi", new BigDecimal("85.01"), new BigDecimal("95.00"), 700, 759,
                        new BigDecimal("1.00"), new BigDecimal("726200.00"), 25, MiPremiumType.LPMI,
                        null, null, new BigDecimal("0.37500000"), "radian:lpmi:row-1")))),
                List.of(new MiProgram("Radian", MiPremiumType.LPMI)),
                true);

        FinalPriceResponse response = api.calculate(TENANT, writeHeaders("idem-mi-lpmi"), request(false));

        assertEquals(new BigDecimal("100.37500"), response.roundedFinalPrice());
        assertEquals(1, response.mortgageInsurance().size());
        assertEquals("Radian", response.mortgageInsurance().get(0).carrier());
        assertEquals(new BigDecimal("0.37500000"), response.mortgageInsurance().get(0).priceAdjustment());
        assertTrue(response.ledger().stream().anyMatch(entry -> "MORTGAGE_INSURANCE".equals(entry.step())
                && "MI_Radian_LPMI".equals(entry.reasonCode())));
        assertTrue(response.versionGraph().refs().contains("mi-version-radian"));
    }

    @Test
    void governmentProductPricingParticipatesInFinalPriceLedgerAndVersionGraph() {
        facts = new ScenarioFacts(TENANT, "scenario-1", "scenario-hash-1", Map.of(
                "occupancy", "PRIMARY",
                "loanType", "FHA",
                "loanAmount", "400000.00",
                "countyFips", "06037",
                "state", "CA"));
        configuration = new PricingConfigurationSnapshot(
                TENANT,
                List.of("adjustment-version-1", "gov-fha-v1"),
                null,
                null,
                null,
                "BASE",
                "FINAL_PRICE",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(fhaGovernmentConfig()),
                false);

        FinalPriceResponse response = api.calculate(TENANT, writeHeaders("idem-government-fha"), request(false));

        assertEquals(1, response.governmentPricing().size());
        assertEquals(GovernmentProductType.FHA, response.governmentPricing().get(0).catalog().productType());
        assertTrue(response.ledger().stream().anyMatch(entry -> "GOVERNMENT_FEE".equals(entry.step())
                && "FHA_UPFRONT_MIP".equals(entry.reasonCode())));
        assertTrue(response.versionGraph().refs().contains("gov-fha-v1"));
    }

    @Test
    void governmentPricingFailsClosedWhenConfigurationMissing() {
        facts = new ScenarioFacts(TENANT, "scenario-1", "scenario-hash-1", Map.of(
                "occupancy", "PRIMARY",
                "loanType", "FHA",
                "loanAmount", "400000.00",
                "countyFips", "06037",
                "state", "CA"));
        configuration = new PricingConfigurationSnapshot(TENANT, List.of("adjustment-version-1"), null, null,
                null, "BASE", "FINAL_PRICE", List.of(), List.of());

        FinalPriceException exception = assertThrows(FinalPriceException.class,
                () -> api.calculate(TENANT, writeHeaders("idem-government-missing"), request(false)));

        assertEquals(FinalPriceErrorCode.GOVERNMENT_CONFIG_MISSING, exception.code());
    }

    @Test
    void PriceCapFloorEvaluator_warnActionFailsClosedUntilConfigured() {
        configuration = new PricingConfigurationSnapshot(TENANT, List.of("adjustment-version-1", "cap-floor-version-warn"), null, null, null,
                "BASE", "FINAL_PRICE", List.of(),
                List.of(new CapFloorRule("cap-floor-version-warn", null, new BigDecimal("99.50000000"),
                        CapFloorAction.WARN, 1, "WARN_REQUIRES_PRODUCT_COMPLIANCE_APPROVAL")), true);

        FinalPriceException exception = assertThrows(FinalPriceException.class,
                () -> api.calculate(TENANT, writeHeaders("idem-cap-warn"), request(false)));

        assertEquals(FinalPriceErrorCode.PRICE_BOUNDARY_POLICY_NOT_SATISFIED, exception.code());
    }

    private FinalPriceRequest request(boolean dryRun) {
        return new FinalPriceRequest(SELECTION_ID, "scenario-1", "scenario-hash-1",
                List.of("adjustment-version-1"), AS_OF, "quote-1", dryRun);
    }

    private static FinalPriceHeaders writeHeaders(String idempotencyKey) {
        return new FinalPriceHeaders(Set.of(FinalPriceApi.FINAL_PRICE_WRITE_PERMISSION), "actor-1", "corr-1", idempotencyKey);
    }

    private static GovernmentProductConfiguration fhaGovernmentConfig() {
        return new GovernmentProductConfiguration(
                new GovernmentProductCatalog(GovernmentProductType.FHA, "FHA-30", "GOV-INVESTOR", "RETAIL", 10,
                        "catalog:fha"),
                "gov-fha-v1",
                true,
                Map.of("06037", new LoanLimit(new BigDecimal("524225.00"), "hud:2026:06037", "fha-limit-v1")),
                new FhaMipTable(new BigDecimal("1.75000000"), new BigDecimal("0.66250000"),
                        "hud:mip:2026", "fha-mip-v1"),
                List.of(),
                null,
                null,
                Map.of(),
                Set.of());
    }

    private RoundingPolicyVersion createValidateAndPublishRoundingPolicy(String ruleId) {
        CreateRoundingPolicyRequest request = new CreateRoundingPolicyRequest(
                TENANT,
                "BASE",
                null,
                null,
                null,
                LocalDate.parse("2026-01-01"),
                LocalDate.parse("2027-01-01"),
                1,
                List.of(new RoundingRule(ruleId, "FINAL_PRICE", RoundingUnit.PRICE, 5, RoundingMode.HALF_UP,
                        new BigDecimal("0.12500"), 0, "ROUND_FINAL_PRICE")),
                List.of(new RoundingSampleFixture("final-price-rounding", "FINAL_PRICE",
                        new BigDecimal("100.12500"), new BigDecimal("100.12500"))));
        RoundingPolicyVersion policy = roundingPolicyApi.createDraft(TENANT, writeRoundingHeaders(), request);
        roundingPolicyApi.validatePolicy(TENANT, policy.id(), writeRoundingHeaders());
        return roundingPolicyApi.publish(TENANT, policy.id(), publishRoundingHeaders());
    }

    private static RoundingHeaders writeRoundingHeaders() {
        return new RoundingHeaders(Set.of(RoundingPolicyApi.ROUNDING_WRITE_PERMISSION), "creator-1", "corr-round", "idem-round");
    }

    private static RoundingHeaders publishRoundingHeaders() {
        return new RoundingHeaders(Set.of(RoundingPolicyApi.ROUNDING_APPROVE_PERMISSION,
                RoundingPolicyApi.ROUNDING_PUBLISH_PERMISSION), "approver-1", "corr-publish", "idem-publish");
    }
}
