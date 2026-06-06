package com.wcpe.ratefeed.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.wcpe.ratefeed.domain.RateFeedModels.*;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * PII-04-S01: Tests for the batch create command path (complete upload session).
 * Verifies that completing an upload session creates a batch record with UPLOADED status.
 */
class RateFeedBatchCreateCommandTest {

  private final RateFeedRepository repository = mock(RateFeedRepository.class);
  private final RateFeedService service = TestRateFeedServices.create(repository);

  @AfterEach
  void clearRoles() { RequestContext.clear(); }

  @Test
  void createBatchRequiresUploadRole() {
    // Without role, complete upload should fail with FORBIDDEN
    assertThatThrownBy(() -> service.complete(tenant(), session(), validCompleteRequest(), "idem-1", "actor", "corr"))
        .isInstanceOf(RateFeedException.class)
        .satisfies(ex -> {
          RateFeedException e = (RateFeedException) ex;
          assertThat(e.code()).isEqualTo("ACCESS_DENIED");
          assertThat(e.status()).isEqualTo(HttpStatus.FORBIDDEN);
        });
    verifyNoInteractions(repository);
  }

  @Test
  void createBatchRequiresIdempotencyKey() {
    RequestContext.roles("RATE_FEED_UPLOAD");
    when(repository.idempotent(any(), isNull(), any(), eq(CompleteUploadResponse.class), any()))
        .thenAnswer(invocation -> {
          String key = invocation.getArgument(1);
          if (key == null || key.isBlank()) {
            throw new RateFeedException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key is required.");
          }
          return null;
        });
    assertThatThrownBy(() -> service.complete(tenant(), session(), validCompleteRequest(), null, "actor", "corr"))
        .isInstanceOf(RateFeedException.class)
        .satisfies(ex -> {
          RateFeedException e = (RateFeedException) ex;
          assertThat(e.code()).isEqualTo("IDEMPOTENCY_KEY_REQUIRED");
          assertThat(e.status()).isEqualTo(HttpStatus.BAD_REQUEST);
        });
  }

  @Test
  void createBatchRequiresValidFileHash() {
    RequestContext.roles("RATE_FEED_UPLOAD");
    when(repository.idempotent(any(), any(), any(), eq(CompleteUploadResponse.class), any())).thenAnswer(invocation -> {
      @SuppressWarnings("unchecked") Supplier<CompleteUploadResponse> command = invocation.getArgument(4);
      return command.get();
    });

    CompleteUploadRequest badHash = new CompleteUploadRequest("not-a-hash", "local://synthetic/object.csv", "scan-1", "CLEAN");
    assertThatThrownBy(() -> service.complete(tenant(), session(), badHash, "idem-2", "actor", "corr"))
        .isInstanceOf(RateFeedException.class)
        .satisfies(ex -> {
          RateFeedException e = (RateFeedException) ex;
          assertThat(e.code()).isEqualTo("INVALID_FILE_HASH");
        });
    verify(repository, never()).session(any(), any());
  }

  @Test
  void createBatchRequiresStorageObjectId() {
    RequestContext.roles("RATE_FEED_UPLOAD");
    when(repository.idempotent(any(), any(), any(), eq(CompleteUploadResponse.class), any())).thenAnswer(invocation -> {
      @SuppressWarnings("unchecked") Supplier<CompleteUploadResponse> command = invocation.getArgument(4);
      return command.get();
    });

    CompleteUploadRequest noStorage = new CompleteUploadRequest("a".repeat(64), null, "scan-1", "CLEAN");
    assertThatThrownBy(() -> service.complete(tenant(), session(), noStorage, "idem-3", "actor", "corr"))
        .isInstanceOf(RateFeedException.class)
        .satisfies(ex -> {
          RateFeedException e = (RateFeedException) ex;
          assertThat(e.code()).isEqualTo("STORAGE_OBJECT_REQUIRED");
        });
  }

