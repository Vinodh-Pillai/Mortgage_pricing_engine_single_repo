package com.wcpe.catalog.domain;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.wcpe.catalog.auth.AuthorizationService;
import com.wcpe.catalog.auth.TenantProductAuthorization;
import com.wcpe.catalog.auth.TenantProductAuthorizationService;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

class TenantProductAuthorizationFlowContractTest {
  private static final Instant AS_OF = Instant.parse("2026-03-01T12:00:00Z");

  @Test
  void productListFiltersUnauthorizedProductsAndFailsClosedWhenAuthorizationSetIsEmpty() {
    UUID tenantId = UUID.randomUUID();
    CatalogService service = mock(CatalogService.class);
    DomainRepository domainRepository = mock(DomainRepository.class);
    AuthorizationService rbac = mock(AuthorizationService.class);
    TenantProductAuthorizationService tenantAuthorization = mock(TenantProductAuthorizationService.class);
    CatalogController controller = new CatalogController(service, domainRepository, rbac, tenantAuthorization);
    MockHttpServletRequest http = readRequest();
    when(domainRepository.listProducts(tenantId, null)).thenReturn(List.of(
        new ProductResponse(UUID.randomUUID(), "CONV30", "Conventional 30", "CONVENTIONAL", "PUBLISHED", AS_OF),
        new ProductResponse(UUID.randomUUID(), "JUMBO30", "Jumbo 30", "JUMBO", "PUBLISHED", AS_OF)));
    when(tenantAuthorization.getAuthorizedRulesAsOf(eq(tenantId), any())).thenReturn(List.of(active(tenantId, "CONV30", null, null)));

    ProductListResponse filtered = controller.listProductsDomain(tenantId, null, http);

    assertThat(filtered.products()).extracting(ProductResponse::code).containsExactly("CONV30");
    assertThat(filtered.count()).isEqualTo(1);

    when(tenantAuthorization.getAuthorizedRulesAsOf(eq(tenantId), any())).thenReturn(List.of());
    assertThatThrownBy(() -> controller.listProductsDomain(tenantId, null, http))
        .isInstanceOf(CatalogException.class)
        .hasMessage("TENANT_PRODUCT_AUTHORIZATION_CONFIG_REQUIRED");
  }

  @Test
  void productPricingConfigurationRejectsUnauthorizedExplicitProductAndMissingAuthorizationConfig() {
    UUID tenantId = UUID.randomUUID();
    CatalogRepository repository = mock(CatalogRepository.class);
    TenantProductAuthorizationService tenantAuthorization = mock(TenantProductAuthorizationService.class);
    CatalogService service = new CatalogService(repository, mock(AuthorizationService.class), tenantAuthorization);
    ProductPricingConfigurationResponse jumboPricing = new ProductPricingConfigurationResponse(
        "JUMBO30", UUID.randomUUID(), AS_OF, null, List.of(), "catalog-audit:pricing:JUMBO30");
    when(repository.resolveProductPricingConfiguration(tenantId, "JUMBO30", AS_OF)).thenReturn(jumboPricing);
    when(tenantAuthorization.getAuthorizedRulesAsOf(tenantId, AS_OF)).thenReturn(List.of(active(tenantId, "CONV30", null, null)));

    assertThatThrownBy(() -> service.resolveProductPricingConfiguration(tenantId, "JUMBO30", AS_OF))
        .isInstanceOf(CatalogException.class)
        .hasMessage("PRODUCT_NOT_AUTHORIZED");

    when(tenantAuthorization.getAuthorizedRulesAsOf(tenantId, AS_OF)).thenReturn(List.of());
    assertThatThrownBy(() -> service.resolveProductPricingConfiguration(tenantId, "JUMBO30", AS_OF))
        .isInstanceOf(CatalogException.class)
        .hasMessage("TENANT_PRODUCT_AUTHORIZATION_CONFIG_REQUIRED");
  }

