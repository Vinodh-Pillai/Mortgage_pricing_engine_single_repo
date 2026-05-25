package com.wcpe.ratefeed.activation;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.springframework.jdbc.core.JdbcTemplate;
import com.wcpe.ratefeed.audit.AuditService;
import com.wcpe.ratefeed.domain.*;

/** Unit tests for activation status transition DAG. */
class ActivationServiceTest {

  @Test
  void statusTransition_DRAFT_to_PARSING_allowed() {
    assertTrue(RateSheetStatus.DRAFT.allowedTransitions().contains(RateSheetStatus.PARSING));
  }

  @Test
  void statusTransition_PARSING_to_VALIDATED_allowed() {
    assertTrue(RateSheetStatus.PARSING.allowedTransitions().contains(RateSheetStatus.VALIDATED));
  }

  @Test
  void statusTransition_VALIDATED_to_ACTIVE_allowed() {
    assertTrue(RateSheetStatus.VALIDATED.allowedTransitions().contains(RateSheetStatus.ACTIVE));
  }

  @Test
  void statusTransition_ACTIVE_to_SUPERSEDED_allowed() {
    assertTrue(RateSheetStatus.ACTIVE.allowedTransitions().contains(RateSheetStatus.SUPERSEDED));
  }

  @Test
  void statusTransition_INVALID_DRAFT_to_ACTIVE_denied() {
    assertFalse(RateSheetStatus.DRAFT.allowedTransitions().contains(RateSheetStatus.ACTIVE));
  }

  @Test
  void statusTransition_SUPERSEDED_is_terminal() {
    assertTrue(RateSheetStatus.SUPERSEDED.allowedTransitions().isEmpty());
  }

  @Test
  void statusTransition_REJECTED_is_terminal() {
    assertTrue(RateSheetStatus.REJECTED.allowedTransitions().isEmpty());
  }

  @Test
  void versionManager_returnsNextVersion() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.queryForObject(anyString(), eq(Integer.class), any())).thenReturn(2);
    VersionManager vm = new VersionManager(jdbc);
    int next = vm.nextVersion(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "CONFORMING_30YR");
    assertEquals(2, next);
  }
}
