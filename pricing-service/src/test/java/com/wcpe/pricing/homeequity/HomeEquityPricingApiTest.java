package com.wcpe.pricing.homeequity;

import com.wcpe.pricing.homeequity.HomeEquityPricingApi.HomeEquityAdjustmentCondition;
import com.wcpe.pricing.homeequity.HomeEquityPricingApi.HomeEquityAdjustmentRule;
import com.wcpe.pricing.homeequity.HomeEquityPricingApi.HomeEquityConditionType;
import com.wcpe.pricing.homeequity.HomeEquityPricingApi.HomeEquityIndexCode;
import com.wcpe.pricing.homeequity.HomeEquityPricingApi.HomeEquityPriceRequest;
import com.wcpe.pricing.homeequity.HomeEquityPricingApi.HomeEquityPriceResponse;
import com.wcpe.pricing.homeequity.HomeEquityPricingApi.HomeEquityPricingConfiguration;
import com.wcpe.pricing.homeequity.HomeEquityPricingApi.HomeEquityPricingHeaders;
import com.wcpe.pricing.homeequity.HomeEquityPricingApi.HomeEquityProductType;
import com.wcpe.pricing.homeequity.HomeEquityPricingApi.IndexRateConfig;
import com.wcpe.pricing.homeequity.HomeEquityPricingApi.LienPosition;
import com.wcpe.pricing.homeequity.HomeEquityPricingApi.PpeHomeEquityMappingResult;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HomeEquityPricingApiTest {
    private static final String TENANT = "tenant-a";
    private static final Instant AS_OF = Instant.parse("2026-06-01T00:00:00Z");

    private final HomeEquityPricingApi api = new HomeEquityPricingApi();

    @Test
    void helocPricingUsesConfiguredIndexMarginDrawAndRepaymentAssumptions() {
        HomeEquityPriceResponse response = api.price(TENANT, headers(), request(config()));

        assertEquals("PRICED", response.status());
        assertEquals(HomeEquityIndexCode.PRIME, response.indexCode());
        assertEquals(new BigDecimal("8.50000"), response.indexRate());
        assertEquals(125, response.baseMarginBps());
        assertEquals(200, response.totalMarginBps());
        assertEquals(new BigDecimal("10.50000"), response.fullyIndexedRate());
        assertEquals(new BigDecimal("10.50000"), response.initialRate());
        assertEquals(new BigDecimal("525.00"), response.drawPeriodInterestOnlyPayment());
        assertEquals(new BigDecimal("663.24"), response.repaymentPeriodAmortizedPayment());
        assertTrue(response.waterfall().stream().anyMatch(line -> "INDEX_RATE".equals(line.step())
                && line.configRef().contains("prime-feed:v1")));
        assertTrue(response.waterfall().stream().anyMatch(line -> "DRAW_PAYMENT".equals(line.step())
                && "INTEREST_ONLY_DRAW_PERIOD".equals(line.reasonCode())));
        assertTrue(response.versionRefs().contains("home-equity-config:v1"));
        assertTrue(response.versionRefs().contains("home-equity-adjustments:v1"));
        assertTrue(response.blockers().isEmpty());
    }

    @Test
    void missingIndexConfigurationBlocksWithConfigEvidence() {
        HomeEquityPricingConfiguration configuration = new HomeEquityPricingConfiguration(
                "home-equity-config:v1", true, 125,
                java.util.List.of(new IndexRateConfig(HomeEquityIndexCode.SOFR, new BigDecimal("5.25000"),
                        "sofr-feed:v1", "rate-index:v1")),
                new BigDecimal("4.00000"), new BigDecimal("18.00000"), new BigDecimal("2.00000"),
                new BigDecimal("6.00000"), new BigDecimal("85.00000"), java.util.List.of());

        HomeEquityPriceResponse response = api.price(TENANT, headers(), request(configuration));

        assertEquals("BLOCKED", response.status());
        assertTrue(response.blockers().stream().anyMatch(blocker -> "INDEX_CONFIGURATION_MISSING".equals(blocker.code())
                && blocker.configRef().contains("indexRates")));
    }

    @Test
    void configuredCltvLimitBlocksWithoutInventedThresholds() {
        HomeEquityPriceRequest highCltv = new HomeEquityPriceRequest(TENANT, "scenario-heloc-1",
                HomeEquityProductType.HELOC, HomeEquityIndexCode.PRIME, LienPosition.SECOND,
                new BigDecimal("150000.00"), new BigDecimal("60000.00"), new BigDecimal("500000.00"),
                new BigDecimal("350000.00"), 720, "SINGLE_FAMILY", 120, 180, AS_OF, config());

        HomeEquityPriceResponse response = api.price(TENANT, headers(), highCltv);

        assertEquals("BLOCKED", response.status());
        assertTrue(response.blockers().stream().anyMatch(blocker -> "CLTV_LIMIT_EXCEEDED".equals(blocker.code())
                && blocker.configRef().contains("maxCombinedLoanToValue")
                && blocker.remediationHint().contains("configured_max_cltv=85.00")));
    }

    @Test
    void floorCeilingAnnualAndLifetimeCapsAreAuditable() {
        HomeEquityPricingConfiguration capped = new HomeEquityPricingConfiguration(
                "home-equity-config:v2", true, 125,
                java.util.List.of(new IndexRateConfig(HomeEquityIndexCode.PRIME, new BigDecimal("19.00000"),
                        "prime-feed:v2", "rate-index:v2")),
                new BigDecimal("4.00000"), new BigDecimal("18.00000"), new BigDecimal("2.00000"),
                new BigDecimal("6.00000"), new BigDecimal("85.00000"), java.util.List.of());

        HomeEquityPriceResponse response = api.price(TENANT, headers(), request(capped));

        assertEquals(new BigDecimal("20.25000"), response.fullyIndexedRate());
        assertEquals(new BigDecimal("18.00000"), response.initialRate());
        assertEquals(new BigDecimal("18.00000"), response.maximumAnnualAdjustmentRate());
        assertEquals(new BigDecimal("18.00000"), response.maximumLifetimeRate());
        assertTrue(response.waterfall().stream().anyMatch(line -> "RATE_BOUNDARY".equals(line.step())
                && "CEILING_RATE_APPLIED".equals(line.reasonCode())));
    }

    @Test
    void ppeExportMapsSupportedHomeEquityFieldsAndReportsUnmapped() {
        PpeHomeEquityMappingResult mapping = api.validatePpeExport(Map.of(
                "productType", "HELOC",
                "indexCode", "PRIME",
                "marginBps", "125",
                "drawPeriodMonths", "120",
                "vendorOnlyField", "not-canonical"));

        assertEquals("HELOC", mapping.canonicalFields().get("homeEquity.productType"));
        assertEquals("PRIME", mapping.canonicalFields().get("homeEquity.indexCode"));
        assertEquals("125", mapping.canonicalFields().get("homeEquity.baseMarginBps"));
        assertEquals(java.util.List.of("vendorOnlyField"), mapping.unmappedFields());
    }

    @Test
    void controllerExposesTenantHomeEquityPriceEndpoint() {
        HomeEquityPricingController controller = new HomeEquityPricingController(api);

        ResponseEntity<HomeEquityPriceResponse> response = controller.price(TENANT,
                HomeEquityPricingApi.HOME_EQUITY_PRICE_PERMISSION, "actor-1", "corr-controller", request(config()));

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("PRICED", response.getBody().status());
    }

    private static HomeEquityPricingHeaders headers() {
        return new HomeEquityPricingHeaders(Set.of(HomeEquityPricingApi.HOME_EQUITY_PRICE_PERMISSION), "actor-1", "corr-heloc-1");
    }

    private static HomeEquityPriceRequest request(HomeEquityPricingConfiguration configuration) {
        return new HomeEquityPriceRequest(TENANT, "scenario-heloc-1", HomeEquityProductType.HELOC,
                HomeEquityIndexCode.PRIME, LienPosition.SECOND, new BigDecimal("100000.00"),
                new BigDecimal("60000.00"), new BigDecimal("500000.00"), new BigDecimal("300000.00"),
                720, "CONDO", 120, 180, AS_OF, configuration);
    }

    private static HomeEquityPricingConfiguration config() {
        return new HomeEquityPricingConfiguration(
                "home-equity-config:v1",
                true,
                125,
                java.util.List.of(new IndexRateConfig(HomeEquityIndexCode.PRIME, new BigDecimal("8.50000"),
                        "prime-feed:v1", "rate-index:v1")),
                new BigDecimal("4.00000"),
                new BigDecimal("18.00000"),
                new BigDecimal("2.00000"),
                new BigDecimal("6.00000"),
                new BigDecimal("85.00000"),
                java.util.List.of(
                        new HomeEquityAdjustmentRule("heloc-cltv", "home-equity-adjustments:v1",
                                new HomeEquityAdjustmentCondition(HomeEquityConditionType.CLTV_GREATER_THAN,
                                        new BigDecimal("75.00000"), null, null), 50, 10, "CLTV_GT_75"),
                        new HomeEquityAdjustmentRule("heloc-condo", "home-equity-adjustments:v1",
                                new HomeEquityAdjustmentCondition(HomeEquityConditionType.PROPERTY_TYPE_EQUALS,
                                        null, null, "CONDO"), 25, 20, "PROPERTY_TYPE_CONDO")));
    }
}