  @Test
  void catalogSnapshotFiltersUnauthorizedProductComponentsAndFailsClosedForEmptyAuthorizationSet() {
    UUID tenantId = UUID.randomUUID();
    UUID catalogId = UUID.randomUUID();
    CatalogRepository repository = mock(CatalogRepository.class);
    TenantProductAuthorizationService tenantAuthorization = mock(TenantProductAuthorizationService.class);
    CatalogService service = new CatalogService(repository, mock(AuthorizationService.class), tenantAuthorization);
    ResolveCatalogRequest request = new ResolveCatalogRequest(AS_OF, null, "RETAIL", null, "TX", null, "CONVENTIONAL", null, "FNMA", "PURCHASE", null, null, null, null, false);
    allowIdempotent(repository);
    when(repository.activeCatalogId(tenantId)).thenReturn(catalogId);
    when(repository.resolveMaterialized(tenantId, request, "corr-snapshot")).thenReturn(new ProductConfigSnapshotMaterialization(snapshotWithAuthorizedAndUnauthorizedProducts(tenantId), true));
    when(tenantAuthorization.getAuthorizedRulesAsOf(tenantId, AS_OF)).thenReturn(List.of(active(tenantId, "CONV30", null, "RETAIL")));

    ProductConfigSnapshot snapshot = service.resolve(tenantId, request, "snapshot-key", "svc-pricing", "corr-snapshot");

    assertThat(snapshot.products()).extracting(ProductDefinition::productCode).containsExactly("CONV30");
    assertThat(snapshot.productComponents()).extracting(SnapshotProduct::productCode).containsExactly("CONV30");
    assertThat(snapshot.investors()).extracting(InvestorProgram::investorCode).containsExactly("FNMA");

    when(tenantAuthorization.getAuthorizedRulesAsOf(tenantId, AS_OF)).thenReturn(List.of());
    assertThatThrownBy(() -> service.resolve(tenantId, request, "snapshot-key-empty", "svc-pricing", "corr-snapshot"))
        .isInstanceOf(CatalogException.class)
        .hasMessage("TENANT_PRODUCT_AUTHORIZATION_CONFIG_REQUIRED");
  }

  @Test
  void conventionalResolveRejectsUnauthorizedProductCandidatesAndFailsClosedForEmptyAuthorizationSet() {
    UUID tenantId = UUID.randomUUID();
    UUID catalogId = UUID.randomUUID();
    CatalogRepository repository = mock(CatalogRepository.class);
    TenantProductAuthorizationService tenantAuthorization = mock(TenantProductAuthorizationService.class);
    CatalogService service = new CatalogService(repository, mock(AuthorizationService.class), tenantAuthorization);
    ConventionalProductResolveRequest request = new ConventionalProductResolveRequest(AS_OF, "RETAIL", "PURCHASE", "SINGLE_FAMILY", "PRIMARY_RESIDENCE", "TX", null, 360, "FIXED");
    when(repository.activeCatalogId(tenantId)).thenReturn(catalogId);
    when(repository.resolveConventionalProducts(tenantId, request)).thenReturn(new ConventionalProductResolveResponse(
        List.of(
            new ConventionalProductMatch("CONV30", UUID.randomUUID(), List.of("FNMA"), "sha256:conv30"),
            new ConventionalProductMatch("JUMBO30", UUID.randomUUID(), List.of("JPM"), "sha256:jumbo30")),
        List.of()));
    when(tenantAuthorization.getAuthorizedRulesAsOf(tenantId, AS_OF)).thenReturn(List.of(active(tenantId, "CONV30", "FNMA", "RETAIL")));

    ConventionalProductResolveResponse resolved = service.resolveConventionalProducts(tenantId, request, "svc-pricing", "corr-conv");

    assertThat(resolved.eligibleProducts()).extracting(ConventionalProductMatch::productCode).containsExactly("CONV30");
    assertThat(resolved.rejectedProducts()).singleElement().satisfies(rejected -> {
      assertThat(rejected.productCode()).isEqualTo("JUMBO30");
      assertThat(rejected.code()).isEqualTo("PRODUCT_NOT_AUTHORIZED");
    });

    when(tenantAuthorization.getAuthorizedRulesAsOf(tenantId, AS_OF)).thenReturn(List.of());
    assertThatThrownBy(() -> service.resolveConventionalProducts(tenantId, request, "svc-pricing", "corr-conv"))
        .isInstanceOf(CatalogException.class)
        .hasMessage("TENANT_PRODUCT_AUTHORIZATION_CONFIG_REQUIRED");
  }

