package com.wcpe.catalog.domain;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.wcpe.catalog.auth.AuthorizationService;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class FieldLibraryQueryPolicyTest {
  @Test
  void filtersBoundedCategoriesAndOmitsUnrequestedFieldGroups() {
    FieldLibraryQueryResponse product = FieldLibraryQueryPolicy.query("product", fields(), enumerations(), false, true);
    FieldLibraryQueryResponse application = FieldLibraryQueryPolicy.query("application", fields(), enumerations(), false, true);
    FieldLibraryQueryResponse pipeline = FieldLibraryQueryPolicy.query("pipeline", fields(), enumerations(), false, true);
    FieldLibraryQueryResponse clientSettings = FieldLibraryQueryPolicy.query("client-settings", fields(), enumerations(), false, true);
    FieldLibraryQueryResponse pricingNotification = FieldLibraryQueryPolicy.query("pricing-notification", fields(), enumerations(), false, true);

    assertThat(product.fields()).extracting(FieldLibraryFieldResponse::id).containsExactly("field@product-channel", "field@pricing-calculation-mode");
    assertThat(application.fields()).extracting(FieldLibraryFieldResponse::id).containsExactly("field@borrower-state");
    assertThat(pipeline.fields()).extracting(FieldLibraryFieldResponse::id).containsExactly("field@pipeline-status");
    assertThat(clientSettings.fields()).extracting(FieldLibraryFieldResponse::id).containsExactly("field@client-theme");
    assertThat(clientSettings.fields()).extracting(FieldLibraryFieldResponse::category).containsOnly("client-settings");
    assertThat(pricingNotification.fields()).extracting(FieldLibraryFieldResponse::id).containsExactly("field@notification-email");
    assertThat(pricingNotification.fields()).extracting(FieldLibraryFieldResponse::category).containsOnly("pricing-notification");
  }

  @Test
  void tenantAndSystemControllersReturnBoundedFieldLibrarySlices() {
    UUID tenantId = UUID.randomUUID();
    CatalogService service = mock(CatalogService.class);
    AuthorizationService authorization = mock(AuthorizationService.class);
    CatalogController tenantController = new CatalogController(service, mock(DomainRepository.class), authorization);
    SystemFieldLibraryController systemController = new SystemFieldLibraryController(service, authorization);
    MockHttpServletRequest http = readRequest();
    when(service.queryFieldLibrary(eq(tenantId), eq("client-settings"), eq(false), eq("svc-ui"), eq("corr-field-library")))
        .thenReturn(FieldLibraryQueryPolicy.query("client-settings", fields(), enumerations(), false, true));
    when(service.querySystemFieldLibrary(eq("pricing-notification"), eq(false), eq("svc-ui"), eq("corr-field-library")))
        .thenReturn(FieldLibraryQueryPolicy.query("pricing-notification", fields(), enumerations(), false, false));

    FieldLibraryQueryResponse tenantResponse = tenantController.queryFieldLibrary(tenantId, "client-settings", false, http);
    FieldLibraryQueryResponse systemResponse = systemController.querySystemFieldLibrary("pricing-notification", false, http);

    assertThat(tenantResponse.tenantSpecific()).isTrue();
    assertThat(tenantResponse.sourceScope()).isEqualTo("tenant");
    assertThat(tenantResponse.fields()).extracting(FieldLibraryFieldResponse::id).containsExactly("field@client-theme");
    assertThat(tenantResponse.fields()).extracting(FieldLibraryFieldResponse::category).containsOnly("client-settings");
    assertThat(systemResponse.tenantSpecific()).isFalse();
    assertThat(systemResponse.sourceScope()).isEqualTo("system/default");
    assertThat(systemResponse.fields()).extracting(FieldLibraryFieldResponse::id).containsExactly("field@notification-email");
    assertThat(systemResponse.fields()).extracting(FieldLibraryFieldResponse::category).containsOnly("pricing-notification");
    verify(authorization, times(2)).authorize("READ_CATALOG", "CATALOG_READER");
  }

  @Test
  void serviceQueriesApplyCategoryFiltersForTenantAndSystemDefault() {
    UUID tenantId = UUID.randomUUID();
    UUID catalogId = UUID.randomUUID();
    UUID systemTenantId = new UUID(0L, 0L);
    CatalogRepository repository = mock(CatalogRepository.class);
    CatalogService service = new CatalogService(repository, mock(AuthorizationService.class));
    when(repository.listFieldMetadata(tenantId)).thenReturn(fields());
    when(repository.listEnumerations(tenantId)).thenReturn(enumerations());
    when(repository.currentCatalogId(tenantId)).thenReturn(catalogId);
    when(repository.listFieldMetadata(systemTenantId)).thenReturn(fields());
    when(repository.listEnumerations(systemTenantId)).thenReturn(enumerations());

    FieldLibraryQueryResponse tenantResponse = service.queryFieldLibrary(tenantId, "application", false, "svc-ui", "corr-field-library");
    FieldLibraryQueryResponse systemResponse = service.querySystemFieldLibrary("product", true, "svc-ui", "corr-field-library");

    assertThat(tenantResponse.tenantSpecific()).isTrue();
    assertThat(tenantResponse.sourceScope()).isEqualTo("tenant");
    assertThat(tenantResponse.fields()).extracting(FieldLibraryFieldResponse::id).containsExactly("field@borrower-state");
    assertThat(tenantResponse.fields()).extracting(FieldLibraryFieldResponse::category).containsOnly("application");
    assertThat(systemResponse.tenantSpecific()).isFalse();
    assertThat(systemResponse.sourceScope()).isEqualTo("system/default");
    assertThat(systemResponse.fields()).extracting(FieldLibraryFieldResponse::id).containsExactly("field@product-channel", "field@pricing-calculation-mode");
    assertThat(systemResponse.fields()).extracting(FieldLibraryFieldResponse::category).containsOnly("product");
  }

  @Test
  void includesEnumLinksVariantsAndNormalizedConditionParentReferences() {
    FieldLibraryQueryResponse response = FieldLibraryQueryPolicy.query("product", fields(), enumerations(), true, true);

    FieldLibraryFieldResponse channel = response.fields().get(0);
    FieldLibraryFieldResponse calculation = response.fields().get(1);
    assertThat(channel.enumTypeId()).isEqualTo("product-channel");
    assertThat(channel.enumLink()).contains("product-channel");
    assertThat(channel.enumeration().variants()).extracting(EnumerationVariantResponse::variantId).containsExactly("retail", "wholesale");
    assertThat(calculation.conditions()).containsEntry("parentFieldId", "field@product-channel");
    assertThat(calculation.parentFieldReferences()).containsExactly("field@product-channel");
    assertThat(response.enumerations()).extracting(EnumerationTypeResponse::enumTypeId).containsExactly("product-channel");
  }

  @Test
  void marksMissingTenantQueryAsSystemDefaultAndNotTenantSpecific() {
    FieldLibraryQueryResponse response = FieldLibraryQueryPolicy.query("calculation", fields(), enumerations(), false, false);

    assertThat(response.sourceScope()).isEqualTo("system/default");
    assertThat(response.tenantSpecific()).isFalse();
    assertThat(response.fields()).extracting(FieldLibraryFieldResponse::id).containsExactly("field@debt-to-income-ratio");
  }

  private static List<FieldMetadataResponse> fields() {
    return List.of(
        new FieldMetadataResponse("field@product-channel", "field@product-channel", "Product Channel", "Channel selector",
            "product", "enum", "productFields", Map.of("enumTypeId", "product-channel"), "inherited", "ReferenceFormfields.json"),
        new FieldMetadataResponse("field@pricing-calculation-mode", "field@pricing-calculation-mode", "Pricing Calculation Mode", "Calculation mode",
            "product", "enum", "productFields", Map.of("conditionId", "channel-retail", "parentFieldId", "field@product-channel", "enumTypeId", "product-channel", "variantIds", List.of("retail")), "inherited", "ReferenceFormfields.json"),
        new FieldMetadataResponse("field@borrower-state", "field@borrower-state", "Borrower State", "State selector",
            "application", "us-state", "creditApplicationFields", Map.of(), "inherited", "ReferenceFormfields.json"),
        new FieldMetadataResponse("field@pipeline-status", "field@pipeline-status", "Pipeline Status", "Pipeline status",
            "pipeline", "enum", "pipelineOnlyFields", Map.of(), "native", "ReferenceFormfields.json"),
        new FieldMetadataResponse("field@client-theme", "field@client-theme", "Client Theme", "Client setting",
            "client-settings", "string", "rawFields", Map.of(), "native", "ReferenceFormfields.json"),
        new FieldMetadataResponse("field@notification-email", "field@notification-email", "Notification Email", "Pricing notification",
            "pricing-notification", "string", "rawFields", Map.of(), "native", "ReferenceFormfields.json"),
        new FieldMetadataResponse("field@debt-to-income-ratio", "field@debt-to-income-ratio", "Debt-to-income ratio", "Derived calculation",
            "calculation", "number", "rawFields", Map.of(), "native", "ReferenceFormfields.json"));
  }

  private static List<EnumerationTypeResponse> enumerations() {
    return List.of(new EnumerationTypeResponse("product-channel", "Product Channel", List.of(
        new EnumerationVariantResponse("retail", "101", "Retail"),
        new EnumerationVariantResponse("wholesale", "102", "Wholesale")),
        "ReferenceFormfields.json", "system/default"));
  }

  private static MockHttpServletRequest readRequest() {
    MockHttpServletRequest http = new MockHttpServletRequest();
    http.addHeader("X-Roles", "CATALOG_READER");
    http.addHeader("X-Actor-Id", "svc-ui");
    http.addHeader("X-Correlation-Id", "corr-field-library");
    return http;
  }
}
