package com.wcpe.catalog.domain;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.wcpe.catalog.auth.AuthorizationService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

class ConventionalProductDefinitionPolicyTest {
  @AfterEach
  void clearRequestContext() {
    RequestContext.clear();
  }

  @Test
  void rejectsArmWithoutIndex() {
    assertThatThrownBy(() -> ConventionalProductDefinitionPolicy.validateStructure(new ConventionalProductDraftRequest(
        "CONV_ARM_5_6", "Conventional ARM 5/6", "CONVENTIONAL_ARM", List.of("FNMA"), List.of("RETAIL"), List.of(360), "ARM", null, 60, 6,
        List.of("SINGLE_FAMILY"), List.of("PRIMARY_RESIDENCE"), List.of("PURCHASE"), List.of("TX"), new BigDecimal("50000.00"), new BigDecimal("806500.00"), Instant.parse("2026-01-01T00:00:00Z"), null)))
        .isInstanceOf(CatalogException.class)
        .hasMessage("INVALID_ARM_STRUCTURE");
  }

  @Test
  void rejectsFixedWithArmPeriods() {
    assertThatThrownBy(() -> ConventionalProductDefinitionPolicy.validateStructure(new ConventionalProductDraftRequest(
        "CONV_FIXED_BAD", "Conventional Fixed Bad", "CONVENTIONAL_FIXED", List.of("FNMA"), List.of("RETAIL"), List.of(360), "FIXED", "SOFR", 60, 6,
        List.of("SINGLE_FAMILY"), List.of("PRIMARY_RESIDENCE"), List.of("PURCHASE"), List.of("TX"), new BigDecimal("50000.00"), new BigDecimal("806500.00"), Instant.parse("2026-01-01T00:00:00Z"), null)))
        .isInstanceOf(CatalogException.class)
        .hasMessage("INVALID_FIXED_STRUCTURE");
  }

  @Test
  void acceptsFixedThirtyWithScaleTwoLoanAmounts() {
    ConventionalProductDefinitionPolicy.validateStructure(new ConventionalProductDraftRequest(
        "CONV_FIXED_30", "Conventional Fixed 30 Year", "CONVENTIONAL_FIXED", List.of("FNMA"), List.of("RETAIL"), List.of(360), "FIXED", null, null, null,
        List.of("SINGLE_FAMILY"), List.of("PRIMARY_RESIDENCE"), List.of("PURCHASE"), List.of("TX"), new BigDecimal("50000.00"), new BigDecimal("806500.00"), Instant.parse("2026-01-01T00:00:00Z"), null));
  }

  @Test
  void conventionalDraftRequiresIdempotencyKeyBeforeMutation() {
    CatalogRepository repository = mock(CatalogRepository.class);
    CatalogService service = new CatalogService(repository, mock(AuthorizationService.class));
    RequestContext.roles("CATALOG_ADMIN");

    assertThatThrownBy(() -> service.addConventionalProductDraft(UUID.randomUUID(), fixedThirtyRequest(), " ", "actor-1", "corr-0202"))
        .isInstanceOf(CatalogException.class)
        .hasMessage("IDEMPOTENCY_KEY_REQUIRED");
    verifyNoInteractions(repository);
  }

  @Test
  void conventionalResolveErrorsReturnStoryContractShape() {
    CatalogController controller = new CatalogController(mock(CatalogService.class), mock(DomainRepository.class), mock(AuthorizationService.class));
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Correlation-Id", "corr-0202");

    ResponseEntity<Map<String, Object>> response = controller.error(new CatalogException("NO_ELIGIBLE_CONVENTIONAL_PRODUCT"), request);

    assertThat(response.getStatusCode().value()).isEqualTo(422);
    assertThat(response.getBody()).containsEntry("errorCode", "NO_ELIGIBLE_CONVENTIONAL_PRODUCT");
    assertThat(response.getBody()).containsEntry("correlationId", "corr-0202");
    assertThat((List<?>) response.getBody().get("fieldErrors")).isNotEmpty();
  }

