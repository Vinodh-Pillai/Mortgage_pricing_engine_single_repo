package com.wcpe.ratefeed.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import static com.wcpe.ratefeed.domain.RateFeedModels.*;

class PartnerFeedApiTest {
  private final RateFeedRepository repository = mock(RateFeedRepository.class);
  private final RateFeedService service = TestRateFeedServices.create(repository);

  @Test
  void partnerAuthFailsClosedBeforeMappingLookup() {
    assertThatThrownBy(() -> service.submitPartnerStructured("tenant-ext", structuredRequest("v1"), new RateFeedService.PartnerHeaders("idem-1", null, null, "corr-1", false)))
        .isInstanceOf(RateFeedException.class)
        .satisfies(ex -> assertRateFeedException(ex, "PARTNER_AUTH_FAILED", HttpStatus.UNAUTHORIZED));

    verifyNoInteractions(repository);
  }

  @Test
  void externalKeyMappingFailsClosedWhenPartnerIsNotAuthorized() {
    when(repository.partnerIntegration("tenant-ext", "investor-ext", "channel-ext", "csv")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.submitPartnerStructured("tenant-ext", structuredRequest("v1"), headers("idem-2")))
        .isInstanceOf(RateFeedException.class)
        .satisfies(ex -> assertRateFeedException(ex, "PARTNER_NOT_AUTHORIZED_FOR_INVESTOR", HttpStatus.FORBIDDEN));
  }

  @Test
  void structuredSubmissionRejectsUnsupportedSchemaVersion() {
    when(repository.partnerIntegration("tenant-ext", "investor-ext", "channel-ext", "csv")).thenReturn(Optional.of(integration("v1")));

    assertThatThrownBy(() -> service.submitPartnerStructured("tenant-ext", structuredRequest("v2"), headers("idem-3")))
        .isInstanceOf(RateFeedException.class)
        .satisfies(ex -> assertRateFeedException(ex, "SCHEMA_VERSION_UNSUPPORTED", HttpStatus.UNPROCESSABLE_ENTITY));
  }

  @Test
  void validFileSubmissionUsesTenantScopedIdempotencyAndReturnsAcceptedStatusLink() {
    RateFeedRepository.InvestorFeedIntegrationRow integration = integration("v1");
    PartnerSubmissionResponse response = new PartnerSubmissionResponse(UUID.randomUUID(), UUID.randomUUID(), "RECEIVED", "/api/v1/partner/rate-feeds/tenant-ext/submissions/00000000-0000-0000-0000-000000000001", Instant.parse("2026-06-05T00:00:00Z"), "sha256:abc");
    when(repository.partnerIntegration("tenant-ext", "investor-ext", "channel-ext", "csv")).thenReturn(Optional.of(integration));
    when(repository.json(any())).thenReturn("{}");
    when(repository.idempotent(eq(integration.tenantId()), eq("idem-4"), any(), eq(PartnerSubmissionResponse.class), any())).thenAnswer(invocation -> {
      @SuppressWarnings("unchecked") Supplier<PartnerSubmissionResponse> command = invocation.getArgument(4);
      command.get();
      return response;
    });
    when(repository.savePartnerSubmission(eq(integration), any(), any(), eq("FILE"), eq("RECEIVED"), eq("v1"), eq("rates.csv"), eq("text/csv"), eq(22L), any(), eq("partner-subject"), eq("corr-4"))).thenReturn(response);

    PartnerSubmissionResponse actual = service.submitPartnerFile("tenant-ext", fileMetadata("v1"),
        new MockMultipartFile("file", "rates.csv", "text/csv", "note_rate,lock_period\n6.5,30".getBytes()), headers("idem-4"));

    assertThat(actual.status()).isEqualTo("RECEIVED");
    assertThat(actual.statusUrl()).contains("/api/v1/partner/rate-feeds/tenant-ext/submissions/");
    verify(repository).idempotent(eq(integration.tenantId()), eq("idem-4"), any(), eq(PartnerSubmissionResponse.class), any());
  }

  @Test
  void rateLimitHeaderFailsWithStoryErrorCodeBeforeMappingLookup() {
    assertThatThrownBy(() -> service.submitPartnerStructured("tenant-ext", structuredRequest("v1"), new RateFeedService.PartnerHeaders("idem-5", "partner-client", "partner-subject", "corr-5", true)))
        .isInstanceOf(RateFeedException.class)
        .satisfies(ex -> assertRateFeedException(ex, "RATE_LIMIT_EXCEEDED", HttpStatus.TOO_MANY_REQUESTS));

    verifyNoInteractions(repository);
  }

