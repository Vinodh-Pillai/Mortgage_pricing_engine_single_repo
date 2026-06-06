package com.wcpe.catalog.domain;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.wcpe.catalog.auth.AuthorizationService;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ChannelTaxonomyPolicyTest {
  private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

  @Test
  void rejectsDuplicateLosMapping() {
    ChannelTaxonomyDraftRequest request = wholesale(List.of(new ChannelSourceSystemMapping("LOS", "WHL"), new ChannelSourceSystemMapping("LOS", "WHL")));

    assertThatThrownBy(() -> ChannelTaxonomyPolicy.validateDraft(request, false, (source, value) -> false))
        .isInstanceOf(CatalogException.class)
        .hasMessage("DUPLICATE_SOURCE_MAPPING");
  }

  @Test
  void rejectsNonBaselineChannelCode() {
    ChannelTaxonomyDraftRequest request = new ChannelTaxonomyDraftRequest("BROKER", "Broker", "Broker", List.of(), List.of("LOS"), false, "BROKER_STANDARD", START, null);

    assertThatThrownBy(() -> ChannelTaxonomyPolicy.validateDraft(request, false, (source, value) -> false))
        .isInstanceOf(CatalogException.class)
        .hasMessage("CHANNEL_CODE_NOT_BASELINE");
  }

  @Test
  void acceptsStoryBaselineCodesAndMappings() {
    assertThat(ChannelTaxonomyPolicy.BASELINE_CODES).containsExactlyInAnyOrder("RETAIL", "WHOLESALE", "CORRESPONDENT", "CONSUMER_DIRECT", "PARTNER_API");
    assertThatCode(() -> ChannelTaxonomyPolicy.validateDraft(wholesale(List.of(new ChannelSourceSystemMapping("LOS", "WHL"))), false, (source, value) -> false)).doesNotThrowAnyException();
  }

  @Test
  void publishEmitsChannelCatalogChangedEventPayload() {
    UUID tenantId = UUID.randomUUID();
    UUID catalogId = UUID.randomUUID();
    UUID channelId = UUID.randomUUID();
    UUID versionId = UUID.randomUUID();
    CatalogRepository repository = mock(CatalogRepository.class);
    CatalogService service = new CatalogService(repository, mock(AuthorizationService.class));
    RequestContext.roles("CATALOG_ADMIN");
    ChannelTaxonomyDraftRequest request = wholesale(List.of(new ChannelSourceSystemMapping("LOS", "WHL")));
    ChannelTaxonomyDraftResponse response = new ChannelTaxonomyDraftResponse(channelId, versionId, CatalogStatus.DRAFT, new ChannelTaxonomyValidation(List.of(), List.of()));
    CatalogResponse before = new CatalogResponse(catalogId, 1, CatalogStatus.DRAFT, List.of(), List.of(), List.of(), List.of(), "before");
    CatalogResponse after = new CatalogResponse(catalogId, 2, CatalogStatus.DRAFT, List.of(), List.of(), List.of(), List.of(), "after");
    when(repository.idempotent(eq(tenantId), eq("channel-key"), eq(request), eq(ChannelTaxonomyDraftResponse.class), any())).thenAnswer(invocation -> {
      @SuppressWarnings("unchecked")
      Supplier<ChannelTaxonomyDraftResponse> command = invocation.getArgument(4, Supplier.class);
      return command.get();
    });
    when(repository.currentCatalogId(tenantId)).thenReturn(catalogId);
    when(repository.current(tenantId)).thenReturn(before, after);
    when(repository.addChannelTaxonomyDraft(tenantId, catalogId, request, "actor-1")).thenReturn(response);

    service.addChannelTaxonomyDraft(tenantId, request, "channel-key", "actor-1", "corr-0203");

    ArgumentCaptor<CatalogEvent> events = ArgumentCaptor.forClass(CatalogEvent.class);
    verify(repository).event(events.capture());
    assertThat(events.getValue().eventType()).isEqualTo("ChannelCatalogChanged.v1");
    assertThat(events.getValue().payload()).containsEntry("channelCode", "WHOLESALE");
    verify(repository).audit(eq(tenantId), eq(catalogId), eq("CHANNEL_CATALOG_CHANGED"), nullable(String.class), eq(before), eq(after), anyMap(), eq("actor-1"), eq("corr-0203"), eq("channel-key"));
  }

  private ChannelTaxonomyDraftRequest wholesale(List<ChannelSourceSystemMapping> mappings) {
    return new ChannelTaxonomyDraftRequest("WHOLESALE", "Wholesale Broker", "Third-party broker submissions", mappings, List.of("LOS", "PARTNER_API"), true, "WHOLESALE_STANDARD", START, null);
  }
}