  @Test
  void TermAmortizationContractTest_unsupportedTerm422() {
    CatalogController controller = new CatalogController(mock(CatalogService.class), mock(DomainRepository.class), mock(AuthorizationService.class));
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Correlation-Id", "c-0206");

    ResponseEntity<Map<String, Object>> response = controller.error(new CatalogException("TERM_AMORTIZATION_NOT_SUPPORTED"), request);

    assertThat(response.getStatusCode().value()).isEqualTo(422);
    assertThat(response.getBody()).containsEntry("errorCode", "TERM_AMORTIZATION_NOT_SUPPORTED");
    assertThat(response.getBody()).containsEntry("correlationId", "c-0206");
    assertThat((List<?>) response.getBody().get("fieldErrors")).singleElement().satisfies(error ->
        assertThat(((Map<?, ?>) error).get("code")).isEqualTo("UNSUPPORTED_TERM"));
  }

  @Test
  void publishEmitsConventionalProductDefinitionPublishedEventPayload() {
    UUID tenantId = UUID.randomUUID();
    UUID catalogId = UUID.randomUUID();
    UUID definitionId = UUID.randomUUID();
    UUID versionId = UUID.randomUUID();
    UUID referencedVersionId = UUID.randomUUID();
    CatalogRepository repository = mock(CatalogRepository.class);
    CatalogService service = new CatalogService(repository, mock(AuthorizationService.class));
    RequestContext.roles("CATALOG_ADMIN");
    CatalogResponse before = new CatalogResponse(catalogId, 1, CatalogStatus.APPROVED, List.of(), List.of(), List.of(), List.of(), "before");
    CatalogResponse after = new CatalogResponse(catalogId, 2, CatalogStatus.PUBLISHED, List.of(), List.of(), List.of(), List.of(), "after");
    Map<String, Object> publishedPayload = Map.of(
        "productDefinitionId", definitionId.toString(),
        "productVersionId", versionId.toString(),
        "productCode", "CONV_FIXED_30",
        "status", "PUBLISHED",
        "effectiveWindow", Map.of("start", "2026-01-01T00:00:00Z"),
        "referencedVersionIds", List.of(referencedVersionId.toString()),
        "configHash", "sha256:test");
    when(repository.idempotent(eq(tenantId), eq("pub-key"), any(), eq(CatalogResponse.class), any())).thenAnswer(invocation -> {
      @SuppressWarnings("unchecked")
      Supplier<CatalogResponse> command = invocation.getArgument(4, Supplier.class);
      return command.get();
    });
    when(repository.currentCatalogId(tenantId)).thenReturn(catalogId);
    when(repository.current(tenantId)).thenReturn(before);
    when(repository.active(tenantId)).thenReturn(after);
    when(repository.publishedConventionalProductDefinitions(tenantId, catalogId)).thenReturn(List.of(publishedPayload));

    service.publish(tenantId, new PublishCatalogRequest("conventional publish", java.time.LocalDate.of(2026, 1, 1)), "pub-key", "actor-3", "corr-0202");

    ArgumentCaptor<CatalogEvent> events = ArgumentCaptor.forClass(CatalogEvent.class);
    verify(repository, times(2)).event(events.capture());
    CatalogEvent conventionalPublished = events.getAllValues().stream()
        .filter(event -> "ConventionalProductDefinitionPublished.v1".equals(event.eventType()))
        .findFirst()
        .orElseThrow();
    assertThat(conventionalPublished.payload()).containsEntry("productDefinitionId", definitionId.toString());
    assertThat(conventionalPublished.payload()).containsEntry("productVersionId", versionId.toString());
    assertThat(conventionalPublished.payload()).containsEntry("productCode", "CONV_FIXED_30");
    assertThat(conventionalPublished.payload()).containsEntry("status", "PUBLISHED");
    assertThat(conventionalPublished.payload()).containsEntry("referencedVersionIds", List.of(referencedVersionId.toString()));
    assertThat(conventionalPublished.payload()).containsEntry("configHash", "sha256:test");
  }

  private ConventionalProductDraftRequest fixedThirtyRequest() {
    return new ConventionalProductDraftRequest(
        "CONV_FIXED_30", "Conventional Fixed 30 Year", "CONVENTIONAL_FIXED", List.of("FNMA"), List.of("RETAIL"), List.of(360), "FIXED", null, null, null,
        List.of("SINGLE_FAMILY"), List.of("PRIMARY_RESIDENCE"), List.of("PURCHASE"), List.of("TX"), new BigDecimal("50000.00"), new BigDecimal("806500.00"), Instant.parse("2026-01-01T00:00:00Z"), null);
  }
}
