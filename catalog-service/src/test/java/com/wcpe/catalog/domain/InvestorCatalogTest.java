package com.wcpe.catalog.domain;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.wcpe.catalog.auth.AuthorizationService;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

class InvestorCatalogTest {
  private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

  @AfterEach
  void clearRequestContext() {
    RequestContext.clear();
  }

  @Test
  void requiresSellerIdForActiveChannel() {
    InvestorCatalogDraftRequest request = fnma(List.of());

    assertThatThrownBy(() -> InvestorCatalogPolicy.validateDraft(request, false))
        .isInstanceOf(CatalogException.class)
        .hasMessage("SELLER_ID_REQUIRED");
  }

  @Test
  void enforcesAgencyCodeMapping() {
    InvestorCatalogDraftRequest request = new InvestorCatalogDraftRequest("FANNIE", "Federal National Mortgage Association", "AGENCY", "FANNIE_MAE",
        List.of(new InvestorSellerServicerId("RETAIL", "123456", "654321")), List.of("BEST_EFFORTS"), List.of("RETAIL"), true, CatalogStatus.DRAFT, START, null);

    assertThatThrownBy(() -> InvestorCatalogPolicy.validateDraft(request, false))
        .isInstanceOf(CatalogException.class)
        .hasMessage("AGENCY_CODE_MISMATCH");
  }

  @Test
  void masksSellerIdsWithoutPermission() {
    assertThat(InvestorCatalogPolicy.maskSellerId("123456", false)).isEqualTo("***456");
    assertThat(InvestorCatalogPolicy.maskSellerId("123456", true)).isEqualTo("123456");
  }

  @Test
  void investorResolveErrorsReturnStoryContractShape() {
    CatalogController controller = new CatalogController(mock(CatalogService.class), mock(DomainRepository.class), mock(AuthorizationService.class));
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Correlation-Id", "c-0204");

    ResponseEntity<Map<String, Object>> response = controller.error(new CatalogException("INVESTOR_NOT_ACTIVE_FOR_CHANNEL"), request);

    assertThat(response.getStatusCode().value()).isEqualTo(422);
    assertThat(response.getBody()).containsEntry("errorCode", "INVESTOR_NOT_ACTIVE_FOR_CHANNEL");
    assertThat((List<?>) response.getBody().get("fieldErrors")).isNotEmpty();
  }

  @Test
  void versionSeparationOfDutiesErrorsReturnForbidden() {
    CatalogController controller = new CatalogController(mock(CatalogService.class), mock(DomainRepository.class), mock(AuthorizationService.class));
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Correlation-Id", "c-0205");

    ResponseEntity<Map<String, Object>> response = controller.error(new CatalogException("SEPARATION_OF_DUTIES_VIOLATION"), request);

    assertThat(response.getStatusCode().value()).isEqualTo(403);
    assertThat(response.getBody()).containsEntry("errorCode", "SEPARATION_OF_DUTIES_VIOLATION");
  }

  @Test
  void investorOutboxOmitsSellerIdsFromEvent() {
    UUID tenantId = UUID.randomUUID();
    UUID catalogId = UUID.randomUUID();
    UUID investorId = UUID.randomUUID();
    UUID versionId = UUID.randomUUID();
    CatalogRepository repository = mock(CatalogRepository.class);
    CatalogService service = new CatalogService(repository, mock(AuthorizationService.class));
    RequestContext.roles("CATALOG_ADMIN");
    InvestorCatalogDraftRequest request = fnma(List.of(new InvestorSellerServicerId("RETAIL", "123456", "654321")));
    InvestorCatalogDraftResponse response = new InvestorCatalogDraftResponse(investorId, versionId, CatalogStatus.DRAFT, new InvestorCatalogValidation(List.of(), List.of()));
    CatalogResponse before = new CatalogResponse(catalogId, 1, CatalogStatus.DRAFT, List.of(), List.of(), List.of(), List.of(), "before");
    CatalogResponse after = new CatalogResponse(catalogId, 2, CatalogStatus.DRAFT, List.of(), List.of(), List.of(), List.of(), "after");
    when(repository.idempotent(eq(tenantId), eq("inv-key"), eq(request), eq(InvestorCatalogDraftResponse.class), any())).thenAnswer(invocation -> {
      @SuppressWarnings("unchecked")
      Supplier<InvestorCatalogDraftResponse> command = invocation.getArgument(4, Supplier.class);
      return command.get();
    });
    when(repository.currentCatalogId(tenantId)).thenReturn(catalogId);
    when(repository.current(tenantId)).thenReturn(before, after);
    when(repository.addInvestorCatalogDraft(tenantId, catalogId, request, "actor-1")).thenReturn(response);

    service.addInvestorCatalogDraft(tenantId, request, "inv-key", "actor-1", "corr-0204");

    ArgumentCaptor<CatalogEvent> events = ArgumentCaptor.forClass(CatalogEvent.class);
    verify(repository).event(events.capture());
    assertThat(events.getValue().eventType()).isEqualTo("InvestorCatalogChanged.v1");
    assertThat(events.getValue().payload()).containsEntry("investorCode", "FNMA");
    assertThat(events.getValue().payload().toString()).doesNotContain("123456", "654321");
  }

  private InvestorCatalogDraftRequest fnma(List<InvestorSellerServicerId> sellerIds) {
    return new InvestorCatalogDraftRequest("FNMA", "Federal National Mortgage Association", "AGENCY", "FANNIE_MAE", sellerIds,
        List.of("BEST_EFFORTS", "MANDATORY"), List.of("RETAIL"), true, CatalogStatus.DRAFT, START, null);
  }
}
