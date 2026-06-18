package com.wcpe.catalog.domain;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.catalog.auth.AuthorizationService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;

class ProductCreationEndpointContractTest {
  @AfterEach
  void clearRequestContext() {
    RequestContext.clear();
  }

  @Test
  void servicePersistsVersionedProductMappingRefsAndEmitsAuditOutboxEvidence() {
    UUID tenantId = UUID.randomUUID();
    UUID catalogId = UUID.randomUUID();
    UUID productId = UUID.randomUUID();
    UUID productVersionId = UUID.randomUUID();
    CatalogRepository repository = mock(CatalogRepository.class);
    CatalogService service = new CatalogService(repository, mock(AuthorizationService.class));
    RequestContext.roles("CATALOG_ADMIN");
    ProductCreationRequest request = validRequest("CONV30", Map.of("loanPassProductCode", "LP-CONV30"));
    ProductDefinition product = new ProductDefinition(productId, "CONV30", "Conventional 30", "CONVENTIONAL", List.of("RETAIL"), List.of("TX"), java.time.LocalDate.of(2026, 1, 1), null);
    ProductCreationPersistence persisted = new ProductCreationPersistence(product, productVersionId, "DRAFT", Map.of("loanPassProductCode", "LP-CONV30"));
    CatalogResponse before = new CatalogResponse(catalogId, 1, CatalogStatus.DRAFT, List.of(), List.of(), List.of(), List.of(), "before");
    CatalogResponse after = new CatalogResponse(catalogId, 2, CatalogStatus.DRAFT, List.of(product), List.of(), List.of(), List.of(), "after");
    when(repository.idempotent(eq(tenantId), eq("create-key"), eq(request), eq(ProductCreationResponse.class), any())).thenAnswer(invocation -> {
      @SuppressWarnings("unchecked")
      Supplier<ProductCreationResponse> command = invocation.getArgument(4, Supplier.class);
      return command.get();
    });
    when(repository.currentCatalogId(tenantId)).thenReturn(catalogId);
    when(repository.current(tenantId)).thenReturn(before, after);
    when(repository.replayHash(tenantId, catalogId)).thenReturn("after");
    when(repository.addProductCreation(eq(tenantId), eq(catalogId), any(ProductCreationDraft.class), eq("actor-1"))).thenReturn(persisted);

    ProductCreationResponse response = service.createProduct(tenantId, request, "create-key", "actor-1", "corr-1");

    assertThat(response.productId()).isEqualTo(productId);
    assertThat(response.productVersionId()).isEqualTo(productVersionId);
    assertThat(response.metadataRefs()).containsEntry("loanPassProductCode", "LP-CONV30");
    ArgumentCaptor<ProductCreationDraft> draft = ArgumentCaptor.forClass(ProductCreationDraft.class);
    verify(repository).addProductCreation(eq(tenantId), eq(catalogId), draft.capture(), eq("actor-1"));
    assertThat(draft.getValue().metadataRefs()).containsEntry("loanPassProductCode", "LP-CONV30");
    ArgumentCaptor<CatalogEvent> event = ArgumentCaptor.forClass(CatalogEvent.class);
    verify(repository).event(event.capture());
    assertThat(event.getValue().eventType()).isEqualTo("ProductCreationEndpointAccepted.v1");
    assertThat(event.getValue().payload()).containsEntry("productCode", "CONV30").containsKey("productVersionId");
    verify(repository).audit(eq(tenantId), eq(catalogId), eq("PRODUCT_CREATION_ENDPOINT_ACCEPTED"), eq("after"), eq(before), eq(after), argThat(payload -> payload.containsKey("metadataRefKeys")), eq("actor-1"), eq("corr-1"), eq("create-key"));
  }