  @Test
  void fileSubmissionRejectsUnsupportedFileFormat() {
    assertThatThrownBy(() -> service.submitPartnerFile("tenant-ext", fileMetadata("v1"),
        new MockMultipartFile("file", "rates.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "bad".getBytes()), headers("idem-6")))
        .isInstanceOf(RateFeedException.class)
        .satisfies(ex -> assertRateFeedException(ex, "FEED_FORMAT_NOT_ALLOWED", HttpStatus.UNSUPPORTED_MEDIA_TYPE));

    verifyNoInteractions(repository);
  }

  @Test
  void statusLookupIsScopedByTenantExternalKey() {
    UUID submissionId = UUID.randomUUID();
    PartnerSubmissionStatusResponse status = new PartnerSubmissionStatusResponse(submissionId, UUID.randomUUID(), "VALIDATION_PENDING", "tenant-ext", "investor-ext", "channel-ext", "csv", "v1", Instant.parse("2026-06-05T00:00:00Z"), "sha256:abc");
    when(repository.partnerSubmissionStatus("tenant-ext", submissionId)).thenReturn(status);

    PartnerSubmissionStatusResponse actual = service.partnerSubmissionStatus("tenant-ext", submissionId, headers("idem-7"));

    assertThat(actual.status()).isEqualTo("VALIDATION_PENDING");
    verify(repository).partnerSubmissionStatus("tenant-ext", submissionId);
  }

  @Test
  void structuredSuccessRecordsValidationPendingStatus() {
    RateFeedRepository.InvestorFeedIntegrationRow integration = integration("v1");
    PartnerSubmissionResponse response = new PartnerSubmissionResponse(UUID.randomUUID(), UUID.randomUUID(), "VALIDATION_PENDING", "/api/v1/partner/rate-feeds/tenant-ext/submissions/00000000-0000-0000-0000-000000000001", Instant.parse("2026-06-05T00:00:00Z"), "sha256:def");
    when(repository.partnerIntegration("tenant-ext", "investor-ext", "channel-ext", "csv")).thenReturn(Optional.of(integration));
    when(repository.json(any())).thenReturn("{}");
    when(repository.idempotent(eq(integration.tenantId()), eq("idem-8"), any(), eq(PartnerSubmissionResponse.class), any())).thenAnswer(invocation -> {
      @SuppressWarnings("unchecked") Supplier<PartnerSubmissionResponse> command = invocation.getArgument(4);
      command.get();
      return response;
    });
    when(repository.savePartnerSubmission(eq(integration), any(), any(), eq("STRUCTURED"), eq("VALIDATION_PENDING"), eq("v1"), eq("structured-submission.json"), eq("application/json"), anyLong(), any(), eq("partner-subject"), eq("corr-4"))).thenReturn(response);

    PartnerSubmissionResponse actual = service.submitPartnerStructured("tenant-ext", structuredRequest("v1"), headers("idem-8"));

    assertThat(actual.status()).isEqualTo("VALIDATION_PENDING");
  }

  @Test
  void idempotencyConflictIsPropagatedWithStoryErrorCode() {
    when(repository.partnerIntegration("tenant-ext", "investor-ext", "channel-ext", "csv")).thenReturn(Optional.of(integration("v1")));
    when(repository.idempotent(any(), eq("idem-9"), any(), eq(PartnerSubmissionResponse.class), any()))
        .thenThrow(new RateFeedException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", "Idempotency key reused with a different request."));

    assertThatThrownBy(() -> service.submitPartnerStructured("tenant-ext", structuredRequest("v1"), headers("idem-9")))
        .isInstanceOf(RateFeedException.class)
        .satisfies(ex -> assertRateFeedException(ex, "IDEMPOTENCY_CONFLICT", HttpStatus.CONFLICT));
  }

  private static RateFeedService.PartnerHeaders headers(String idempotencyKey) {
    return new RateFeedService.PartnerHeaders(idempotencyKey, "partner-client", "partner-subject", "corr-4", false);
  }

  private static PartnerStructuredSubmissionRequest structuredRequest(String schemaVersion) {
    return new PartnerStructuredSubmissionRequest("investor-ext", "channel-ext", "csv", schemaVersion, Instant.parse("2026-06-05T00:00:00Z"), "UTC", Map.of("source", "partner"), List.of(Map.of("note_rate", "6.5", "lock_period", "30", "base_price", "100.125")));
  }

  private static PartnerFileSubmissionMetadata fileMetadata(String schemaVersion) {
    return new PartnerFileSubmissionMetadata("investor-ext", "channel-ext", "csv", schemaVersion, Instant.parse("2026-06-05T00:00:00Z"), "UTC", "partner-system");
  }

  private static RateFeedRepository.InvestorFeedIntegrationRow integration(String schemaVersion) {
    return new RateFeedRepository.InvestorFeedIntegrationRow(
        UUID.fromString("00000000-0000-0000-0000-000000000100"),
        UUID.fromString("00000000-0000-0000-0000-000000000201"),
        UUID.fromString("00000000-0000-0000-0000-000000000202"),
        UUID.fromString("00000000-0000-0000-0000-000000000203"),
        "tenant-ext", "investor-ext", "channel-ext", "csv", schemaVersion);
  }

  private static void assertRateFeedException(Throwable ex, String code, HttpStatus status) {
    RateFeedException rateFeedException = (RateFeedException) ex;
    assertThat(rateFeedException.code()).isEqualTo(code);
    assertThat(rateFeedException.status()).isEqualTo(status);
  }
}
