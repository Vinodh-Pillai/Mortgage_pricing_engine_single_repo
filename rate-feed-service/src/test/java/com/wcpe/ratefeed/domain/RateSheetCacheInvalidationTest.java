package com.wcpe.ratefeed.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.wcpe.ratefeed.role.RateFeedRoles;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static com.wcpe.ratefeed.domain.RateFeedModels.*;

class RateSheetCacheInvalidationTest {
  private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000100");
  private static final UUID VERSION = UUID.fromString("00000000-0000-0000-0000-000000000200");
  private static final UUID INVESTOR = UUID.fromString("00000000-0000-0000-0000-000000000201");
  private static final UUID CHANNEL = UUID.fromString("00000000-0000-0000-0000-000000000202");
  private static final Instant EFFECTIVE_AT = Instant.parse("2026-06-05T00:00:00Z");
  private static final String VERSION_HASH = "sha256:published-version";

  private final RateFeedRepository repository = mock(RateFeedRepository.class);
  private final RateFeedService service = TestRateFeedServices.create(repository);

  @BeforeEach
  void setRoles() {
    RequestContext.roles(String.join(",", RateFeedRoles.RATE_FEED_OPERATIONS, RateFeedRoles.RATE_FEED_VIEW));
  }

  @AfterEach
  void clearRoles() {
    RequestContext.clear();
  }

  @Test
  void cacheKeyTenantVersionPolicyUsesTenantVersionInvestorAndChannel() {
    stubPublishedVersion(VERSION_HASH);
    answerIdempotentCommand();

    RateSheetCacheInvalidationResponse response = service.createRateSheetCacheInvalidation(TENANT, request(VERSION_HASH), "idem-1", "ops-user", "corr-1");

    assertThat(response.status()).isEqualTo("COMPLETED");
    assertThat(response.affectedPatterns()).containsExactly(
        "rate-sheet:" + TENANT + ":active:" + INVESTOR + ":" + CHANNEL + ":*",
        "rate-sheet:" + TENANT + ":version:" + VERSION + ":*",
        "base-pricing:" + TENANT + ":rate-sheet:" + INVESTOR + ":" + CHANNEL + ":*");
    verify(repository).evictCachePatterns(eq(TENANT), eq(response.cacheInvalidationId()), eq(response.affectedPatterns()));
    verify(repository).outbox(eq(TENANT), eq(response.cacheInvalidationId()), eq("RateSheetCacheInvalidationRequested.v1"), eq(1), eq("ops-user"), eq("corr-1"), anyMap(), any());
    verify(repository).outbox(eq(TENANT), eq(response.cacheInvalidationId()), eq("RateSheetCacheInvalidated.v1"), eq(1), eq("ops-user"), eq("corr-1"), anyMap(), any());
  }

  @Test
  void staleVersionHashFailsClosedBeforeEviction() {
    stubPublishedVersion(VERSION_HASH);
    answerIdempotentCommand();

    assertThatThrownBy(() -> service.createRateSheetCacheInvalidation(TENANT, request("sha256:stale"), "idem-2", "ops-user", "corr-2"))
        .isInstanceOf(RateFeedException.class)
        .satisfies(ex -> assertRateFeedException(ex, "STALE_VERSION_HASH", HttpStatus.CONFLICT));

    verify(repository, never()).evictCachePatterns(any(), any(), anyList());
    verify(repository, never()).saveCacheInvalidation(any());
  }