  @Test
  void duplicateProductCodeIsEndpointOverlapProtectionAndDoesNotEmitAuditOrOutbox() {
    UUID tenantId = UUID.randomUUID();
    UUID catalogId = UUID.randomUUID();
    CatalogRepository repository = mock(CatalogRepository.class);
    CatalogService service = new CatalogService(repository, mock(AuthorizationService.class));
    RequestContext.roles("CATALOG_ADMIN");
    ProductCreationRequest request = validRequest("CONV30", Map.of());
    when(repository.idempotent(eq(tenantId), eq("dup-key"), eq(request), eq(ProductCreationResponse.class), any())).thenAnswer(invocation -> {
      @SuppressWarnings("unchecked")
      Supplier<ProductCreationResponse> command = invocation.getArgument(4, Supplier.class);
      return command.get();
    });
    when(repository.currentCatalogId(tenantId)).thenReturn(catalogId);
    when(repository.current(tenantId)).thenReturn(new CatalogResponse(catalogId, 1, CatalogStatus.DRAFT, List.of(), List.of(), List.of(), List.of(), "before"));
    when(repository.addProductCreation(eq(tenantId), eq(catalogId), any(ProductCreationDraft.class), eq("actor-1"))).thenThrow(new CatalogException("PRODUCT_CODE_DUPLICATE"));

    assertThatThrownBy(() -> service.createProduct(tenantId, request, "dup-key", "actor-1", "corr-1"))
        .isInstanceOf(CatalogException.class)
        .hasMessage("PRODUCT_CODE_DUPLICATE");

    verify(repository, never()).event(any());
    verify(repository, never()).audit(any(), any(), eq("PRODUCT_CREATION_ENDPOINT_ACCEPTED"), any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void productPricingConfigurationPersistsVersionedRefsWithoutRulePayloadLeakage() {
    UUID tenantId = UUID.randomUUID();
    UUID catalogId = UUID.randomUUID();
    UUID productVersionId = UUID.randomUUID();
    UUID rateSheetVersionId = UUID.randomUUID();
    CatalogRepository repository = mock(CatalogRepository.class);
    CatalogService service = new CatalogService(repository, mock(AuthorizationService.class));
    RequestContext.roles("CATALOG_ADMIN");
    ProductPricingConfigurationRequest request = new ProductPricingConfigurationRequest("CONV30", Instant.parse("2026-02-01T00:00:00Z"), null,
        List.of(new PricingConfigReference("RATE_SHEET_PROFILE", "LP-CONV30-RETAIL", rateSheetVersionId)));
    ProductPricingConfigurationResponse stored = new ProductPricingConfigurationResponse("CONV30", productVersionId, request.effectiveStart(), request.effectiveEnd(), request.refs(), "catalog-audit:cfg");
    CatalogResponse before = new CatalogResponse(catalogId, 1, CatalogStatus.DRAFT, List.of(), List.of(), List.of(), List.of(), "before");
    CatalogResponse after = new CatalogResponse(catalogId, 2, CatalogStatus.DRAFT, List.of(), List.of(), List.of(), List.of(), "after");
    when(repository.idempotent(eq(tenantId), eq("pricing-config-key"), eq(request), eq(ProductPricingConfigurationResponse.class), any())).thenAnswer(invocation -> {
      @SuppressWarnings("unchecked")
      Supplier<ProductPricingConfigurationResponse> command = invocation.getArgument(4, Supplier.class);
      return command.get();
    });
    when(repository.currentCatalogId(tenantId)).thenReturn(catalogId);
    when(repository.current(tenantId)).thenReturn(before, after);
    when(repository.replayHash(tenantId, catalogId)).thenReturn("after");
    when(repository.attachProductPricingConfiguration(tenantId, catalogId, request, "actor-1")).thenReturn(stored);

    ProductPricingConfigurationResponse response = service.attachProductPricingConfiguration(tenantId, request, "pricing-config-key", "actor-1", "corr-1");

    assertThat(response.refs()).containsExactly(new PricingConfigReference("RATE_SHEET_PROFILE", "LP-CONV30-RETAIL", rateSheetVersionId));
    assertThat(response.auditRef()).isEqualTo("catalog-audit:cfg");
    ArgumentCaptor<CatalogEvent> event = ArgumentCaptor.forClass(CatalogEvent.class);
    verify(repository).event(event.capture());
    assertThat(event.getValue().eventType()).isEqualTo("ProductPricingConfigurationAttached.v1");
    assertThat(event.getValue().payload()).containsEntry("productCode", "CONV30").containsEntry("refCount", 1).containsKey("refTypes");
    assertThat(event.getValue().payload()).doesNotContainKeys("rules", "rateRows", "marginPolicyPayload", "adjustmentRules");
    verify(repository).audit(eq(tenantId), eq(catalogId), eq("PRODUCT_PRICING_CONFIGURATION_ATTACHED"), eq("after"), eq(before), eq(after), argThat(payload -> payload.containsKey("refTypes") && !payload.containsKey("rules")), eq("actor-1"), eq("corr-1"), eq("pricing-config-key"));
  }

  @Test
  void pricingConfigurationRejectsNonPricingReferenceTypeBeforeVersionLookup() {
    UUID tenantId = UUID.randomUUID();
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    CatalogRepository repository = new CatalogRepository(jdbc, new ObjectMapper());

    assertThatThrownBy(() -> repository.validatePricingConfigRefs(tenantId, Instant.parse("2026-02-01T00:00:00Z"),
        List.of(new PricingConfigReference("INVESTOR", "FNMA", UUID.randomUUID()))))
        .isInstanceOf(CatalogException.class)
        .hasMessage("PRICING_CONFIG_REF_TYPE_UNSUPPORTED");

    verify(jdbc, never()).queryForObject(anyString(), eq(Integer.class), any(), any(), any(), any(), any(), any());
  }

  @Test
  void pricingConfigurationRejectsMissingReferencedVersion() {
    UUID tenantId = UUID.randomUUID();
    UUID missingVersion = UUID.randomUUID();
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    CatalogRepository repository = new CatalogRepository(jdbc, new ObjectMapper());
    when(jdbc.queryForObject(anyString(), eq(Integer.class), any(), any(), any(), any(), any(), any())).thenReturn(0);

    assertThatThrownBy(() -> repository.validatePricingConfigRefs(tenantId, Instant.parse("2026-02-01T00:00:00Z"),
        List.of(new PricingConfigReference("RATE_SHEET_PROFILE", "LP-CONV30-RETAIL", missingVersion))))
        .isInstanceOf(CatalogException.class)
        .hasMessage("PRICING_CONFIG_REFERENCE_NOT_ACTIVE");
  }

  @Test
  void pricingConfigurationRejectsExpiredEffectiveWindow() {
    UUID tenantId = UUID.randomUUID();
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    CatalogRepository repository = new CatalogRepository(jdbc, new ObjectMapper());
    when(jdbc.queryForObject(anyString(), eq(Integer.class), any(), any(), any(), any(), any(), any())).thenReturn(0);

    assertThatThrownBy(() -> repository.validatePricingConfigRefs(tenantId, Instant.parse("2026-02-01T00:00:00Z"),
        List.of(new PricingConfigReference("RATE_SHEET_PROFILE", "EXPIRED-CONV30", UUID.randomUUID()))))
        .isInstanceOf(CatalogException.class)
        .hasMessage("PRICING_CONFIG_REFERENCE_NOT_ACTIVE");
  }

  @Test
  void pricingConfigurationRejectsWrongTenantReference() {
    UUID tenantId = UUID.randomUUID();
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    CatalogRepository repository = new CatalogRepository(jdbc, new ObjectMapper());
    when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(tenantId), eq("RATE_SHEET_PROFILE"), eq("LP-CONV30-RETAIL"), any(), any(), any())).thenReturn(0);

    assertThatThrownBy(() -> repository.validatePricingConfigRefs(tenantId, Instant.parse("2026-02-01T00:00:00Z"),
        List.of(new PricingConfigReference("RATE_SHEET_PROFILE", "LP-CONV30-RETAIL", UUID.randomUUID()))))
        .isInstanceOf(CatalogException.class)
        .hasMessage("PRICING_CONFIG_REFERENCE_NOT_ACTIVE");
  }

