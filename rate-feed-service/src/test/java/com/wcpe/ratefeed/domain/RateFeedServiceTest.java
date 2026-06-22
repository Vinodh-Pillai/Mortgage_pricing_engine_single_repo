package com.wcpe.ratefeed.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import static com.wcpe.ratefeed.domain.RateFeedModels.*;

class RateFeedServiceTest {
  private final RateFeedRepository repository = mock(RateFeedRepository.class);
  private final RateFeedService service = TestRateFeedServices.create(repository);

  @AfterEach
  void clearRoles() { RequestContext.clear(); }

  @Test
  void createSessionAcceptsCsvMediaTypeWithParametersAndNormalizesDeterministically() {
    RequestContext.roles("RATE_FEED_UPLOAD");
    when(repository.json(any())).thenReturn("{}");
    when(repository.idempotent(any(), eq("key-1"), any(), eq(UploadSessionResponse.class), any())).thenAnswer(invocation -> {
      @SuppressWarnings("unchecked") Supplier<UploadSessionResponse> command = invocation.getArgument(4);
      return command.get();
    });

    UploadSessionResponse response = service.createSession(tenant(), validRequest("RateSheet.csv", "Text/CSV; Charset=UTF-8", "MANUAL_UPLOAD", "synthetic note"), "key-1", "actor", "corr");

    assertThat(response.status()).isEqualTo("OPEN");
    verify(repository).saveSession(any(), any(), any(), eq("actor"), eq("corr"), any(), eq(response));
  }

  @Test
  void createSessionRejectsFormulaInjectionMetadata() {
    RequestContext.roles("RATE_FEED_UPLOAD");
    when(repository.idempotent(any(), any(), any(), eq(UploadSessionResponse.class), any())).thenAnswer(invocation -> {
      @SuppressWarnings("unchecked") Supplier<UploadSessionResponse> command = invocation.getArgument(4);
      return command.get();
    });

    assertThatThrownBy(() -> service.createSession(tenant(), validRequest("=cmd.csv", "text/csv", "MANUAL_UPLOAD", "synthetic note"), "key-2", "actor", "corr"))
        .isInstanceOf(RateFeedException.class)
        .satisfies(ex -> assertRateFeedException(ex, "FORMULA_INJECTION_RISK", HttpStatus.BAD_REQUEST));
  }

  @Test
  void createSessionRequiresUploadRoleBeforePersistence() {
    assertThatThrownBy(() -> service.createSession(tenant(), validRequest("RateSheet.csv", "text/csv", "MANUAL_UPLOAD", "note"), "key-3", "actor", "corr"))
        .isInstanceOf(RateFeedException.class)
        .satisfies(ex -> assertRateFeedException(ex, "ACCESS_DENIED", HttpStatus.FORBIDDEN));
    verifyNoInteractions(repository);
  }

  @Test
  void completeRejectsMalwareScanFailureBeforeSessionMutation() {
    RequestContext.roles("RATE_FEED_UPLOAD");
    when(repository.idempotent(any(), any(), any(), eq(CompleteUploadResponse.class), any())).thenAnswer(invocation -> {
      @SuppressWarnings("unchecked") Supplier<CompleteUploadResponse> command = invocation.getArgument(4);
      return command.get();
    });

    CompleteUploadRequest request = new CompleteUploadRequest("a".repeat(64), "local://synthetic/object.csv", "scan-1", "FAILED");
    assertThatThrownBy(() -> service.complete(tenant(), UUID.randomUUID(), request, "key-4", "actor", "corr"))
        .isInstanceOf(RateFeedException.class)
        .satisfies(ex -> assertRateFeedException(ex, "MALWARE_SCAN_FAILED", HttpStatus.UNPROCESSABLE_ENTITY));
    verify(repository, never()).session(any(), any());
  }

  @Test
  void completeIdempotencyIdentityIncludesUploadSessionRouteParameter() {
    RequestContext.roles("RATE_FEED_UPLOAD");
    UUID sessionId = UUID.fromString("00000000-0000-0000-0000-000000000301");
    CompleteUploadRequest request = new CompleteUploadRequest("b".repeat(64), "local://synthetic/object.csv", "scan-1", "CLEAN");
    when(repository.idempotent(any(), eq("key-route"), any(), eq(CompleteUploadResponse.class), any())).thenReturn(
        new CompleteUploadResponse(UUID.randomUUID(), "UPLOADED", UUID.randomUUID(), UUID.randomUUID(), java.util.Map.of(), "hash"));

    service.complete(tenant(), sessionId, request, "key-route", "actor", "corr");

    ArgumentCaptor<Object> identity = ArgumentCaptor.forClass(Object.class);
    verify(repository).idempotent(any(), eq("key-route"), identity.capture(), eq(CompleteUploadResponse.class), any());
    assertThat(identity.getValue().toString()).contains("completeUploadSession", sessionId.toString());
  }

