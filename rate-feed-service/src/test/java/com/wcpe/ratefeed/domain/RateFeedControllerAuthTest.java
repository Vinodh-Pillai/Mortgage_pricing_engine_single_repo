package com.wcpe.ratefeed.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import static com.wcpe.ratefeed.domain.RateFeedModels.*;

class RateFeedControllerAuthTest {
  private final RateFeedRepository repository = mock(RateFeedRepository.class);
  private final RateFeedService service = TestRateFeedServices.create(repository);

  @AfterEach
  void clearRoles() { RequestContext.clear(); }

  @Test
  void directRolesAndActorHeadersFailClosedWhenTrustBoundaryDisabled() {
    RateFeedController controller = new RateFeedController(service, false);
    MockHttpServletRequest http = new MockHttpServletRequest();
    http.addHeader("X-Roles", "RATE_FEED_UPLOAD");
    http.addHeader("X-Actor-Id", "direct-forged-actor");

    assertThatThrownBy(() -> controller.createSession(tenant(), validRequest(), http))
        .isInstanceOf(RateFeedException.class)
        .satisfies(ex -> assertRateFeedException(ex, "UNTRUSTED_DIRECT_AUTH_HEADERS", HttpStatus.UNAUTHORIZED));
    verifyNoInteractions(repository);
  }

  @Test
  void missingAuthFailsClosedWhenTrustBoundaryDisabled() {
    RateFeedController controller = new RateFeedController(service, false);

    assertThatThrownBy(() -> controller.batch(tenant(), UUID.randomUUID(), new MockHttpServletRequest()))
        .isInstanceOf(RateFeedException.class)
        .satisfies(ex -> assertRateFeedException(ex, "AUTHENTICATION_REQUIRED", HttpStatus.UNAUTHORIZED));
    verifyNoInteractions(repository);
  }

  @Test
  void wrongRoleFailsBeforePersistenceWhenTrustBoundaryEnabledForLocalDev() {
    RateFeedController controller = new RateFeedController(service, true);
    MockHttpServletRequest http = new MockHttpServletRequest();
    http.addHeader("X-Roles", "RATE_FEED_VIEW");
    http.addHeader("X-Actor-Id", "local-dev-actor");

    assertThatThrownBy(() -> controller.createSession(tenant(), validRequest(), http))
        .isInstanceOf(RateFeedException.class)
        .satisfies(ex -> assertRateFeedException(ex, "ACCESS_DENIED", HttpStatus.FORBIDDEN));
    verifyNoInteractions(repository);
  }

  @Test
  void trustedLocalDevHeadersPropagateActorOnlyWhenExplicitlyEnabled() {
    RateFeedController controller = new RateFeedController(service, true);
    MockHttpServletRequest http = new MockHttpServletRequest();
    http.addHeader("X-Roles", "RATE_FEED_UPLOAD");
    http.addHeader("X-Actor-Id", "local-dev-actor");
    http.addHeader("Idempotency-Key", "key-controller-auth");
    when(repository.json(any())).thenReturn("{}");
    when(repository.idempotent(any(), eq("key-controller-auth"), any(), eq(UploadSessionResponse.class), any())).thenAnswer(invocation -> {
      @SuppressWarnings("unchecked") java.util.function.Supplier<UploadSessionResponse> command = invocation.getArgument(4);
      return command.get();
    });

    controller.createSession(tenant(), validRequest(), http);

    verify(repository).saveSession(any(), any(), any(), eq("local-dev-actor"), any(), any(), any());
  }

  private static void assertRateFeedException(Throwable ex, String code, HttpStatus status) {
    RateFeedException rateFeedException = (RateFeedException) ex;
    assertThat(rateFeedException.code()).isEqualTo(code);
    assertThat(rateFeedException.status()).isEqualTo(status);
  }

  private static UUID tenant() { return UUID.fromString("00000000-0000-0000-0000-000000000100"); }

  private static UploadSessionRequest validRequest() {
    return new UploadSessionRequest(
        UUID.fromString("00000000-0000-0000-0000-000000000201"),
        UUID.fromString("00000000-0000-0000-0000-000000000202"),
        UUID.fromString("00000000-0000-0000-0000-000000000203"),
        "MANUAL_UPLOAD",
        Instant.parse("2026-05-17T12:00:00Z"),
        "America/New_York",
        "RateSheet.csv",
        "text/csv",
        1024,
        null,
        "synthetic note");
  }
}
