package com.wcpe.ratefeed.activation;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.springframework.jdbc.core.JdbcTemplate;
import com.wcpe.ratefeed.audit.AuditService;
import com.wcpe.ratefeed.domain.*;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;

import static com.wcpe.ratefeed.domain.RateFeedModels.RateSheetStatus;

/** Unit tests for activation status transition DAG. */
class ActivationServiceTest {

  @Test
  void statusTransition_DRAFT_to_PARSING_allowed() {
    assertTrue(allowedTransitions(RateSheetStatus.DRAFT).contains(RateSheetStatus.PARSING));
  }

  @Test
  void statusTransition_PARSING_to_VALIDATED_allowed() {
    assertTrue(allowedTransitions(RateSheetStatus.PARSING).contains(RateSheetStatus.VALIDATED));
  }

  @Test
  void statusTransition_VALIDATED_to_ACTIVE_allowed() {
    assertTrue(allowedTransitions(RateSheetStatus.VALIDATED).contains(RateSheetStatus.ACTIVE));
  }

  @Test
  void statusTransition_ACTIVE_to_SUPERSEDED_allowed() {
    assertTrue(allowedTransitions(RateSheetStatus.ACTIVE).contains(RateSheetStatus.SUPERSEDED));
  }

  @Test
  void statusTransition_INVALID_DRAFT_to_ACTIVE_denied() {
    assertFalse(allowedTransitions(RateSheetStatus.DRAFT).contains(RateSheetStatus.ACTIVE));
  }

  @Test
  void statusTransition_SUPERSEDED_is_terminal() {
    assertTrue(allowedTransitions(RateSheetStatus.SUPERSEDED).isEmpty());
  }

  @Test
  void statusTransition_REJECTED_is_terminal() {
    assertTrue(allowedTransitions(RateSheetStatus.REJECTED).isEmpty());
  }

  @Test
  void versionManager_returnsNextVersion() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(2);
    VersionManager vm = new VersionManager(jdbc);
    int next = vm.nextVersion(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "CONFORMING_30YR");
    assertEquals(2, next);
  }

  @Test
  void supersession_recordsVersionLineageWithoutMutatingPricePoints() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    UUID tenantId = UUID.randomUUID();
    UUID investorId = UUID.randomUUID();
    UUID channelId = UUID.randomUUID();
    UUID newSheetId = UUID.randomUUID();
    UUID supersededSheetId = UUID.randomUUID();
    when(jdbc.queryForList(anyString(), eq(UUID.class), any(), any(), any(), any(), any()))
        .thenReturn(java.util.List.of(supersededSheetId));

    new SupersessionEngine(jdbc).supersede(tenantId, investorId, channelId,
        "CONFORMING_30YR", newSheetId, 3);

    verify(jdbc).update(contains("UPDATE rate_feed.rate_sheet SET status = 'SUPERSEDED'"), any(Object[].class));
    verify(jdbc).update(contains("SET superseded_by = ?"), any(Object[].class));
    verify(jdbc, never()).update(contains("rate_feed.rate_price_point"), any(Object[].class));
  }

  @SuppressWarnings("unchecked")
  private static Set<RateSheetStatus> allowedTransitions(RateSheetStatus status) {
    try {
      Method method = RateSheetStatus.class.getDeclaredMethod("allowedTransitions");
      method.setAccessible(true);
      return (Set<RateSheetStatus>) method.invoke(status);
    } catch (ReflectiveOperationException ex) {
      throw new AssertionError(ex);
    }
  }
}