  @Test
  void unsupportedMediaIsDeterministic() {
    RequestContext.roles("RATE_FEED_UPLOAD");
    when(repository.idempotent(any(), any(), any(), eq(UploadSessionResponse.class), any())).thenAnswer(invocation -> {
      @SuppressWarnings("unchecked") Supplier<UploadSessionResponse> command = invocation.getArgument(4);
      return command.get();
    });

    assertThatThrownBy(() -> service.createSession(tenant(), validRequest("RateSheet.csv", "application/pdf", "MANUAL_UPLOAD", "note"), "key-5", "actor", "corr"))
        .isInstanceOf(RateFeedException.class)
        .satisfies(ex -> assertRateFeedException(ex, "UNSUPPORTED_MEDIA_TYPE", HttpStatus.UNSUPPORTED_MEDIA_TYPE));
  }

  @Test
  void completeRejectsRemoteSignedStorageObjectReferencesBeforeLookup() {
    RequestContext.roles("RATE_FEED_UPLOAD");
    when(repository.idempotent(any(), any(), any(), eq(CompleteUploadResponse.class), any())).thenAnswer(invocation -> {
      @SuppressWarnings("unchecked") Supplier<CompleteUploadResponse> command = invocation.getArgument(4);
      return command.get();
    });

    CompleteUploadRequest request = new CompleteUploadRequest("c".repeat(64), "https://object-store.local/rate.csv?sig=synthetic", "scan-1", "CLEAN");
    assertThatThrownBy(() -> service.complete(tenant(), UUID.randomUUID(), request, "key-6", "actor", "corr"))
        .isInstanceOf(RateFeedException.class)
        .satisfies(ex -> assertRateFeedException(ex, "INVALID_OBJECT_REFERENCE", HttpStatus.BAD_REQUEST));
    verify(repository, never()).session(any(), any());
  }

  @Test
  void completeRejectsControlCharactersAndOverlongObjectReferencesBeforeLookup() {
    RequestContext.roles("RATE_FEED_UPLOAD");
    when(repository.idempotent(any(), any(), any(), eq(CompleteUploadResponse.class), any())).thenAnswer(invocation -> {
      @SuppressWarnings("unchecked") Supplier<CompleteUploadResponse> command = invocation.getArgument(4);
      return command.get();
    });

    CompleteUploadRequest controlCharacter = new CompleteUploadRequest("d".repeat(64), "local://synthetic/object\n.csv", "scan-1", "CLEAN");
    assertThatThrownBy(() -> service.complete(tenant(), UUID.randomUUID(), controlCharacter, "key-7", "actor", "corr"))
        .isInstanceOf(RateFeedException.class)
        .satisfies(ex -> assertRateFeedException(ex, "INVALID_OBJECT_REFERENCE", HttpStatus.BAD_REQUEST));

    CompleteUploadRequest overlong = new CompleteUploadRequest("e".repeat(64), "local://synthetic/" + "a".repeat(513), "scan-1", "CLEAN");
    assertThatThrownBy(() -> service.complete(tenant(), UUID.randomUUID(), overlong, "key-8", "actor", "corr"))
        .isInstanceOf(RateFeedException.class)
        .satisfies(ex -> assertRateFeedException(ex, "OBJECT_REFERENCE_TOO_LONG", HttpStatus.BAD_REQUEST));
    verify(repository, never()).session(any(), any());
  }

  @Test
  void pipelineStatusFailsClosedWithoutDurableProjectionStore() {
    RequestContext.roles("RATE_FEED_VIEW");

    assertThatThrownBy(() -> service.pipelineStatus(tenant()))
        .isInstanceOf(RateFeedException.class)
        .satisfies(ex -> assertRateFeedException(ex, "PIPELINE_PROJECTION_STORE_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE));
  }

  private static void assertRateFeedException(Throwable ex, String code, HttpStatus status) {
    RateFeedException rateFeedException = (RateFeedException) ex;
    assertThat(rateFeedException.code()).isEqualTo(code);
    assertThat(rateFeedException.status()).isEqualTo(status);
  }

  private static UUID tenant() { return UUID.fromString("00000000-0000-0000-0000-000000000100"); }

  private static UploadSessionRequest validRequest(String fileName, String contentType, String sourceType, String notes) {
    return new UploadSessionRequest(
        UUID.fromString("00000000-0000-0000-0000-000000000201"),
        UUID.fromString("00000000-0000-0000-0000-000000000202"),
        UUID.fromString("00000000-0000-0000-0000-000000000203"),
        sourceType,
        Instant.parse("2026-05-17T12:00:00Z"),
        "America/New_York",
        fileName,
        contentType,
        1024,
        null,
        notes);
  }
}
