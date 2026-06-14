package com.wcpe.pricing.government;

import com.wcpe.pricing.government.GovernmentPricingApi.FhaMipTable;
import com.wcpe.pricing.government.GovernmentPricingApi.GovernmentPriceRequest;
import com.wcpe.pricing.government.GovernmentPricingApi.GovernmentPriceResponse;
import com.wcpe.pricing.government.GovernmentPricingApi.GovernmentPricingHeaders;
import com.wcpe.pricing.government.GovernmentPricingApi.GovernmentProductCatalog;
import com.wcpe.pricing.government.GovernmentPricingApi.GovernmentProductConfiguration;
import com.wcpe.pricing.government.GovernmentPricingApi.GovernmentProductType;
import com.wcpe.pricing.government.GovernmentPricingApi.LoanLimit;
import com.wcpe.pricing.government.GovernmentPricingApi.UsdaGuaranteeFeeTable;
import com.wcpe.pricing.government.GovernmentPricingApi.UsdaIncomeLimit;
import com.wcpe.pricing.government.GovernmentPricingApi.VaEntitlementConfig;
import com.wcpe.pricing.government.GovernmentPricingApi.VaFundingFeeRule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GovernmentPricingApiTest {
    private final GovernmentPricingApi api = new GovernmentPricingApi();

    @Test
    void fhaPricingComputesConfiguredUpfrontAnnualAndMonthlyMipWithCountyLimitEvidence() {
        GovernmentPriceResponse response = api.price("tenant-a", headers(), request(GovernmentProductType.FHA, fhaConfig()));

        assertTrue(response.blockers().isEmpty());
        assertNotNull(response.selectedOption());
        assertEquals(new BigDecimal("7000.00"), response.selectedOption().lineItems().get(0).amount());
        assertEquals("FHA_ANNUAL_MIP", response.selectedOption().lineItems().get(1).feeType());
        assertEquals(new BigDecimal("220.83"), response.selectedOption().lineItems().get(2).amount());
        assertEquals(new BigDecimal("524225.00"), response.selectedOption().loanLimit().limitAmount());
        assertFalse(response.replayHash().isBlank());
    }

    @Test
    void vaPricingComputesFundingFeeLoanLimitAndEntitlementFromConfiguration() {
        GovernmentPriceResponse response = api.price("tenant-a", headers(), request(GovernmentProductType.VA, vaConfig()));

        assertTrue(response.blockers().isEmpty());
        assertEquals("VA_FUNDING_FEE", response.selectedOption().lineItems().get(0).feeType());
        assertEquals(new BigDecimal("9200.00"), response.selectedOption().lineItems().get(0).amount());
        assertEquals(new BigDecimal("210000.00"), response.selectedOption().availableEntitlement());
    }

    @Test
    void usdaPricingComputesGuaranteeFeesAndBlocksIneligiblePropertyDeterministically() {
        GovernmentPriceResponse response = api.price("tenant-a", headers(),
                new GovernmentPriceRequest(GovernmentProductType.USDA, new BigDecimal("400000.00"), "06037",
                        "CA", new BigDecimal("95000.00"), "not-eligible", false, false,
                        BigDecimal.ZERO, BigDecimal.ZERO, List.of(usdaConfig())));

        assertEquals("USDA_PROPERTY_INELIGIBLE", response.blockers().get(0).code());

        GovernmentPriceResponse eligible = api.price("tenant-a", headers(), request(GovernmentProductType.USDA, usdaConfig()));
        assertTrue(eligible.blockers().isEmpty());
        assertEquals(new BigDecimal("4000.00"), eligible.selectedOption().lineItems().get(0).amount());
        assertEquals(new BigDecimal("1400.00"), eligible.selectedOption().lineItems().get(1).amount());
    }

    @Test
    void missingGovernmentFeeConfigurationBlocksWithoutDefaults() {
        GovernmentPriceResponse response = api.price("tenant-a", headers(),
                new GovernmentPriceRequest(GovernmentProductType.FHA, new BigDecimal("400000.00"), "06037",
                        "CA", BigDecimal.ZERO, "06037", false, false, BigDecimal.ZERO, BigDecimal.ZERO, List.of()));

        assertEquals(GovernmentPricingApi.CONFIG_MISSING_BLOCKER, response.blockers().get(0).code());
        assertEquals(null, response.selectedOption());
    }

    private static GovernmentPriceRequest request(GovernmentProductType type, GovernmentProductConfiguration configuration) {
        return new GovernmentPriceRequest(type, new BigDecimal("400000.00"), "06037", "CA",
                new BigDecimal("95000.00"), "06037", false, true, new BigDecimal("0.0000"),
                new BigDecimal("15000.00"), List.of(configuration));
    }

    private static GovernmentProductConfiguration fhaConfig() {
        return new GovernmentProductConfiguration(catalog(GovernmentProductType.FHA), "gov-fha-v1", true,
                Map.of("06037", new LoanLimit(new BigDecimal("524225.00"), "hud:2026:06037", "limit-v1")),
                new FhaMipTable(new BigDecimal("1.75000000"), new BigDecimal("0.66250000"), "hud:mip:2026", "mip-v1"),
                List.of(), null, null, Map.of(), Set.of());
    }

    private static GovernmentProductConfiguration vaConfig() {
        return new GovernmentProductConfiguration(catalog(GovernmentProductType.VA), "gov-va-v1", true,
                Map.of("06037", new LoanLimit(new BigDecimal("806500.00"), "va:2026:06037", "va-limit-v1")),
                null,
                List.of(new VaFundingFeeRule(false, true, BigDecimal.ZERO, null, new BigDecimal("2.30000000"),
                        1, "va:funding-fee:first-use", "va-fee-v1")),
                new VaEntitlementConfig(new BigDecimal("225000.00"), "va:entitlement:2026", "va-entitlement-v1"),
                null, Map.of(), Set.of());
    }

    private static GovernmentProductConfiguration usdaConfig() {
        return new GovernmentProductConfiguration(catalog(GovernmentProductType.USDA), "gov-usda-v1", true,
                Map.of(), null, List.of(), null,
                new UsdaGuaranteeFeeTable(new BigDecimal("1.00000000"), new BigDecimal("0.35000000"),
                        "usda:guarantee:2026", "usda-fee-v1"),
                Map.of("06037", new UsdaIncomeLimit(new BigDecimal("125000.00"), "usda:income:06037", "usda-income-v1")),
                Set.of("06037"));
    }

    private static GovernmentProductCatalog catalog(GovernmentProductType type) {
        return new GovernmentProductCatalog(type, type.name() + "-30", "GOV-INVESTOR", "RETAIL", 10,
                "catalog:" + type.name());
    }

    private static GovernmentPricingHeaders headers() {
        return new GovernmentPricingHeaders(Set.of(GovernmentPricingApi.GOVERNMENT_PRICE_PERMISSION), "actor-1", "corr-1");
    }
}