  @Test
  void completeUploadSessionReturnsUploadedStatus() {
    RequestContext.roles("RATE_FEED_UPLOAD");
    UUID sessionId = session();
    CompleteUploadRequest request = validCompleteRequest();

    when(repository.idempotent(any(), eq("idem-ok"), any(), eq(CompleteUploadResponse.class), any())).thenAnswer(invocation -> {
      @SuppressWarnings("unchecked") Supplier<CompleteUploadResponse> command = invocation.getArgument(4);
      return command.get();
    });
    final int[] callCount = {0};
    when(repository.session(any(), any())).thenAnswer(invocation -> {
      callCount[0]++;
      return new RateFeedRepository.UploadSessionRow(
          sessionId, investor(), channel(), format(), "MANUAL_UPLOAD",
          Instant.parse("2026-06-01T12:00:00Z"), "America/New_York",
          "RateSheet.csv", "text/csv", 2048, null,
          "OPEN", Instant.now().plusSeconds(3600), "actor", "corr");
    });
    when(repository.complete(any(), any(), any(), any(), any(), any())).thenReturn(
        new CompleteUploadResponse(UUID.randomUUID(), "UPLOADED", UUID.randomUUID(), UUID.randomUUID(), java.util.Map.of(), "result-hash"));

    CompleteUploadResponse response = service.complete(tenant(), sessionId, request, "idem-ok", "actor", "corr");

    assertThat(response.status()).isEqualTo("UPLOADED");
    assertThat(response.batchId()).isNotNull();
    assertThat(response.rawFileId()).isNotNull();
  }

  @Test
  void completeRejectsExpiredSession() {
    RequestContext.roles("RATE_FEED_UPLOAD");
    UUID sessionId = session();
    CompleteUploadRequest request = validCompleteRequest();

    when(repository.idempotent(any(), any(), any(), eq(CompleteUploadResponse.class), any())).thenAnswer(invocation -> {
      @SuppressWarnings("unchecked") Supplier<CompleteUploadResponse> command = invocation.getArgument(4);
      return command.get();
    });
    when(repository.session(any(), any())).thenReturn(new RateFeedRepository.UploadSessionRow(
        sessionId, investor(), channel(), format(), "MANUAL_UPLOAD",
        Instant.parse("2026-06-01T12:00:00Z"), "America/New_York",
        "RateSheet.csv", "text/csv", 2048, null,
        "OPEN", Instant.parse("2020-01-01T00:00:00Z"), "actor", "corr"));

    assertThatThrownBy(() -> service.complete(tenant(), sessionId, request, "idem-4", "actor", "corr"))
        .isInstanceOf(RateFeedException.class)
        .satisfies(ex -> {
          RateFeedException e = (RateFeedException) ex;
          assertThat(e.code()).isEqualTo("UPLOAD_SESSION_EXPIRED");
        });
  }

  @Test
  void completeRejectsNonOpenSession() {
    RequestContext.roles("RATE_FEED_UPLOAD");
    UUID sessionId = session();
    CompleteUploadRequest request = validCompleteRequest();

    when(repository.idempotent(any(), any(), any(), eq(CompleteUploadResponse.class), any())).thenAnswer(invocation -> {
      @SuppressWarnings("unchecked") Supplier<CompleteUploadResponse> command = invocation.getArgument(4);
      return command.get();
    });
    when(repository.session(any(), any())).thenReturn(new RateFeedRepository.UploadSessionRow(
        sessionId, investor(), channel(), format(), "MANUAL_UPLOAD",
        Instant.parse("2026-06-01T12:00:00Z"), "America/New_York",
        "RateSheet.csv", "text/csv", 2048, null,
        "COMPLETED", Instant.now().plusSeconds(3600), "actor", "corr"));

    assertThatThrownBy(() -> service.complete(tenant(), sessionId, request, "idem-5", "actor", "corr"))
        .isInstanceOf(RateFeedException.class)
        .satisfies(ex -> {
          RateFeedException e = (RateFeedException) ex;
          assertThat(e.code()).isEqualTo("UPLOAD_SESSION_NOT_OPEN");
        });
  }

  private static UUID tenant() { return UUID.fromString("00000000-0000-0000-0000-000000000100"); }
  private static UUID session() { return UUID.fromString("00000000-0000-0000-0000-000000000301"); }
  private static UUID investor() { return UUID.fromString("00000000-0000-0000-0000-000000000201"); }
  private static UUID channel() { return UUID.fromString("00000000-0000-0000-0000-000000000202"); }
  private static UUID format() { return UUID.fromString("00000000-0000-0000-0000-000000000203"); }

  private static CompleteUploadRequest validCompleteRequest() {
    return new CompleteUploadRequest(
        "a".repeat(64),
        "local://synthetic/rate-sheet.csv",
        "scan-result-1",
        "CLEAN");
  }
}
