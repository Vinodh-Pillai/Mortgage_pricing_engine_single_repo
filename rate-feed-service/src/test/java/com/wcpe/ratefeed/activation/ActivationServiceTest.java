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
