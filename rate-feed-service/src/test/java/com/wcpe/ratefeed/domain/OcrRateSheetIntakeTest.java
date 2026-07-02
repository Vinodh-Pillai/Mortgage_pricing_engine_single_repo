package com.wcpe.ratefeed.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import static com.wcpe.ratefeed.domain.RateFeedModels.*;

class OcrRateSheetIntakeTest {
  private final RateFeedRepository repository = mock(RateFeedRepository.class);
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final RateFeedService service = TestRateFeedServices.create(repository, jdbc);

  @AfterEach
  void clearRoles() { RequestContext.clear(); }

  @Test
  void startsLocalOcrExtractionWithGovernedProfileAndMatchingFileHash() {
    RequestContext.roles("RATE_FEED_UPLOAD");
    UUID tenantId = tenant();
    UUID batchId = UUID.fromString("00000000-0000-0000-0000-000000000701");
    String fileHash = "a".repeat(64);
    UUID profileId = UUID.fromString("00000000-0000-0000-0000-000000000702");
    when(repository.batchParseSource(tenantId, batchId)).thenReturn(new RateFeedRepository.BatchParseSource(batchId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "UPLOADED", fileHash, "local://synthetic/src/test/resources/ratesheets/stored-valid.csv"));
    when(repository.json(any())).thenReturn("{}");
    when(repository.idempotent(eq(tenantId), eq("ocr-key"), any(), eq(OcrExtractionResponse.class), any())).thenAnswer(invocation -> {
      @SuppressWarnings("unchecked") Supplier<OcrExtractionResponse> command = invocation.getArgument(4);
      return command.get();
    });

    OcrExtractionResponse response = service.startOcrExtraction(tenantId, batchId, new StartOcrExtractionRequest(fileHash, profileId), "ocr-key", "actor", "corr");

    assertThat(response.batchId()).isEqualTo(batchId);
    assertThat(response.status()).isEqualTo("OCR_REVIEW_REQUIRED");
    verify(jdbc).update(startsWith("insert into rate_feed.ocr_extraction"), eq(tenantId), any(UUID.class), eq(batchId), eq(profileId), eq("local-adapter-contract-v1"), eq("OCR_REVIEW_REQUIRED"), eq(0), isNull(), eq(true), any(), anyString(), eq("ocr-key"));
    verify(repository).outbox(eq(tenantId), eq(batchId), eq("RateSheetOcrExtracted.v1"), eq(1), eq("actor"), eq("corr"), anyMap(), any());
  }

  @Test
  void startOcrExtractionRejectsMissingProfileFailClosed() {
    RequestContext.roles("RATE_FEED_UPLOAD");

    assertThatThrownBy(() -> service.startOcrExtraction(tenant(), UUID.randomUUID(), new StartOcrExtractionRequest("b".repeat(64), null), "key", "actor", "corr"))
        .isInstanceOf(RateFeedException.class)
        .satisfies(ex -> assertRateFeedException(ex, "OCR_PROFILE_NOT_FOUND", HttpStatus.UNPROCESSABLE_ENTITY));
    verifyNoInteractions(repository, jdbc);
  }

  @Test
  void reviewOcrCellPersistsHumanCorrectionAndRejectsFormulaText() {
    RequestContext.roles("RATE_FEED_OCR_REVIEW");
    UUID extractionId = UUID.fromString("00000000-0000-0000-0000-000000000703");
    UUID cellId = UUID.fromString("00000000-0000-0000-0000-000000000704");
    when(repository.json(any())).thenReturn("{}");
    when(jdbc.update(startsWith("update rate_feed.ocr_extracted_cell"), any(), any(), any(), any(), any())).thenReturn(1);

    OcrCellReviewResponse response = service.reviewOcrCell(tenant(), extractionId, cellId, new ReviewOcrCellRequest("6.250"), "reviewer", "corr");

    assertThat(response.status()).isEqualTo("REVIEWED");
    assertThat(response.cellId()).isEqualTo(cellId);

    assertThatThrownBy(() -> service.reviewOcrCell(tenant(), extractionId, cellId, new ReviewOcrCellRequest("=cmd"), "reviewer", "corr"))
        .isInstanceOf(RateFeedException.class)
        .satisfies(ex -> assertRateFeedException(ex, "FORMULA_INJECTION_RISK", HttpStatus.BAD_REQUEST));
  }

  @Test
  void approveOcrExtractionRequiresReviewedCellsBeforeParserHandoff() {
    RequestContext.roles("RATE_FEED_OCR_REVIEW");
    UUID extractionId = UUID.fromString("00000000-0000-0000-0000-000000000705");
    UUID batchId = UUID.fromString("00000000-0000-0000-0000-000000000706");
    when(jdbc.queryForMap(anyString(), any(Object[].class))).thenReturn(Map.of("batch_id", batchId, "status", "OCR_REVIEW_REQUIRED", "result_hash", "hash-1"));
    when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class))).thenReturn(List.of());

    assertThatThrownBy(() -> service.approveOcrExtraction(tenant(), extractionId, new ApproveOcrExtractionRequest("hash-1"), "reviewer", "corr"))
        .isInstanceOf(RateFeedException.class)
        .satisfies(ex -> assertRateFeedException(ex, "OCR_REVIEW_REQUIRED", HttpStatus.UNPROCESSABLE_ENTITY));
  }

  private static void assertRateFeedException(Throwable ex, String code, HttpStatus status) {
    RateFeedException rateFeedException = (RateFeedException) ex;
    assertThat(rateFeedException.code()).isEqualTo(code);
    assertThat(rateFeedException.status()).isEqualTo(status);
  }

  private static UUID tenant() { return UUID.fromString("00000000-0000-0000-0000-000000000100"); }
}
