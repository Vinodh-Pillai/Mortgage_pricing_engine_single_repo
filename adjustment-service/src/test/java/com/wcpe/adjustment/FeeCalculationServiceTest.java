package com.wcpe.adjustment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wcpe.adjustment.FeeCalculationService.FeeCalculationConfiguration;
import com.wcpe.adjustment.FeeCalculationService.CalculationOutputSnapshot;
import com.wcpe.adjustment.FeeCalculationService.ConfigurationBlockerException;
import com.wcpe.adjustment.FeeCalculationService.FeeCalculationRequest;
import com.wcpe.adjustment.FeeCalculationService.FeeCalculationResult;
import com.wcpe.adjustment.FeeCalculationService.ManualFeeInput;
import com.wcpe.adjustment.FeeCalculationService.PassThroughFeeSource;
import com.wcpe.adjustment.FeeCatalogVersion.CalculationMethod;
import com.wcpe.adjustment.FeeCatalogVersion.CatalogStatus;
import com.wcpe.adjustment.FeeCatalogVersion.EffectiveWindow;
import com.wcpe.adjustment.FeeCatalogVersion.FeeApplicability;
import com.wcpe.adjustment.FeeCatalogVersion.FeeCatalogRequestContext;
import com.wcpe.adjustment.FeeCatalogVersion.FeeDefinition;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FeeCalculationServiceTest {
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000067");
    private static final UUID CATALOG_ID = UUID.fromString("20000000-0000-0000-0000-000000000067");
    private static final UUID CALCULATION_ID = UUID.fromString("30000000-0000-0000-0000-000000000067");
    private static final Instant QUOTE_DATE = Instant.parse("2026-04-01T00:00:00Z");

    private final FeeCalculationService service = new FeeCalculationService();

    @Test
    void calculatesConfiguredMethodsWithBigDecimalPrecisionRoundedLedgerAndReplayMetadata() {
        FeeCatalogVersion catalog = publishedCatalog(List.of(
            fee("APPRAISAL", CalculationMethod.FIXED_AMOUNT, Map.of("amountConfigRef", "amount-appraisal"), "BORROWER"),
            fee("INVESTOR_BPS", CalculationMethod.BPS_OF_LOAN_AMOUNT, Map.of("bpsConfigRef", "bps-investor"), "BORROWER"),
            fee("PROCESSING", CalculationMethod.PERCENT_OF_LOAN_AMOUNT, Map.of("percentConfigRef", "percent-processing"), "BORROWER"),
            fee("THIRD_PARTY_UNIT", CalculationMethod.PER_UNIT, Map.of("unitAmountConfigRef", "unit-third-party"), "THIRD_PARTY"),
            fee("WAIVED_ADMIN", CalculationMethod.WAIVED, Map.of("waiverPolicyRef", "waiver-admin"), "LENDER")
        ));
        FeeCalculationConfiguration config = new FeeCalculationConfiguration(Map.of(
            "amount-appraisal", new BigDecimal("100.005"),
            "bps-investor", new BigDecimal("25"),
            "percent-processing", new BigDecimal("1.25"),
            "unit-third-party", new BigDecimal("10.00")
        ), Map.of());

        FeeCalculationResult result = service.calculate(request(catalog, config));
        FeeCalculationResult replay = service.calculate(request(catalog, config));

        assertThat(result.feeLines()).extracting("feeCode")
            .containsExactly("APPRAISAL", "INVESTOR_BPS", "PROCESSING", "THIRD_PARTY_UNIT", "WAIVED_ADMIN");
        assertThat(result.feeLines().get(0).rawAmount()).isEqualByComparingTo("100.005000");
        assertThat(result.feeLines().get(0).roundedAmount()).isEqualByComparingTo("100.01");
        assertThat(result.totalBorrowerPaid()).isEqualByComparingTo("1600.01");
        assertThat(result.totalLenderPaid()).isEqualByComparingTo("0.00");
        assertThat(result.totalThirdParty()).isEqualByComparingTo("20.00");
        assertThat(result.inputSnapshotHash()).hasSize(64).isEqualTo(replay.inputSnapshotHash());
        assertThat(result.event().topic()).isEqualTo("pricing.quote.fees.v1");
        assertThat(result.event().eventType()).isEqualTo("QuoteFeesCalculated.v1");
        assertThat(result.audit().action()).isEqualTo("FEE_CALCULATION_COMPLETED");
        assertThat(result.audit().resultHash()).hasSize(64).isEqualTo(replay.audit().resultHash());
    }

    @Test
    void consumesCalculationEngineOutputsAndTracesInputFieldsVersionsAndOutputAmounts() {
        FeeCatalogVersion catalog = publishedCatalog(List.of(
            fee("CALC_ENGINE_FEE", CalculationMethod.FIXED_AMOUNT, Map.of(
                "amountCalculationOutputRef", "calc@fee.amount"
            ), "BORROWER")
        ));
        CalculationOutputSnapshot outputs = new CalculationOutputSnapshot(
            "runtime-eval-PII-74-S01",
            List.of("calc-version-PII-71-S04"),
            Map.of("calc@fee.amount", new BigDecimal("42.125")),
            Map.of("loanAmount", "100000.00", "ltv", "72.50")
        );

        FeeCalculationResult result = service.calculate(request(catalog, FeeCalculationConfiguration.empty(), outputs));

        assertThat(result.feeLines()).hasSize(1);
        assertThat(result.feeLines().get(0).roundedAmount()).isEqualByComparingTo("42.13");
        assertThat(result.feeLines().get(0).formulaInputs())
            .containsEntry("amountCalculationOutputRef", "calc@fee.amount")
            .containsEntry("calculationVersionIds", "calc-version-PII-71-S04")
            .containsEntry("calculationEvaluationId", "runtime-eval-PII-74-S01")
            .containsEntry("inputFieldRefs", "loanAmount,ltv");
        assertThat(result.audit().calculationVersionIds()).containsExactly("calc-version-PII-71-S04");
        assertThat(result.audit().inputFieldRefs()).containsExactly("loanAmount", "ltv");
        assertThat(result.audit().outputAmountSummary()).containsEntry("CALC_ENGINE_FEE", "42.13");
    }

    @Test
    void appliesCapFloorAfterRawFormulaAndTotalsUseRoundedFeeLines() {
        FeeCatalogVersion catalog = publishedCatalog(List.of(
            fee("CAPPED", CalculationMethod.PERCENT_OF_LOAN_AMOUNT, Map.of(
                "percentConfigRef", "percent-ref",
                "capAmountConfigRef", "cap-ref"
            ), "BORROWER"),
            fee("FLOORED", CalculationMethod.FIXED_AMOUNT, Map.of(
                "amountConfigRef", "small-amount-ref",
                "floorAmountConfigRef", "floor-ref"
            ), "BORROWER")
        ));
        FeeCalculationConfiguration config = new FeeCalculationConfiguration(Map.of(
            "percent-ref", new BigDecimal("10"),
            "cap-ref", new BigDecimal("50.004"),
            "small-amount-ref", new BigDecimal("1.004"),
            "floor-ref", new BigDecimal("5.005")
        ), Map.of());

        FeeCalculationResult result = service.calculate(request(catalog, config));

        assertThat(result.feeLines().get(0).capFloorResult().applied()).isEqualTo("CAP");
        assertThat(result.feeLines().get(0).roundedAmount()).isEqualByComparingTo("50.00");
        assertThat(result.feeLines().get(1).capFloorResult().applied()).isEqualTo("FLOOR");
        assertThat(result.feeLines().get(1).roundedAmount()).isEqualByComparingTo("5.01");
        assertThat(result.totalBorrowerPaid()).isEqualByComparingTo("55.01");
    }

    @Test
    void manualFeeInputRequiresConfiguredPermissionAuditReasonAndCreditAllowedConfig() {
        FeeCatalogVersion catalog = publishedCatalog(List.of(
            fee("MANUAL_CREDIT", CalculationMethod.MANUAL_INPUT_ALLOWED, Map.of(
                "manualInputPermissionRef", "fee.manual.override",
                "auditReasonRequired", "true",
                "creditAllowedConfigRef", "credit-allowed-ref"
            ), "BORROWER")
        ));
        FeeCalculationConfiguration noCreditConfig = new FeeCalculationConfiguration(Map.of(), Map.of());

        assertThatThrownBy(() -> service.calculate(request(catalog, noCreditConfig, Map.of(),
            Map.of("MANUAL_CREDIT", new ManualFeeInput(new BigDecimal("-12.34"), "approved credit")), Set.of())))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("requires permission");

        assertThatThrownBy(() -> service.calculate(request(catalog, noCreditConfig, Map.of(),
            Map.of("MANUAL_CREDIT", new ManualFeeInput(new BigDecimal("-12.34"), "")), Set.of("fee.manual.override"))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("requires audit reason");

        assertThatThrownBy(() -> service.calculate(request(catalog, noCreditConfig, Map.of(),
            Map.of("MANUAL_CREDIT", new ManualFeeInput(new BigDecimal("-12.34"), "approved credit")), Set.of("fee.manual.override"))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("negative fee requires explicit creditAllowed config");

        FeeCalculationConfiguration creditAllowed = new FeeCalculationConfiguration(Map.of(), Map.of("credit-allowed-ref", true));
        FeeCalculationResult result = service.calculate(request(catalog, creditAllowed, Map.of(),
            Map.of("MANUAL_CREDIT", new ManualFeeInput(new BigDecimal("-12.34"), "approved credit")), Set.of("fee.manual.override")));

        assertThat(result.feeLines()).hasSize(1);
        assertThat(result.feeLines().get(0).roundedAmount()).isEqualByComparingTo("-12.34");
    }

    @Test
    void passThroughFeeRequiresTrustedSourceAndConfiguredBounds() {
        FeeCatalogVersion catalog = publishedCatalog(List.of(
            fee("PASS_THROUGH_TITLE", CalculationMethod.PASS_THROUGH, Map.of(
                "trustedSourceRef", "trusted-title-source",
                "minimumAmountConfigRef", "title-min",
                "maximumAmountConfigRef", "title-max"
            ), "THIRD_PARTY")
        ));
        FeeCalculationConfiguration config = new FeeCalculationConfiguration(Map.of(
            "title-min", new BigDecimal("10.00"),
            "title-max", new BigDecimal("500.00")
        ), Map.of());

        assertThatThrownBy(() -> service.calculate(request(catalog, config, Map.of(
            "PASS_THROUGH_TITLE", new PassThroughFeeSource(new BigDecimal("25.00"), "untrusted-source")
        ), Map.of(), Set.of())))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("source is not trusted");

        assertThatThrownBy(() -> service.calculate(request(catalog, config, Map.of(
            "PASS_THROUGH_TITLE", new PassThroughFeeSource(new BigDecimal("501.00"), "trusted-title-source")
        ), Map.of(), Set.of())))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("above configured bounds");

        FeeCalculationResult result = service.calculate(request(catalog, config, Map.of(
            "PASS_THROUGH_TITLE", new PassThroughFeeSource(new BigDecimal("125.456"), "trusted-title-source")
        ), Map.of(), Set.of()));

        assertThat(result.totalThirdParty()).isEqualByComparingTo("125.46");
        assertThat(result.feeLines().get(0).formulaInputs()).containsEntry("trustedSourceRef", "trusted-title-source");
    }

    @Test
    void failsClosedForMissingConfigurationAndNonPublishedCatalogs() {
        FeeCatalogVersion draft = new FeeCatalogVersion(
            TENANT_ID,
            CATALOG_ID,
            1,
            CatalogStatus.DRAFT,
            new EffectiveWindow(Instant.parse("2026-01-01T00:00:00Z"), null),
            List.of(fee("APPRAISAL", CalculationMethod.FIXED_AMOUNT, Map.of("amountConfigRef", "amount-appraisal"), "BORROWER")),
            "requester-1",
            null,
            null,
            null
        );

        assertThatThrownBy(() -> service.calculate(request(draft, FeeCalculationConfiguration.empty())))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("requires a published fee catalog");

        FeeCatalogVersion published = draft.publish("approver-1", Instant.parse("2026-01-02T00:00:00Z"), List.of());
        assertThatThrownBy(() -> service.calculate(request(published, FeeCalculationConfiguration.empty())))
            .isInstanceOf(ConfigurationBlockerException.class)
            .hasMessageContaining("missing configured numeric value");
    }

    private FeeCalculationRequest request(FeeCatalogVersion catalog, FeeCalculationConfiguration configuration) {
        return request(catalog, configuration, Map.of(), Map.of(), Set.of());
    }

    private FeeCalculationRequest request(
        FeeCatalogVersion catalog,
        FeeCalculationConfiguration configuration,
        CalculationOutputSnapshot outputs
    ) {
        return new FeeCalculationRequest(
            TENANT_ID,
            CALCULATION_ID,
            "quote-PII-74-S01",
            "scenario-PII-74-S01",
            "actor-1",
            catalog,
            context(),
            new BigDecimal("100000.00"),
            2,
            configuration,
            Map.of(),
            Map.of(),
            Set.of(),
            outputs,
            null,
            Instant.parse("2026-04-01T00:00:01Z"),
            "correlation-PII-74-S01",
            "idem-PII-74-S01"
        );
    }

    private FeeCalculationRequest request(
        FeeCatalogVersion catalog,
        FeeCalculationConfiguration configuration,
        Map<String, PassThroughFeeSource> passThroughFees,
        Map<String, ManualFeeInput> manualInputs,
        Set<String> permissions
    ) {
        return new FeeCalculationRequest(
            TENANT_ID,
            CALCULATION_ID,
            "quote-PII-06-S07",
            "scenario-PII-06-S07",
            "actor-1",
            catalog,
            context(),
            new BigDecimal("100000.00"),
            2,
            configuration,
            passThroughFees,
            manualInputs,
            permissions,
            null,
            Instant.parse("2026-04-01T00:00:01Z"),
            "correlation-PII-06-S07",
            "idem-PII-06-S07"
        );
    }

    private static FeeCatalogVersion publishedCatalog(List<FeeDefinition> definitions) {
        return new FeeCatalogVersion(
            TENANT_ID,
            CATALOG_ID,
            1,
            CatalogStatus.DRAFT,
            new EffectiveWindow(Instant.parse("2026-01-01T00:00:00Z"), null),
            definitions,
            "requester-1",
            null,
            null,
            null
        ).publish("approver-1", Instant.parse("2026-01-02T00:00:00Z"), List.of());
    }

    private static FeeCatalogRequestContext context() {
        return new FeeCatalogRequestContext(
            TENANT_ID,
            "configured-product",
            "configured-investor",
            "configured-channel",
            "configured-jurisdiction",
            QUOTE_DATE
        );
    }

    private static FeeDefinition fee(
        String feeCode,
        CalculationMethod method,
        Map<String, String> formulaParameters,
        String payer
    ) {
        return new FeeDefinition(
            UUID.nameUUIDFromBytes(feeCode.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
            feeCode,
            "Configured " + feeCode,
            "Synthetic definition with externally supplied fee values",
            "CONFIGURED_CATEGORY",
            payer,
            "THIRD_PARTY".equals(payer) ? "THIRD_PARTY_PAYEE" : "LENDER".equals(payer) ? "LENDER" : "SETTLEMENT_AGENT",
            true,
            true,
            "CONFIGURED_TOLERANCE_BUCKET",
            "CONFIGURED_DISCLOSURE_SECTION",
            method,
            formulaParameters,
            new FeeApplicability(
                List.of("configured-product"),
                List.of("configured-investor"),
                List.of("configured-channel"),
                List.of("configured-jurisdiction")
            ),
            "CONFIGURED_REASON",
            "source-policy-ref",
            true,
            null
        );
    }
}
