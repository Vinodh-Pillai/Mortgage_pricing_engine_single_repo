package com.wcpe.ratefeed.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RatesheetValidationParsingStoryTest {
  private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000100");
  private static final UUID BATCH = UUID.fromString("00000000-0000-0000-0000-000000000200");
  private static final UUID INVESTOR = UUID.fromString("00000000-0000-0000-0000-000000000201");
  private static final UUID CHANNEL = UUID.fromString("00000000-0000-0000-0000-000000000202");
  private static final UUID FORMAT = UUID.fromString("00000000-0000-0000-0000-000000000203");

  private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
  private final RateFeedRepository repository = mock(RateFeedRepository.class);
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final RateFeedService service = TestRateFeedServices.create(repository, jdbc);

  @AfterEach
  void clearRoles() { RequestContext.clear(); }

  @Test
  void parseBatchWithConfiguredProfilePersistsLineageAndAuditSummary() throws Exception {
    arrangeRepository();
    String csv = "product,note_rate,lock_period,price,investor,channel\n"
        + "CONVENTIONAL,6.125,30,108.5," + INVESTOR + "," + CHANNEL + "\n";

    RateFeedModels.ParseBatchResponse response = service.parseBatch(TENANT, BATCH,
        new RateFeedModels.ParseBatchRequest("INITIAL", hash64(), csv, "configured-profile:v7"),
        "parse-key", "worker", "corr-parse");

    assertThat(response.status()).isEqualTo("PARSED");
    verify(jdbc, atLeastOnce()).update(startsWith("insert into rate_feed.rate_feed_parse_job"), any(), any(), any(), eq("configured-profile:v7"), any(), any(), any());
    verify(jdbc, atLeastOnce()).update(startsWith("insert into rate_feed.rate_feed_parsed_field"), any(), any(), any(), eq("canonical_product_key"), eq("CONVENTIONAL"), eq("CONVENTIONAL"), eq(1), eq("INFO"), isNull(), isNull(), eq(2));

    ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
    verify(repository).audit(eq(TENANT), eq(BATCH), eq("RATE_SHEET_PARSED"), eq("RateFeedBatch"), eq("worker"), eq("corr-parse"), isNull(), any(), payload.capture());
    @SuppressWarnings("unchecked")
    Map<String, Object> audit = (Map<String, Object>) payload.getValue();
    assertThat(audit).containsEntry("templateProfileId", "configured-profile:v7");
    assertThat(audit).containsEntry("profileVersion", "configured-profile:v7");
    assertThat(audit).containsKey("sourceChecksum");
    assertThat((Map<String, Object>) audit.get("validationSummary"))
        .containsEntry("sourceRowCount", 1)
        .containsEntry("acceptedRowCount", 1)
        .containsEntry("publishingBlocked", false);
  }

  @Test
  void parseBatchFailsClosedForMalformedOptionalNumericField() throws Exception {
    arrangeRepository();
    String csv = "product,note_rate,lock_period,price,discount_points\nCONVENTIONAL,6.125,30,108.5,not-a-number\n";

    RateFeedModels.ParseBatchResponse response = service.parseBatch(TENANT, BATCH,
        new RateFeedModels.ParseBatchRequest("INITIAL", hash64(), csv, null),
        "parse-key", "worker", "corr-parse");

    assertThat(response.status()).isEqualTo("PARSE_FAILED");
    verify(jdbc, atLeastOnce()).update(startsWith("insert into rate_feed.rate_feed_parsed_field"), any(), any(), any(), eq("discount_points"), eq("not-a-number"), isNull(), eq(5), eq("ERROR"), eq("CSV_VALUE_INVALID"), contains("not a valid number"), eq(2));
    ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
    verify(repository).audit(eq(TENANT), eq(BATCH), eq("RATE_SHEET_PARSE_FAILED"), eq("RateFeedBatch"), eq("worker"), eq("corr-parse"), isNull(), any(), payload.capture());
    @SuppressWarnings("unchecked")
    Map<String, Object> audit = (Map<String, Object>) payload.getValue();
    assertThat((Map<String, Object>) audit.get("validationSummary"))
        .containsEntry("rejectedRowCount", 1)
        .containsEntry("publishingBlocked", true);
  }

  @Test
  void parseBatchFailsClosedForMissingRequiredColumnsWithActionableSummary() throws Exception {
    arrangeRepository();
    String csv = "product,lock_period,price\nCONVENTIONAL,30,108.5\n";

    RateFeedModels.ParseBatchResponse response = service.parseBatch(TENANT, BATCH,
        new RateFeedModels.ParseBatchRequest("INITIAL", hash64(), csv, "configured-profile:v7"),
        "parse-key", "worker", "corr-parse");

    assertThat(response.status()).isEqualTo("PARSE_FAILED");
    verify(jdbc, atLeastOnce()).update(startsWith("insert into rate_feed.rate_feed_parsed_field"), any(), any(), any(), eq("header"), contains("product,lock_period,price"), isNull(), eq(1), eq("ERROR"), eq("CSV_HEADER_INVALID"), contains("HeaderMissingRequiredField"), eq(2));
    assertFailurePayloadContainsSummary("HeaderMissingRequiredField");
  }

  @Test
  void parseBatchFailsClosedForAmbiguousHeaderMappingWithActionableSummary() throws Exception {
    arrangeRepository();
    String csv = "rate,note_rate,lock_period,price\n6.125,6.250,30,108.5\n";

    RateFeedModels.ParseBatchResponse response = service.parseBatch(TENANT, BATCH,
        new RateFeedModels.ParseBatchRequest("INITIAL", hash64(), csv, "configured-profile:v7"),
        "parse-key", "worker", "corr-parse");

    assertThat(response.status()).isEqualTo("PARSE_FAILED");
    verify(jdbc, atLeastOnce()).update(startsWith("insert into rate_feed.rate_feed_parsed_field"), any(), any(), any(), eq("header"), contains("rate,note_rate"), isNull(), eq(1), eq("ERROR"), eq("CSV_HEADER_INVALID"), contains("HeaderDuplicateMappedField"), eq(2));
    assertFailurePayloadContainsSummary("HeaderDuplicateMappedField");
  }

  @SuppressWarnings("unchecked")
  private void assertFailurePayloadContainsSummary(String expectedMessageFragment) {
    ArgumentCaptor<Object> outboxPayload = ArgumentCaptor.forClass(Object.class);
    verify(repository).outbox(eq(TENANT), eq(BATCH), eq("RateSheetParseFailed.v1"), eq(1), eq("worker"), eq("corr-parse"), any(), outboxPayload.capture());
    Map<String, Object> payload = (Map<String, Object>) outboxPayload.getValue();
    assertThat(payload)
        .containsEntry("templateProfileId", "configured-profile:v7")
        .containsEntry("mappingVersion", "configured-profile:v7")
        .containsEntry("rowCount", 1)
        .containsEntry("errorCount", 1)
        .containsEntry("warningCount", 0)
        .containsKey("sourceChecksum")
        .containsKey("resultHash")
        .containsKey("validationSummary");
    Map<String, Object> validationSummary = (Map<String, Object>) payload.get("validationSummary");
    assertThat(payload.getOrDefault("message", validationSummary.toString()).toString()).contains(expectedMessageFragment);
    assertThat(validationSummary)
        .containsEntry("sourceRowCount", 1)
        .containsEntry("acceptedRowCount", 0)
        .containsEntry("rejectedRowCount", 1)
        .containsEntry("errorCount", 1)
        .containsEntry("warningCount", 0)
        .containsEntry("publishingBlocked", true);

    ArgumentCaptor<Object> auditPayload = ArgumentCaptor.forClass(Object.class);
    verify(repository).audit(eq(TENANT), eq(BATCH), eq("RATE_SHEET_PARSE_FAILED"), eq("RateFeedBatch"), eq("worker"), eq("corr-parse"), isNull(), any(), auditPayload.capture());
    Map<String, Object> audit = (Map<String, Object>) auditPayload.getValue();
    assertThat(audit)
        .containsEntry("templateProfileId", "configured-profile:v7")
        .containsEntry("profileVersion", "configured-profile:v7")
        .containsEntry("rowCount", 1)
        .containsEntry("errorCount", 1)
        .containsEntry("warningCount", 0)
        .containsKey("sourceChecksum")
        .containsKey("resultHash")
        .containsKey("validationSummary");
  }

  @SuppressWarnings("unchecked")
  private void arrangeRepository() throws Exception {
    RequestContext.roles("RATE_FEED_PARSE,RATE_FEED_VIEW,RATE_FEED_NORMALIZE");
    when(repository.batchParseSource(TENANT, BATCH)).thenReturn(new RateFeedRepository.BatchParseSource(BATCH, INVESTOR, CHANNEL, FORMAT, "UPLOADED", hash64()));
    when(repository.json(any())).thenAnswer(invocation -> mapper.writeValueAsString(invocation.getArgument(0)));
    when(repository.idempotent(any(), any(), any(), eq(RateFeedModels.ParseBatchResponse.class), any()))
        .thenAnswer(invocation -> ((Supplier<RateFeedModels.ParseBatchResponse>) invocation.getArgument(4)).get());
  }

  private static String hash64() { return "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"; }
}