  @Test
  void redisUnavailableFallbackPersistsRetryableFailureAndStillPublishesRequestedEvent() {
    stubPublishedVersion(VERSION_HASH);
    answerIdempotentCommand();
    doThrow(new IllegalStateException("redis unavailable")).when(repository).evictCachePatterns(eq(TENANT), any(), anyList());

    RateSheetCacheInvalidationResponse response = service.createRateSheetCacheInvalidation(TENANT, request(VERSION_HASH), "idem-3", "ops-user", "corr-3");

    assertThat(response.status()).isEqualTo("FAILED");
    verify(repository).saveCacheInvalidation(argThat(row -> "FAILED".equals(row.status()) && "CACHE_PROVIDER_UNAVAILABLE".equals(row.lastErrorCode())));
    verify(repository).outbox(eq(TENANT), eq(response.cacheInvalidationId()), eq("RateSheetCacheInvalidationRequested.v1"), eq(1), eq("ops-user"), eq("corr-3"), anyMap(), any());
    verify(repository, never()).outbox(eq(TENANT), eq(response.cacheInvalidationId()), eq("RateSheetCacheInvalidated.v1"), anyInt(), anyString(), anyString(), anyMap(), any());
  }

  @Test
  void retryCompletedInvalidationIsRejected() {
    UUID commandId = UUID.randomUUID();
    when(repository.cacheInvalidation(TENANT, commandId)).thenReturn(row(commandId, "COMPLETED", 0, null));

    assertThatThrownBy(() -> service.retryRateSheetCacheInvalidation(TENANT, commandId, "ops-user", "corr-4"))
        .isInstanceOf(RateFeedException.class)
        .satisfies(ex -> assertRateFeedException(ex, "INVALIDATION_ALREADY_COMPLETED", HttpStatus.CONFLICT));

    verify(repository, never()).evictCachePatterns(any(), any(), anyList());
  }

  @Test
  void retryFailedInvalidationIncrementsRetryCountAndCanComplete() {
    UUID commandId = UUID.randomUUID();
    when(repository.cacheInvalidation(TENANT, commandId)).thenReturn(row(commandId, "FAILED", 1, "CACHE_PROVIDER_UNAVAILABLE"));

    RateSheetCacheInvalidationResponse response = service.retryRateSheetCacheInvalidation(TENANT, commandId, "ops-user", "corr-5");

    assertThat(response.status()).isEqualTo("COMPLETED");
    verify(repository).saveCacheInvalidation(argThat(row -> row.cacheInvalidationId().equals(commandId) && row.retryCount() == 2 && "COMPLETED".equals(row.status())));
  }

  private void stubPublishedVersion(String resultHash) {
    when(repository.publishedRateSheetVersion(TENANT, VERSION)).thenReturn(Optional.of(
        new RateFeedRepository.PublishedRateSheetVersionRow(TENANT, VERSION, INVESTOR, CHANNEL, "product-key", 7, "PUBLISHED", "grid-hash", resultHash, EFFECTIVE_AT)));
  }

  private void answerIdempotentCommand() {
    when(repository.idempotent(eq(TENANT), anyString(), any(), eq(RateSheetCacheInvalidationResponse.class), any())).thenAnswer(invocation -> {
      @SuppressWarnings("unchecked") Supplier<RateSheetCacheInvalidationResponse> command = invocation.getArgument(4);
      return command.get();
    });
  }

  private static RateSheetCacheInvalidationRequest request(String expectedVersionHash) {
    return new RateSheetCacheInvalidationRequest(CacheInvalidationReason.PUBLISH, VERSION, INVESTOR, CHANNEL, EFFECTIVE_AT, expectedVersionHash);
  }

  private static RateFeedRepository.CacheInvalidationRow row(UUID commandId, String status, int retryCount, String lastErrorCode) {
    return new RateFeedRepository.CacheInvalidationRow(TENANT, commandId, VERSION, CacheInvalidationReason.PUBLISH,
        status, List.of("rate-sheet:" + TENANT + ":version:" + VERSION + ":*"), "ops-user", EFFECTIVE_AT, null,
        retryCount, lastErrorCode, "corr", VERSION_HASH, INVESTOR, CHANNEL, EFFECTIVE_AT, "sha256:result");
  }

  private static void assertRateFeedException(Throwable ex, String code, HttpStatus status) {
    RateFeedException rateFeedException = (RateFeedException) ex;
    assertThat(rateFeedException.code()).isEqualTo(code);
    assertThat(rateFeedException.status()).isEqualTo(status);
  }
}
