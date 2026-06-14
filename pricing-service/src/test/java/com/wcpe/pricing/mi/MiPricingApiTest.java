package com.wcpe.pricing.mi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.wcpe.pricing.mi.MiPricingApi.MiImportValidationResult;
import com.wcpe.pricing.mi.MiPricingApi.MiPremiumType;
import com.wcpe.pricing.mi.MiPricingApi.MiPriceRequest;
import com.wcpe.pricing.mi.MiPricingApi.MiPriceResponse;
import com.wcpe.pricing.mi.MiPricingApi.MiPricingHeaders;
import com.wcpe.pricing.mi.MiPricingApi.MiProgram;
import com.wcpe.pricing.mi.MiPricingApi.MiRateCard;
import com.wcpe.pricing.mi.MiPricingApi.MiRateCardMetadata;
import com.wcpe.pricing.mi.MiPricingApi.MiRateRow;

class MiPricingApiTest {
    private final MiPricingApi api = new MiPricingApi();

    @Test
    void miEngineCalculatesBpmiAndLpmiFromConfiguredCarrierRateCards() {
        MiPriceResponse response = api.price("tenant-a", headers(MiPricingApi.MI_PRICE_PERMISSION), request());

        assertTrue(response.blockers().isEmpty());
        assertNotNull(response.selectedOption());
        assertEquals("MGIC", response.selectedOption().carrier());
        assertEquals(MiPremiumType.BPMI_MONTHLY, response.selectedOption().premiumType());
        assertEquals(new BigDecimal("153.33"), response.selectedOption().monthlyPremium());
        assertEquals(new BigDecimal("0.00000000"), response.selectedOption().priceAdjustment());
        assertEquals(1, response.selectedOption().rank());
        assertEquals(2, response.rankedOptions().size());
        assertNotNull(response.replayHash());
    }

    @Test
    void miEngineReturnsDeterministicBlockerForIneligibleCombination() {
        MiPriceRequest unmatched = new MiPriceRequest("CONVENTIONAL", new BigDecimal("97.00"), 620,
                new BigDecimal("400000.00"), 35, List.of(new MiProgram("Essent", MiPremiumType.BPMI_MONTHLY)),
                List.of(rateCard("Essent", MiPremiumType.BPMI_MONTHLY, "0.46", null, null)));

        MiPriceResponse response = api.price("tenant-a", headers(MiPricingApi.MI_PRICE_PERMISSION), unmatched);

        assertEquals(1, response.blockers().size());
        assertEquals("MI_RATE_ROW_NOT_FOUND", response.blockers().get(0).code());
        assertTrue(response.rankedOptions().isEmpty());
    }

    @Test
    void rateCardMetadataReturnsVersionOnlyWithoutCredentials() {
        MiRateCardMetadata metadata = api.rateCardMetadata("tenant-a", headers(MiPricingApi.MI_RATE_CARD_READ_PERMISSION),
                rateCard("National MI", MiPremiumType.BPMI_MONTHLY, "0.46", null, null));

        assertEquals("National MI", metadata.carrier());
        assertEquals("mi-version-NATIONAL_MI", metadata.versionRef());
        assertEquals(1, metadata.rowCount());
        assertNotNull(metadata.metadataHash());
    }

    @Test
    void importedPpeExportsMapSupportedFieldsAndReportUnsupportedFields() {
        MiImportValidationResult result = api.validateImportedRateRows("Optimal Blue", List.of(Map.of(
                "MI Company", "Radian",
                "Coverage %", "25",
                "Monthly Rate", "0.49",
                "Portal Credential", "not-imported")));

        assertEquals("OPTIMAL_BLUE", result.sourceType());
        assertEquals("Radian", result.canonicalRows().get(0).get("carrier"));
        assertEquals("25", result.canonicalRows().get(0).get("coverage_percent"));
        assertEquals("0.49", result.canonicalRows().get(0).get("annual_rate_percent"));
        assertEquals(List.of("Portal Credential"), result.unsupportedFields());
        assertNotNull(result.validationHash());
    }

    @Test
    void supportedCarrierCatalogIncludesMajorMiCompaniesWithoutHardcodedRates() {
        assertEquals(List.of("MGIC", "RADIAN", "ESSENT", "NATIONAL_MI", "GENWORTH"), MiPricingApi.supportedCarrierNames());
    }

    private static MiPriceRequest request() {
        return new MiPriceRequest("CONVENTIONAL", new BigDecimal("90.00"), 740, new BigDecimal("400000.00"), 25,
                List.of(new MiProgram("MGIC", MiPremiumType.BPMI_MONTHLY), new MiProgram("Radian", MiPremiumType.LPMI)),
                List.of(
                        rateCard("MGIC", MiPremiumType.BPMI_MONTHLY, "0.46", null, null),
                        rateCard("Radian", MiPremiumType.LPMI, null, null, "2.00000000")));
    }

    private static MiRateCard rateCard(String carrier, MiPremiumType premiumType, String annualRatePercent,
            String upfrontRatePercent, String lenderPaidPriceAdjustment) {
        String safeCarrier = carrier.toUpperCase().replace(' ', '_');
        return new MiRateCard("card-" + safeCarrier, carrier, "mi-version-" + safeCarrier, List.of(new MiRateRow(
                "row-1", new BigDecimal("85.01"), new BigDecimal("95.00"), 700, 759,
                new BigDecimal("1.00"), new BigDecimal("726200.00"), 25, premiumType,
                annualRatePercent == null ? null : new BigDecimal(annualRatePercent),
                upfrontRatePercent == null ? null : new BigDecimal(upfrontRatePercent),
                lenderPaidPriceAdjustment == null ? null : new BigDecimal(lenderPaidPriceAdjustment),
                "source:" + safeCarrier + ":row-1")));
    }

    private static MiPricingHeaders headers(String permission) {
        return new MiPricingHeaders(Set.of(permission), "actor-1", "corr-1");
    }
}
