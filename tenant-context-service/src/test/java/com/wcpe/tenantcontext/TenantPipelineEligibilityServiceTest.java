package com.wcpe.tenantcontext;

import static org.assertj.core.api.Assertions.*;

import com.wcpe.tenantcontext.TenantFieldConfigurationStoreService.FieldOrigin;
import com.wcpe.tenantcontext.TenantFieldConfigurationStoreService.TenantFieldConfiguration;
import com.wcpe.tenantcontext.TenantPipelineEligibilityService.FieldReference;
import com.wcpe.tenantcontext.TenantPipelineEligibilityService.TenantInvestorOption;
import com.wcpe.tenantcontext.TenantPipelineEligibilityService.TenantPipelineConfiguration;
import com.wcpe.tenantcontext.TenantPipelineEligibilityService.TenantPipelineEligibilityRequest;
import com.wcpe.tenantcontext.TenantPipelineEligibilityService.TenantPipelineException;
import com.wcpe.tenantcontext.TenantPipelineEligibilityService.TenantProductOption;
import com.wcpe.tenantcontext.TenantPipelineEligibilityService.TenantUserSettings;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TenantPipelineEligibilityServiceTest {
    private static final Instant NOW = Instant.parse("2026-06-18T16:00:00Z");

    @Test
    void returnsOnlyTenantAuthorizedProductsInvestorsAndTenantSettingsForPipelineDisplay() {
        TenantPipelineEligibilityService service = serviceWithActiveFields();
        service.configureTenant(configuration(
            "tenant-alpha",
            List.of(product("alpha-product", "loan-type", true), product("disabled-alpha-product", "loan-type", false)),
            List.of(investor("alpha-investor", "investor-code", true), investor("disabled-alpha-investor", "investor-code", false)),
            Map.of("company.roundingMode", "nearest-cent"),
            List.of(new TenantUserSettings("user-alpha", "tenant-alpha", Map.of("pipeline.defaultView", "summary")))
        ));
        service.configureTenant(configuration(
            "tenant-beta",
            List.of(product("beta-product", "loan-type", true)),
            List.of(investor("beta-investor", "investor-code", true)),
            Map.of("company.roundingMode", "whole-dollar"),
            List.of(new TenantUserSettings("user-beta", "tenant-beta", Map.of("pipeline.defaultView", "detail")))
        ));

        var eligibility = service.eligibleForUser(context("tenant-alpha"), new TenantPipelineEligibilityRequest("tenant-alpha", "user-alpha", "tenant-alpha", null, null));

        assertThat(eligibility.productIds()).containsExactly("alpha-product");
        assertThat(eligibility.investorIds()).containsExactly("alpha-investor");
        assertThat(eligibility.companySettings()).containsEntry("company.roundingMode", "nearest-cent");
        assertThat(eligibility.userSettings()).containsEntry("pipeline.defaultView", "summary");
        assertThat(eligibility.productIds()).doesNotContain("beta-product", "disabled-alpha-product");
        assertThat(eligibility.investorIds()).doesNotContain("beta-investor", "disabled-alpha-investor");
    }

    @Test
    void deniesUserFromQueryingAnotherTenantProductOrInvestorId() {
        TenantPipelineEligibilityService service = serviceWithActiveFields();
        service.configureTenant(configuration(
            "tenant-alpha",
            List.of(product("alpha-product", "loan-type", true)),
            List.of(investor("alpha-investor", "investor-code", true)),
            Map.of(),
            List.of(new TenantUserSettings("user-alpha", "tenant-alpha", Map.of()))
        ));
        service.configureTenant(configuration(
            "tenant-beta",
            List.of(product("beta-product", "loan-type", true)),
            List.of(investor("beta-investor", "investor-code", true)),
            Map.of(),
            List.of(new TenantUserSettings("user-beta", "tenant-beta", Map.of()))
        ));

        assertThatThrownBy(() -> service.eligibleForUser(context("tenant-beta"), new TenantPipelineEligibilityRequest("tenant-beta", "user-alpha", "tenant-beta", "beta-product", "beta-investor")))
            .isInstanceOf(TenantPipelineException.class)
            .extracting(Throwable::getMessage)
            .isEqualTo("TENANT_PIPELINE_ACCESS_DENIED");

        assertThatThrownBy(() -> service.eligibleForUser(context("tenant-alpha"), new TenantPipelineEligibilityRequest("tenant-alpha", "user-alpha", "tenant-alpha", "beta-product", null)))
            .isInstanceOf(TenantPipelineException.class)
            .extracting(Throwable::getMessage)
            .isEqualTo("TENANT_PIPELINE_PRODUCT_DENIED");

        assertThat(service.accessAuditRecordsForTenant("tenant-beta"))
            .singleElement()
            .satisfies(record -> {
                assertThat(record.code()).isEqualTo("TENANT_PIPELINE_ACCESS_DENIED");
                assertThat(record.entityType()).isEqualTo("user");
                assertThat(record.entityId()).isEqualTo("user-alpha");
            });
        assertThat(service.accessAuditRecordsForTenant("tenant-alpha"))
            .singleElement()
            .satisfies(record -> {
                assertThat(record.code()).isEqualTo("TENANT_PIPELINE_PRODUCT_DENIED");
                assertThat(record.entityType()).isEqualTo("product");
                assertThat(record.entityId()).isEqualTo("beta-product");
            });
    }

    @Test
    void recordsSuccessfulPipelineMetadataEvaluationsOnlyForTheRequestTenant() {
        TenantPipelineEligibilityService service = serviceWithActiveFields();
        service.configureTenant(configuration(
            "tenant-alpha",
            List.of(product("alpha-product", "loan-type", true)),
            List.of(investor("alpha-investor", "investor-code", true)),
            Map.of("pipeline.defaultView", "summary"),
            List.of(new TenantUserSettings("user-alpha", "tenant-alpha", Map.of("pipeline.columns", "compact")))
        ));
        service.configureTenant(configuration(
            "tenant-beta",
            List.of(product("beta-product", "loan-type", true)),
            List.of(investor("beta-investor", "investor-code", true)),
            Map.of("pipeline.defaultView", "detail"),
            List.of(new TenantUserSettings("user-beta", "tenant-beta", Map.of("pipeline.columns", "expanded")))
        ));

        service.eligibleForUser(context("tenant-alpha"), new TenantPipelineEligibilityRequest("tenant-alpha", "user-alpha", "tenant-alpha", "alpha-product", "alpha-investor"));

        assertThat(service.accessAuditRecordsForTenant("tenant-alpha"))
            .singleElement()
            .satisfies(record -> {
                assertThat(record.code()).isEqualTo("TENANT_PIPELINE_METADATA_EVALUATED");
                assertThat(record.entityType()).isEqualTo("tenant");
                assertThat(record.entityId()).isEqualTo("tenant-alpha");
                assertThat(record.userId()).isEqualTo("user-alpha");
                assertThat(record.actorId()).isEqualTo("actor-tenant-alpha");
            });
        assertThat(service.accessAuditRecordsForTenant("tenant-beta")).isEmpty();
    }

    @Test
    void rejectsPipelineMetadataQueriesWhenTenantContextIsMissing() {
        TenantPipelineEligibilityService service = serviceWithActiveFields();

        assertThatThrownBy(() -> service.eligibleForUser(new TenantPipelineEligibilityRequest("tenant-alpha", "user-alpha", "tenant-alpha", null, null)))
            .isInstanceOf(TenantContextValidationException.class)
            .extracting(error -> ((TenantContextValidationException) error).code())
            .isEqualTo("TENANT_CONTEXT_MISSING");
    }

    @Test
    void rejectsAndAuditsTenantContextThatDoesNotMatchRequestedMetadataTenant() {
        TenantPipelineEligibilityService service = serviceWithActiveFields();

        assertThatThrownBy(() -> service.eligibleForUser(context("tenant-alpha"), new TenantPipelineEligibilityRequest("tenant-beta", "user-beta", "tenant-beta", null, null)))
            .isInstanceOf(TenantContextValidationException.class)
            .extracting(error -> ((TenantContextValidationException) error).code())
            .isEqualTo("TENANT_ACCESS_DENIED");

        assertThat(service.accessAuditRecordsForTenant("tenant-beta"))
            .singleElement()
            .satisfies(record -> {
                assertThat(record.code()).isEqualTo("TENANT_ACCESS_DENIED");
                assertThat(record.actorId()).isEqualTo("actor-tenant-alpha");
                assertThat(record.entityType()).isEqualTo("tenant");
                assertThat(record.entityId()).isEqualTo("tenant-beta");
            });
    }

    @Test
    void validatesProductAndInvestorFieldReferencesAgainstTenantActiveFieldLibrary() {
        TenantPipelineEligibilityService service = serviceWithActiveFields();

        assertThatThrownBy(() -> service.configureTenant(configuration(
            "tenant-alpha",
            List.of(product("alpha-product", "missing-field", true)),
            List.of(investor("alpha-investor", "investor-code", true)),
            Map.of(),
            List.of(new TenantUserSettings("user-alpha", "tenant-alpha", Map.of()))
        )))
            .isInstanceOf(TenantPipelineException.class)
            .satisfies(error -> assertThat(((TenantPipelineException) error).code()).isEqualTo("TENANT_PIPELINE_FIELD_REFERENCE_MISSING"));
    }

    private static TenantPipelineEligibilityService serviceWithActiveFields() {
        TenantFieldConfigurationStoreService fieldStore = new TenantFieldConfigurationStoreService(Clock.fixed(NOW, ZoneOffset.UTC));
        fieldStore.save(systemField("tenant-alpha", "PRODUCT_SPEC", "loan-type"));
        fieldStore.save(systemField("tenant-alpha", "PRODUCT_SPEC", "investor-code"));
        fieldStore.save(systemField("tenant-beta", "PRODUCT_SPEC", "loan-type"));
        fieldStore.save(systemField("tenant-beta", "PRODUCT_SPEC", "investor-code"));
        return new TenantPipelineEligibilityService(fieldStore);
    }

    private static TenantPipelineConfiguration configuration(
        String tenantId,
        List<TenantProductOption> products,
        List<TenantInvestorOption> investors,
        Map<String, String> companySettings,
        List<TenantUserSettings> userSettings
    ) {
        return new TenantPipelineConfiguration(tenantId, products, investors, companySettings, userSettings);
    }

    private static TenantProductOption product(String id, String fieldId, boolean enabled) {
        return new TenantProductOption(id, id + " label", List.of(new FieldReference("PRODUCT_SPEC", fieldId)), enabled);
    }

    private static TenantInvestorOption investor(String id, String fieldId, boolean enabled) {
        return new TenantInvestorOption(id, id + " label", List.of(new FieldReference("PRODUCT_SPEC", fieldId)), enabled);
    }

    private static TenantFieldConfiguration systemField(String tenantId, String surface, String fieldId) {
        return new TenantFieldConfiguration(null, tenantId, surface, fieldId, FieldOrigin.INHERITED_SYSTEM, "system-field:" + fieldId, fieldId, "", true, false, null, null);
    }

    private static TenantContext context(String tenantId) {
        return new TenantContext(
            tenantId,
            new RequestContext("request-" + tenantId, "trace-" + tenantId, "correlation-" + tenantId, "cause-" + tenantId, "idem-" + tenantId, "pipeline-metadata"),
            new ActorRef("actor-" + tenantId, "USER"),
            List.of("pricing-analyst"),
            List.of(TenantAccessPolicy.DEFAULT_CONTEXT_READ_SCOPE),
            "pipeline-metadata"
        );
    }
}