  @Test
  void pricingConfigurationAsOfLookupExposesOnlyVersionedRefs() {
    UUID tenantId = UUID.randomUUID();
    UUID catalogId = UUID.randomUUID();
    UUID rateSheetVersionId = UUID.randomUUID();
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    CatalogRepository repository = new CatalogRepository(jdbc, new ObjectMapper());
    when(jdbc.queryForList(anyString(), eq(tenantId), eq(catalogId), eq("CONV30"), any(java.sql.Date.class), any(java.sql.Date.class)))
        .thenReturn(List.of(Map.of("attributes", "{\"pricingConfigRefs\":[{\"refType\":\"RATE_SHEET_PROFILE\",\"refCode\":\"LP-CONV30-RETAIL\",\"versionId\":\"" + rateSheetVersionId + "\"}],\"rules\":[{\"internal\":true}]}")));

    List<PricingConfigReference> refs = repository.pricingConfigurationRefs(tenantId, catalogId, "CONV30", LocalDate.of(2026, 2, 1));

    assertThat(refs).containsExactly(new PricingConfigReference("RATE_SHEET_PROFILE", "LP-CONV30-RETAIL", rateSheetVersionId));
  }

  @Test
  void validationFailureContractReturnsActionableFieldErrors() {
    CatalogController controller = new CatalogController(mock(CatalogService.class), mock(DomainRepository.class), mock(AuthorizationService.class));
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Correlation-Id", "corr-product-create");

    ResponseEntity<Map<String, Object>> response = controller.error(new CatalogException("PRODUCT_NAME_REQUIRED"), request);

    assertThat(response.getStatusCode().value()).isEqualTo(422);
    assertThat(response.getBody()).containsEntry("errorCode", "PRODUCT_NAME_REQUIRED");
    assertThat(response.getBody()).containsEntry("correlationId", "corr-product-create");
    assertThat((List<?>) response.getBody().get("fieldErrors")).singleElement().satisfies(error -> {
      @SuppressWarnings("unchecked")
      Map<String, Object> fieldError = (Map<String, Object>) error;
      assertThat(fieldError).containsEntry("field", "displayName").containsEntry("code", "PRODUCT_NAME_REQUIRED");
    });
  }

