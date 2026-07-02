package com.wcpe.ratefeed.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.wcpe.ratefeed.domain.RateFeedModels.CompleteUploadRequest;
import com.wcpe.ratefeed.domain.RateFeedModels.CompleteUploadResponse;
import com.wcpe.ratefeed.domain.RateFeedModels.RateFeedException;
import com.wcpe.ratefeed.domain.RateFeedModels.RatesheetUploadRequest;
import com.wcpe.ratefeed.domain.RateFeedModels.RatesheetUploadResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

class RatesheetUploadEndpointTest {
  private final RateFeedRepository repository = mock(RateFeedRepository.class);
  private final RateFeedService service = TestRateFeedServices.create(repository);
  private final RateFeedController controller = new RateFeedController(service, true);

  @AfterEach
  void clearRoles() { RequestContext.clear(); }

  @Test
  void authorizedRegisteredFileUploadCreatesIntakeJobAndPendingTemplateStatus() {
    arrangePersistence();
    UUID batchId = UUID.fromString("00000000-0000-0000-0000-000000000301");
    when(repository.complete(any(), any(), any(), any(), any(), any())).thenReturn(new CompleteUploadResponse(
        batchId,
        "UPLOADED",
        UUID.fromString("00000000-0000-0000-0000-000000000302"),
        UUID.fromString("00000000-0000-0000-0000-000000000303"),
        Map.of("batch", "/api/v1/tenants/00000000-0000-0000-0000-000000000100/rate-feed-batches/" + batchId),
        "result-hash"));

    ResponseEntity<RatesheetUploadResponse> response = controller.createRatesheetUpload(tenant(), validRequest(null), authorizedHttp());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().intakeJobId()).isEqualTo(batchId);
    assertThat(response.getBody().detectedTemplateStatus()).isEqualTo("PENDING_TEMPLATE_SELECTION");
    assertThat(response.getBody().validationStatusUrl()).endsWith("/validation-report");
    verify(repository).saveSession(any(), any(), any(), eq("pricing-analyst"), eq("corr-upload"), any(), any());
    verify(repository).complete(any(), any(), any(), eq("pricing-analyst"), eq("corr-upload"), eq("upload-key:complete"));
  }

  @Test
  void csvContentWithoutRegisteredFileReferenceUsesSessionUploadUrlForDurableParserReference() throws Exception {
    arrangePersistence();
    UUID batchId = UUID.fromString("00000000-0000-0000-0000-000000000311");
    when(repository.complete(any(), any(), any(), any(), any(), any())).thenReturn(new CompleteUploadResponse(
        batchId,
        "UPLOADED",
        UUID.fromString("00000000-0000-0000-0000-000000000312"),
        UUID.fromString("00000000-0000-0000-0000-000000000313"),
        Map.of("batch", "/api/v1/tenants/00000000-0000-0000-0000-000000000100/rate-feed-batches/" + batchId),
        "result-hash"));
    String csv = "product,note_rate,lock_period,price,investor,channel\n"
        + "CONVENTIONAL,6.125,30,108.5," + investor() + "," + channel() + "\n";
    String fileSha256 = Hashing.sha256(csv).replaceFirst("^sha256:", "");
    RatesheetUploadRequest request = new RatesheetUploadRequest(investor(), channel(), format(), "MANUAL_UPLOAD",
        Instant.parse("2026-06-17T12:00:00Z"), "America/New_York", "Ratesheet.csv", "text/csv",
        csv.getBytes(java.nio.charset.StandardCharsets.UTF_8).length, null, fileSha256, null, "CLEAN",
        null, "CONVENTIONAL", "inline csv upload", csv);

    ResponseEntity<RatesheetUploadResponse> response = controller.createRatesheetUpload(tenant(), request, authorizedHttp());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody()).isNotNull();
    UUID uploadId = response.getBody().uploadId();
    Path storedCsv = Path.of("build/rate-feed-upload", uploadId + ".csv");
    try {
      assertThat(Files.readString(storedCsv)).isEqualTo(csv);
      var completedRequest = org.mockito.ArgumentCaptor.forClass(CompleteUploadRequest.class);
      verify(repository).complete(any(), any(), completedRequest.capture(), eq("pricing-analyst"), eq("corr-upload"), eq("upload-key:complete"));
      assertThat(completedRequest.getValue().storageObjectId()).isEqualTo("local://rate-feed-upload/" + uploadId);
      assertThat(completedRequest.getValue().csvContent()).isNull();
    } finally {
      Files.deleteIfExists(storedCsv);
    }
  }

  @Test
  void unauthorizedCallerIsRejectedBeforePersistence() {
    MockHttpServletRequest http = authorizedHttp();
    http.removeHeader("X-Roles");
    http.addHeader("X-Roles", "RATE_FEED_VIEW");

    assertThatThrownBy(() -> controller.createRatesheetUpload(tenant(), validRequest(null), http))
        .isInstanceOf(RateFeedException.class)
        .satisfies(ex -> {
          RateFeedException e = (RateFeedException) ex;
          assertThat(e.code()).isEqualTo("ACCESS_DENIED");
          assertThat(e.status()).isEqualTo(HttpStatus.FORBIDDEN);
        });
    verifyNoInteractions(repository);
  }

  @Test
  void unsupportedFileTypeIsRejectedBeforeIntakeCompletion() {
    arrangePersistence();
    RatesheetUploadRequest request = new RatesheetUploadRequest(investor(), channel(), format(), "MANUAL_UPLOAD",
        Instant.parse("2026-06-17T12:00:00Z"), "America/New_York", "Ratesheet.pdf", "application/pdf", 1024,
        "local://synthetic/ratesheets/ratesheet.pdf", sha256(), null, "CLEAN", null, "CONVENTIONAL", null);

    assertThatThrownBy(() -> controller.createRatesheetUpload(tenant(), request, authorizedHttp()))
        .isInstanceOf(RateFeedException.class)
        .satisfies(ex -> {
          RateFeedException e = (RateFeedException) ex;
          assertThat(e.code()).isEqualTo("UNSUPPORTED_MEDIA_TYPE");
          assertThat(e.status()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        });
    verify(repository, never()).complete(any(), any(), any(), any(), any(), any());
  }

  private void arrangePersistence() {
    when(repository.json(any())).thenReturn("{}");
    when(repository.idempotent(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
      @SuppressWarnings("unchecked") Supplier<Object> command = invocation.getArgument(4);
      return command.get();
    });
  }

  private static MockHttpServletRequest authorizedHttp() {
    MockHttpServletRequest http = new MockHttpServletRequest();
    http.addHeader("X-Roles", "RATE_FEED_UPLOAD");
    http.addHeader("X-Actor-Id", "pricing-analyst");
    http.addHeader("X-Correlation-Id", "corr-upload");
    http.addHeader("Idempotency-Key", "upload-key");
    return http;
  }

  private static RatesheetUploadRequest validRequest(String templateProfileId) {
    return new RatesheetUploadRequest(investor(), channel(), format(), "MANUAL_UPLOAD",
        Instant.parse("2026-06-17T12:00:00Z"), "America/New_York", "Ratesheet.csv", "text/csv", 1024,
        "local://synthetic/ratesheets/ratesheet.csv", sha256(), null, "CLEAN", templateProfileId, "CONVENTIONAL", "synthetic upload metadata");
  }

  private static UUID tenant() { return UUID.fromString("00000000-0000-0000-0000-000000000100"); }
  private static UUID investor() { return UUID.fromString("00000000-0000-0000-0000-000000000201"); }
  private static UUID channel() { return UUID.fromString("00000000-0000-0000-0000-000000000202"); }
  private static UUID format() { return UUID.fromString("00000000-0000-0000-0000-000000000203"); }
  private static String sha256() { return "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"; }
}
