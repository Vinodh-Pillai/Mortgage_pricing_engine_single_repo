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
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

class LoanPassTenantMappingCatalogAuthorizationTest {
  private static final Instant AS_OF = Instant.parse("2026-03-01T12:00:00Z");

  @Test
  void mappedTenantContextDrivesTenantIsolatedCatalogFilteringAndAuditRef() {
    UUID tenantId = UUID.randomUUID();
    UUID catalogId = UUID.randomUUID();
    CatalogRepository repository = mock(CatalogRepository.class);
    TenantProductAuthorizationService tenantAuthorization = mock(TenantProductAuthorizationService.class);
    CatalogService service = new CatalogService(repository, mock(AuthorizationService.class), tenantAuthorization);
    allowIdempotent(repository);
    when(repository.activeCatalogId(tenantId)).thenReturn(catalogId);
    when(repository.replayHash(tenantId, catalogId)).thenReturn("sha256:catalog");
    when(repository.resolveMaterialized(eq(tenantId), any(ResolveCatalogRequest.class), eq("corr-mapped")))
        .thenReturn(new ProductConfigSnapshotMaterialization(snapshotWithTwoProducts(tenantId), true));
    when(tenantAuthorization.getAuthorizedRulesAsOf(tenantId, AS_OF))
        .thenReturn(List.of(active(tenantId, "CONV30", "FNMA", "RETAIL")));

    ProductConfigSnapshot snapshot = service.resolveLoanPassMappedCatalog(tenantId,
        new LoanPassMappedCatalogRequest(tenantId.toString(), "RETAIL", "FNMA", "tenant-map-audit:map-retail", AS_OF, "TX", null, "CONVENTIONAL", "PURCHASE", null, null, null, null, false),
        "mapped-key", "svc-loanpass", "corr-mapped");

    assertThat(snapshot.products()).extracting(ProductDefinition::productCode).containsExactly("CONV30");
    assertThat(snapshot.investors()).extracting(InvestorProgram::investorCode).containsExactly("FNMA");
    ArgumentCaptor<ResolveCatalogRequest> requestCaptor = ArgumentCaptor.forClass(ResolveCatalogRequest.class);
    verify(repository).resolveMaterialized(eq(tenantId), requestCaptor.capture(), eq("corr-mapped"));
    assertThat(requestCaptor.getValue().requestedChannel()).isEqualTo("RETAIL");
    assertThat(requestCaptor.getValue().investorCode()).isEqualTo("FNMA");
    verify(repository, atLeastOnce()).audit(eq(tenantId), eq(catalogId), eq("LOANPASS_TENANT_MAPPING_CONSUMED"), anyString(), any(), any(), any(), eq("svc-loanpass"), eq("corr-mapped"), eq("mapped-key"));
  }

  @Test
  void mappedTenantMismatchAndMissingAuditRefFailClosedWithFieldErrors() {
    UUID tenantId = UUID.randomUUID();
    CatalogService service = new CatalogService(mock(CatalogRepository.class), mock(AuthorizationService.class), mock(TenantProductAuthorizationService.class));

    assertThatThrownBy(() -> service.resolveLoanPassMappedCatalog(tenantId,
        new LoanPassMappedCatalogRequest(UUID.randomUUID().toString(), "RETAIL", "FNMA", "tenant-map-audit:other", AS_OF, "TX", null, "CONVENTIONAL", "PURCHASE", null, null, null, null, false),
        "key", "actor", "corr"))
        .isInstanceOf(CatalogException.class)
        .hasMessage("TENANT_MAPPING_TENANT_MISMATCH");

    CatalogController controller = new CatalogController(mock(CatalogService.class), mock(DomainRepository.class), mock(AuthorizationService.class));
    ResponseEntity<Map<String, Object>> missingAudit = controller.error(new CatalogException("TENANT_MAPPING_AUDIT_REF_REQUIRED"), readRequest());
    assertThat(missingAudit.getStatusCode().value()).isEqualTo(422);
    assertThat((List<?>) missingAudit.getBody().get("fieldErrors")).singleElement().satisfies(error -> {
      @SuppressWarnings("unchecked")
      Map<String, Object> fieldError = (Map<String, Object>) error;
      assertThat(fieldError)
          .containsEntry("field", "tenantMappingAuditRef")
          .containsEntry("code", "TENANT_MAPPING_AUDIT_REF_REQUIRED");
    });
  }

  private static MockHttpServletRequest readRequest() {
    MockHttpServletRequest http = new MockHttpServletRequest();
    http.addHeader("X-Roles", "CATALOG_READER");
    http.addHeader("X-Correlation-Id", "corr-mapped");
    return http;
  }

  private static TenantProductAuthorization active(UUID tenantId, String productCode, String investorCode, String channelCode) {
    return new TenantProductAuthorization(tenantId, productCode, investorCode, channelCode, "ACTIVE", Instant.parse("2026-01-01T00:00:00Z"), "tenant-map-test", null, "mapped context contract test authorization");
  }

  private static ProductConfigSnapshot snapshotWithTwoProducts(UUID tenantId) {
    ProductDefinition conv30 = new ProductDefinition(UUID.randomUUID(), "CONV30", "Conventional 30", "CONVENTIONAL", List.of("RETAIL"), List.of("TX"), LocalDate.of(2026, 1, 1), null);
    ProductDefinition jumbo30 = new ProductDefinition(UUID.randomUUID(), "JUMBO30", "Jumbo 30", "JUMBO", List.of("RETAIL"), List.of("TX"), LocalDate.of(2026, 1, 1), null);
    InvestorProgram fnma = new InvestorProgram(UUID.randomUUID(), "FNMA", "Fannie Mae", List.of("RETAIL"), List.of("CONV30"), LocalDate.of(2026, 1, 1), null);
    InvestorProgram jpm = new InvestorProgram(UUID.randomUUID(), "JPM", "JPMorgan", List.of("RETAIL"), List.of("JUMBO30"), LocalDate.of(2026, 1, 1), null);
    return new ProductConfigSnapshot(
        UUID.randomUUID(), tenantId, "sha256:mapped", LocalDate.of(2026, 3, 1),
        List.of(conv30, jumbo30), List.of(fnma, jpm), List.of(), List.of(), AS_OF,
        new SnapshotChannel("RETAIL", UUID.randomUUID()), List.of(),
        List.of(
            new SnapshotProduct("CONV30", UUID.randomUUID(), List.of("FNMA"), List.of("FIXED_30YR")),
            new SnapshotProduct("JUMBO30", UUID.randomUUID(), List.of("JPM"), List.of("FIXED_30YR"))),
        List.of(new SnapshotInvestor("FNMA", UUID.randomUUID(), true), new SnapshotInvestor("JPM", UUID.randomUUID(), true)),
        Map.of("products", List.of("CONV30", "JUMBO30")), List.of(), "request-hash", "corr-mapped");
  }

  @SuppressWarnings("unchecked")
  private static void allowIdempotent(CatalogRepository repository) {
    when(repository.idempotent(any(UUID.class), anyString(), any(), any(), any())).thenAnswer((Answer<Object>) invocation -> {
      Supplier<Object> command = invocation.getArgument(4, Supplier.class);
      return command.get();
    });
  }
}
