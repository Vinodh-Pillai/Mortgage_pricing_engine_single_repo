package com.wcpe.tenantcontext;

import static org.assertj.core.api.Assertions.*;

import com.wcpe.tenantcontext.TenantFieldConfigurationStoreService.FieldOrigin;
import com.wcpe.tenantcontext.TenantFieldConfigurationStoreService.TenantFieldConfigException;
import com.wcpe.tenantcontext.TenantFieldConfigurationStoreService.TenantFieldConfiguration;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TenantFieldConfigurationStoreServiceTest {
    private static final Instant NOW = Instant.parse("2026-06-18T16:00:00Z");
    private static final String TENANT_ALPHA = "11111111-1111-1111-1111-111111111111";
    private static final String TENANT_BETA = "22222222-2222-2222-2222-222222222222";

    @Test
    void storesNativeTenantFieldsWithoutCrossTenantReads() {
        TenantFieldConfigurationStoreService service = service();

        service.save(nativeField("tenant-alpha", "client-settings", "tenant-custom-1", "Alpha custom field"));

        assertThat(service.activeForTenantSurface("tenant-alpha", "CLIENT_SETTINGS"))
            .extracting(TenantFieldConfiguration::fieldId)
            .containsExactly("tenant-custom-1");
        assertThat(service.activeForTenantSurface("tenant-beta", "CLIENT_SETTINGS")).isEmpty();
        assertThat(service.activeField("tenant-beta", "CLIENT_SETTINGS", "tenant-custom-1")).isEmpty();
    }

    @Test
    void appliesSystemFieldAliasesOnlyForTheOwningTenant() {
        TenantFieldConfigurationStoreService service = service();
        service.save(systemField("tenant-alpha", "APPLICATION_FORM", "borrower-name", "Borrower Legal Name", "Alpha-only description", true, false));
        service.save(systemField("tenant-beta", "APPLICATION_FORM", "borrower-name", "Applicant", "Beta-only description", true, false));

        TenantFieldConfiguration alpha = service.activeField("tenant-alpha", "APPLICATION_FORM", "borrower-name").orElseThrow();
        TenantFieldConfiguration beta = service.activeField("tenant-beta", "APPLICATION_FORM", "borrower-name").orElseThrow();

        assertThat(alpha.nameAlias()).isEqualTo("Borrower Legal Name");
        assertThat(alpha.descriptionAlias()).isEqualTo("Alpha-only description");
        assertThat(beta.nameAlias()).isEqualTo("Applicant");
        assertThat(beta.descriptionAlias()).isEqualTo("Beta-only description");
    }

    @Test
    void omitsDisabledTenantFieldsWithoutChangingAnotherTenantConfiguration() {
        TenantFieldConfigurationStoreService service = service();
        service.save(systemField("tenant-alpha", "PIPELINE_SETTINGS", "pipeline-stage", "Pipeline Stage", "Tenant alpha hidden", true, true));
        service.save(systemField("tenant-beta", "PIPELINE_SETTINGS", "pipeline-stage", "Pipeline Stage", "Tenant beta visible", true, false));

        assertThat(service.activeForTenantSurface("tenant-alpha", "PIPELINE_SETTINGS")).isEmpty();
        assertThat(service.storedForTenantSurface("tenant-alpha", "PIPELINE_SETTINGS"))
            .singleElement()
            .satisfies(field -> assertThat(field.omitted()).isTrue());
        assertThat(service.activeForTenantSurface("tenant-beta", "PIPELINE_SETTINGS"))
            .extracting(TenantFieldConfiguration::fieldId)
            .containsExactly("pipeline-stage");
    }

    @Test
    void resolvesSameFieldIdByTenantAndRejectsInvalidPolicyFreeInputs() {
        TenantFieldConfigurationStoreService service = service();
        service.save(systemField("tenant-alpha", "PRODUCT_SPEC", "shared-field", "Alpha Product Label", "Alpha copy", true, false));
        service.save(systemField("tenant-beta", "PRODUCT_SPEC", "shared-field", "Beta Product Label", "Beta copy", true, false));

        assertThat(service.activeField("tenant-alpha", "PRODUCT_SPEC", "shared-field").orElseThrow().nameAlias()).isEqualTo("Alpha Product Label");
        assertThat(service.activeField("tenant-beta", "PRODUCT_SPEC", "shared-field").orElseThrow().nameAlias()).isEqualTo("Beta Product Label");
        assertThatThrownBy(() -> service.save(new TenantFieldConfiguration(null, "tenant-alpha", "PRODUCT_SPEC", "bad-native", FieldOrigin.NATIVE, "system:bad", "", "", true, false, null, null)))
            .isInstanceOf(TenantFieldConfigException.class)
            .extracting(Throwable::getMessage)
            .isEqualTo("TENANT_FIELD_NATIVE_SYSTEM_REF_FORBIDDEN");
    }

    @Test
    void replacesOnlyTheRequestedTenantSurface() {
        TenantFieldConfigurationStoreService service = service();
        service.save(nativeField("tenant-alpha", "CLIENT_SETTINGS", "old-client-field", "Old client field"));
        service.save(nativeField("tenant-alpha", "NOTIFICATION", "notice-field", "Notice field"));

        List<TenantFieldConfiguration> replacement = service.replaceTenantSurface("tenant-alpha", "CLIENT_SETTINGS", List.of(
            nativeField("tenant-alpha", "CLIENT_SETTINGS", "new-client-field", "New client field")
        ));

        assertThat(replacement).extracting(TenantFieldConfiguration::fieldId).containsExactly("new-client-field");
        assertThat(service.activeForTenantSurface("tenant-alpha", "CLIENT_SETTINGS")).extracting(TenantFieldConfiguration::fieldId).containsExactly("new-client-field");
        assertThat(service.activeForTenantSurface("tenant-alpha", "NOTIFICATION")).extracting(TenantFieldConfiguration::fieldId).containsExactly("notice-field");
    }

    @Test
    void controllerUsesPathTenantAsTheTenantContext() {
        TenantFieldConfigurationStoreService service = service();
        TenantFieldConfigurationStoreController controller = new TenantFieldConfigurationStoreController(service, new TenantContextService());

        controller.save(TENANT_ALPHA, contextHeaders(TENANT_ALPHA), nativeField(TENANT_BETA, "CLIENT_SETTINGS", "path-owned-field", "Path owned field"));

        assertThat(controller.activeField(TENANT_ALPHA, "CLIENT_SETTINGS", "path-owned-field", contextHeaders(TENANT_ALPHA)).getBody())
            .extracting(TenantFieldConfiguration::tenantId)
            .isEqualTo(TENANT_ALPHA);
        assertThat(controller.activeField(TENANT_BETA, "CLIENT_SETTINGS", "path-owned-field", contextHeaders(TENANT_BETA)).getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void controllerRejectsTenantDataAccessWhenRequestContextIsMissing() {
        TenantFieldConfigurationStoreController controller = new TenantFieldConfigurationStoreController(service(), new TenantContextService());

        assertThatThrownBy(() -> controller.activeForTenantSurface("tenant-alpha", "CLIENT_SETTINGS", Map.of()))
            .isInstanceOf(TenantContextValidationException.class)
            .extracting(error -> ((TenantContextValidationException) error).code())
            .isEqualTo("TENANT_CONTEXT_MISSING");
    }

    @Test
    void controllerRejectsCrossTenantContextBeforeReturningFieldData() {
        TenantFieldConfigurationStoreService service = service();
        TenantFieldConfigurationStoreController controller = new TenantFieldConfigurationStoreController(service, new TenantContextService());
        controller.save(TENANT_ALPHA, contextHeaders(TENANT_ALPHA), nativeField(TENANT_ALPHA, "CLIENT_SETTINGS", "alpha-field", "Alpha field"));

        assertThatThrownBy(() -> controller.activeForTenantSurface(TENANT_ALPHA, "CLIENT_SETTINGS", contextHeaders(TENANT_BETA)))
            .isInstanceOf(TenantContextValidationException.class)
            .extracting(error -> ((TenantContextValidationException) error).code())
            .isEqualTo("TENANT_ACCESS_DENIED");
    }

    @Test
    void overloadedDraftSaveWritesOneTenantAlignedDraftWithConditions() {
        TenantFieldConfigurationStoreService service = service();

        service.saveDraft(TENANT_ALPHA, "APPLICATION_FORM", List.of(
            systemField(TENANT_BETA, "APPLICATION_FORM", "borrower-name", "Borrower", "Borrower field", true, false),
            systemField(TENANT_BETA, "APPLICATION_FORM", "income", "Income", "Income field", true, false)
        ), Map.of("income", List.of("borrower-name")), "admin-alpha");

        assertThat(service.draftForTenantSurface(TENANT_ALPHA, "APPLICATION_FORM"))
            .get()
            .satisfies(draft -> {
                assertThat(draft.tenantId()).isEqualTo(TENANT_ALPHA);
                assertThat(draft.configurations()).extracting(TenantFieldConfiguration::tenantId).containsOnly(TENANT_ALPHA);
                assertThat(draft.conditionFieldRefs()).containsEntry("income", List.of("borrower-name"));
            });
        assertThat(service.draftForTenantSurface(TENANT_BETA, "APPLICATION_FORM")).isEmpty();
    }

    @Test
    void controllerRefreshesByTenantContextWithoutReusingPreviousTenantFields() {
        TenantFieldConfigurationStoreService service = service();
        TenantFieldConfigurationStoreController controller = new TenantFieldConfigurationStoreController(service, new TenantContextService());
        controller.save("tenant-alpha", contextHeaders("tenant-alpha"), nativeField("tenant-alpha", "PIPELINE_SETTINGS", "stage-alias", "Alpha stage"));
        controller.save("tenant-beta", contextHeaders("tenant-beta"), nativeField("tenant-beta", "PIPELINE_SETTINGS", "stage-alias", "Beta stage"));

        assertThat(controller.activeForTenantSurface("tenant-alpha", "PIPELINE_SETTINGS", contextHeaders("tenant-alpha")))
            .extracting(TenantFieldConfiguration::nameAlias)
            .containsExactly("Alpha stage");
        assertThat(controller.activeForTenantSurface("tenant-beta", "PIPELINE_SETTINGS", contextHeaders("tenant-beta")))
            .extracting(TenantFieldConfiguration::nameAlias)
            .containsExactly("Beta stage");
    }

    @Test
    void draftPublishAndRollbackKeepRuntimePublishedVersionIsolatedByTenant() {
        TenantFieldConfigurationStoreService service = service();
        service.save(nativeField("tenant-alpha", "CLIENT_SETTINGS", "published-field", "Published field"));
        service.save(nativeField("tenant-beta", "CLIENT_SETTINGS", "beta-field", "Beta field"));

        service.saveDraft("tenant-alpha", "CLIENT_SETTINGS", List.of(
            nativeField("tenant-alpha", "CLIENT_SETTINGS", "draft-field", "Draft field")
        ), "admin-alpha");

        assertThat(service.activeForTenantSurface("tenant-alpha", "CLIENT_SETTINGS"))
            .extracting(TenantFieldConfiguration::fieldId)
            .containsExactly("published-field");

        service.publishDraft("tenant-alpha", "CLIENT_SETTINGS", "admin-alpha");
        assertThat(service.activeForTenantSurface("tenant-alpha", "CLIENT_SETTINGS"))
            .extracting(TenantFieldConfiguration::fieldId)
            .containsExactly("draft-field");
        assertThat(service.publishedVersions("tenant-alpha", "CLIENT_SETTINGS"))
            .singleElement()
            .satisfies(version -> assertThat(version.previousConfigurations()).extracting(TenantFieldConfiguration::fieldId).containsExactly("published-field"));

        service.saveDraft("tenant-alpha", "CLIENT_SETTINGS", List.of(
            nativeField("tenant-alpha", "CLIENT_SETTINGS", "newer-field", "Newer field")
        ), "admin-alpha");
        service.publishDraft("tenant-alpha", "CLIENT_SETTINGS", "admin-alpha");

        service.rollbackToPreviousVersion("tenant-alpha", "CLIENT_SETTINGS", "admin-alpha");

        assertThat(service.activeForTenantSurface("tenant-alpha", "CLIENT_SETTINGS"))
            .extracting(TenantFieldConfiguration::fieldId)
            .containsExactly("draft-field");
        assertThat(service.activeForTenantSurface("tenant-beta", "CLIENT_SETTINGS"))
            .extracting(TenantFieldConfiguration::fieldId)
            .containsExactly("beta-field");
    }

    @Test
    void publishRejectsDuplicateFieldIdsAndBrokenConditionReferences() {
        TenantFieldConfigurationStoreService service = service();
        service.saveDraft("tenant-alpha", "APPLICATION_FORM", List.of(
            systemField("tenant-alpha", "APPLICATION_FORM", "borrower-name", "Borrower", "Borrower field", true, false),
            systemField("tenant-alpha", "APPLICATION_FORM", "borrower-name", "Borrower duplicate", "Duplicate", true, false)
        ), "admin-alpha");

        assertThatThrownBy(() -> service.publishDraft("tenant-alpha", "APPLICATION_FORM", "admin-alpha"))
            .isInstanceOf(TenantFieldConfigException.class)
            .extracting(Throwable::getMessage)
            .isEqualTo("TENANT_FIELD_DUPLICATE_FIELD_ID");

        service.saveDraft("tenant-alpha", "APPLICATION_FORM", List.of(
            systemField("tenant-alpha", "APPLICATION_FORM", "borrower-name", "Borrower", "Borrower field", true, false)
        ), Map.of("borrower-name", List.of("missing-income")), "admin-alpha");

        assertThatThrownBy(() -> service.publishDraft("tenant-alpha", "APPLICATION_FORM", "admin-alpha"))
            .isInstanceOf(TenantFieldConfigException.class)
            .extracting(Throwable::getMessage)
            .isEqualTo("TENANT_FIELD_CONDITION_REFERENCE_BROKEN");
    }

    @Test
    void auditRecordsTenantUserOldNewSurfaceAndTimestamp() {
        TenantFieldConfigurationStoreService service = service();
        service.save(nativeField("tenant-alpha", "NOTIFICATION", "notice", "Old notice"));
        service.saveDraft("tenant-alpha", "NOTIFICATION", List.of(
            nativeField("tenant-alpha", "NOTIFICATION", "notice", "New notice")
        ), "admin-alpha");

        service.publishDraft("tenant-alpha", "NOTIFICATION", "admin-alpha");

        assertThat(service.auditRecordsForTenant("tenant-alpha"))
            .singleElement()
            .satisfies(record -> {
                assertThat(record.tenantId()).isEqualTo("tenant-alpha");
                assertThat(record.userId()).isEqualTo("admin-alpha");
                assertThat(record.oldValue()).contains("Old notice");
                assertThat(record.newValue()).contains("New notice");
                assertThat(record.affectedSurface()).isEqualTo("NOTIFICATION");
                assertThat(record.timestamp()).isEqualTo(NOW);
            });
    }

    private static TenantFieldConfigurationStoreService service() {
        return new TenantFieldConfigurationStoreService(Clock.fixed(NOW, ZoneOffset.UTC), new TestOnlyTenantFieldConfigurationStore());
    }

    private static TenantFieldConfiguration nativeField(String tenantId, String surface, String fieldId, String alias) {
        return new TenantFieldConfiguration(null, tenantId, surface, fieldId, FieldOrigin.NATIVE, "", alias, "", true, false, null, null);
    }

    private static TenantFieldConfiguration systemField(String tenantId, String surface, String fieldId, String alias, String description, boolean enabled, boolean omitted) {
        return new TenantFieldConfiguration(null, tenantId, surface, fieldId, FieldOrigin.INHERITED_SYSTEM, "system-field:" + fieldId, alias, description, enabled, omitted, null, null);
    }

    private static Map<String, String> contextHeaders(String tenantId) {
        return Map.ofEntries(
            Map.entry("X-Tenant-Id", tenantId),
            Map.entry("X-Request-Id", "request-" + tenantId),
            Map.entry("X-Trace-Id", "trace-" + tenantId),
            Map.entry("X-Actor-Id", "actor-" + tenantId),
            Map.entry("X-Actor-Type", "USER"),
            Map.entry("X-Roles", "pricing-analyst"),
            Map.entry("X-Scopes", "tenant:context:read"),
            Map.entry("X-Channel", "tenant-field-library"),
            Map.entry("X-Correlation-Id", "correlation-" + tenantId),
            Map.entry("X-Causation-Id", "cause-" + tenantId),
            Map.entry("X-Idempotency-Key", "idem-" + tenantId),
            Map.entry("X-Request-Source", "tenant-field-library"),
            Map.entry("X-Allowed-Tenant-Ids", tenantId),
            Map.entry("X-Selected-Tenant-Id", tenantId),
            Map.entry("X-Tenant-Status", "ACTIVE")
        );
    }
}