  @Test
  void authorizationFailuresReturnTenantProductAuthorizationFieldErrorsForApiContracts() {
    CatalogController controller = new CatalogController(mock(CatalogService.class), mock(DomainRepository.class), mock(AuthorizationService.class));
    MockHttpServletRequest http = readRequest();

    ResponseEntity<Map<String, Object>> missingConfig = controller.error(new CatalogException("TENANT_PRODUCT_AUTHORIZATION_CONFIG_REQUIRED"), http);
    ResponseEntity<Map<String, Object>> unauthorizedProduct = controller.error(new CatalogException("PRODUCT_NOT_AUTHORIZED"), http);

    assertAuthorizationFieldError(missingConfig, "TENANT_PRODUCT_AUTHORIZATION_CONFIG_REQUIRED");
    assertAuthorizationFieldError(unauthorizedProduct, "PRODUCT_NOT_AUTHORIZED");
  }

  private static MockHttpServletRequest readRequest() {
    MockHttpServletRequest http = new MockHttpServletRequest();
    http.addHeader("X-Roles", "CATALOG_READER");
    http.addHeader("X-Correlation-Id", "corr-tenant-product-authz");
    return http;
  }

  private static TenantProductAuthorization active(UUID tenantId, String productCode, String investorCode, String channelCode) {
    return new TenantProductAuthorization(tenantId, productCode, investorCode, channelCode, "ACTIVE", Instant.parse("2026-01-01T00:00:00Z"), "test-seed", null, "contract test authorization");
  }

  private static ProductConfigSnapshot snapshotWithAuthorizedAndUnauthorizedProducts(UUID tenantId) {
    ProductDefinition conv30 = new ProductDefinition(UUID.randomUUID(), "CONV30", "Conventional 30", "CONVENTIONAL", List.of("RETAIL"), List.of("TX"), LocalDate.of(2026, 1, 1), null);
    ProductDefinition jumbo30 = new ProductDefinition(UUID.randomUUID(), "JUMBO30", "Jumbo 30", "JUMBO", List.of("RETAIL"), List.of("TX"), LocalDate.of(2026, 1, 1), null);
    InvestorProgram fnma = new InvestorProgram(UUID.randomUUID(), "FNMA", "Fannie Mae", List.of("RETAIL"), List.of("CONV30"), LocalDate.of(2026, 1, 1), null);
    InvestorProgram jpm = new InvestorProgram(UUID.randomUUID(), "JPM", "JPMorgan", List.of("RETAIL"), List.of("JUMBO30"), LocalDate.of(2026, 1, 1), null);
    return new ProductConfigSnapshot(
        UUID.randomUUID(), tenantId, "sha256:snapshot", LocalDate.of(2026, 3, 1),
        List.of(conv30, jumbo30), List.of(fnma, jpm), List.of(), List.of(), AS_OF,
        new SnapshotChannel("RETAIL", UUID.randomUUID()), List.of(),
        List.of(
            new SnapshotProduct("CONV30", UUID.randomUUID(), List.of("FNMA"), List.of("FIXED_30YR")),
            new SnapshotProduct("JUMBO30", UUID.randomUUID(), List.of("JPM"), List.of("FIXED_30YR"))),
        List.of(new SnapshotInvestor("FNMA", UUID.randomUUID(), true), new SnapshotInvestor("JPM", UUID.randomUUID(), true)),
        Map.of("products", List.of("CONV30", "JUMBO30")), List.of(), "request-hash", "corr-snapshot");
  }

  private static void assertAuthorizationFieldError(ResponseEntity<Map<String, Object>> response, String expectedCode) {
    assertThat(response.getStatusCode().value()).isEqualTo(422);
    assertThat(response.getBody()).containsEntry("errorCode", expectedCode);
    assertThat((List<?>) response.getBody().get("fieldErrors")).singleElement().satisfies(error -> {
      @SuppressWarnings("unchecked")
      Map<String, Object> fieldError = (Map<String, Object>) error;
      assertThat(fieldError)
          .containsEntry("field", "tenantProductAuthorization")
          .containsEntry("code", expectedCode);
    });
  }

  @SuppressWarnings("unchecked")
  private static void allowIdempotent(CatalogRepository repository) {
    when(repository.idempotent(any(UUID.class), anyString(), any(), any(), any())).thenAnswer((Answer<Object>) invocation -> {
      Supplier<Object> command = invocation.getArgument(4, Supplier.class);
      return command.get();
    });
  }
}