  @Test
  void pricingConfigurationValidationFailureReturnsActionableFieldErrors() {
    CatalogController controller = new CatalogController(mock(CatalogService.class), mock(DomainRepository.class), mock(AuthorizationService.class));
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Correlation-Id", "corr-pricing-config");

    ResponseEntity<Map<String, Object>> response = controller.error(new CatalogException("PRICING_CONFIG_REFERENCE_NOT_ACTIVE"), request);

    assertThat(response.getStatusCode().value()).isEqualTo(422);
    assertThat((List<?>) response.getBody().get("fieldErrors")).singleElement().satisfies(error -> {
      @SuppressWarnings("unchecked")
      Map<String, Object> fieldError = (Map<String, Object>) error;
      assertThat(fieldError).containsEntry("field", "pricingConfigurationRefs").containsEntry("code", "PRICING_CONFIG_REFERENCE_NOT_ACTIVE");
    });
  }

  @Test
  void unauthorizedRequestRejectsBeforeMutation() {
    CatalogService service = mock(CatalogService.class);
    AuthorizationService authorization = mock(AuthorizationService.class);
    CatalogController controller = new CatalogController(service, mock(DomainRepository.class), authorization);
    MockHttpServletRequest http = new MockHttpServletRequest();
    http.addHeader("X-Roles", "CATALOG_READER");
    doThrow(new CatalogException("ROLE_REQUIRED_WRITE_CATALOG")).when(authorization).authorize(eq("WRITE_CATALOG"), eq("CATALOG_READER"));

    assertThatThrownBy(() -> controller.createProduct(UUID.randomUUID(), validRequest("CONV30", Map.of()), http))
        .isInstanceOf(CatalogException.class)
        .hasMessage("ROLE_REQUIRED_WRITE_CATALOG");

    verifyNoInteractions(service);
  }

  private static ProductCreationRequest validRequest(String productCode, Map<String, Object> metadataRefs) {
    return new ProductCreationRequest(productCode, "Conventional 30", "CONVENTIONAL", "FIXED",
        List.of(360), List.of("FIXED"), List.of("PURCHASE"), List.of("RETAIL"), List.of("TX"), metadataRefs,
        Instant.parse("2026-01-01T00:00:00Z"), null, "DRAFT");
  }
}
