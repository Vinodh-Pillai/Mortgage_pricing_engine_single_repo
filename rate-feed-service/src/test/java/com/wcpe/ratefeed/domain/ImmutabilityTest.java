package com.wcpe.ratefeed.domain;

import com.wcpe.ratefeed.activation.ActivationService;
import com.wcpe.ratefeed.audit.AuditService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Immutability tests — Active sheet cannot be modified after activation.
 * Validates via status transition DAG and ActivationService contract.
 */
class ImmutabilityTest {

  /* ── RateSheetStatus immutability ── */

  @Test
  void activeSheet_statusTransitions_onlyToSuperseded() {
    Set<RateSheetStatus> transitions = RateSheetStatus.ACTIVE.allowedTransitions();
    assertEquals(Set.of(RateSheetStatus.SUPERSEDED), transitions);
  }

  @Test
  void activeSheet_cannotBeReactivated() {
    assertFalse(RateSheetStatus.ACTIVE.allowedTransitions().contains(RateSheetStatus.ACTIVE));
  }

  @Test
  void activeSheet_cannotBeValidatedAgain() {
    assertFalse(RateSheetStatus.ACTIVE.allowedTransitions().contains(RateSheetStatus.VALIDATED));
  }

  @Test
  void activeSheet_cannotBeParsing() {
    assertFalse(RateSheetStatus.ACTIVE.allowedTransitions().contains(RateSheetStatus.PARSING));
  }

  @Test
  void supersededSheet_isTerminal() {
    assertTrue(RateSheetStatus.SUPERSEDED.isTerminal());
    assertTrue(RateSheetStatus.SUPERSEDED.allowedTransitions().isEmpty());
  }

  @Test
  void rejectedSheet_isTerminal() {
    assertTrue(RateSheetStatus.REJECTED.isTerminal());
    assertTrue(RateSheetStatus.REJECTED.allowedTransitions().isEmpty());
  }

  /* ── Status transition DAG completeness ── */

  @Test
  void dag_DRAFT_to_PARSING_allowed() {
    assertTrue(RateSheetStatus.DRAFT.allowedTransitions().contains(RateSheetStatus.PARSING));
  }

  @Test
  void dag_PARSING_to_VALIDATED_allowed() {
    assertTrue(RateSheetStatus.PARSING.allowedTransitions().contains(RateSheetStatus.VALIDATED));
  }

  @Test
  void dag_PARSING_to_REJECTED_allowed() {
    assertTrue(RateSheetStatus.PARSING.allowedTransitions().contains(RateSheetStatus.REJECTED));
  }

  @Test
  void dag_VALIDATED_to_ACTIVE_allowed() {
    assertTrue(RateSheetStatus.VALIDATED.allowedTransitions().contains(RateSheetStatus.ACTIVE));
  }

  @Test
  void dag_VALIDATED_to_REJECTED_allowed() {
    assertTrue(RateSheetStatus.VALIDATED.allowedTransitions().contains(RateSheetStatus.REJECTED));
  }

  @Test
  void dag_DRAFT_cannotJumpTo_ACTIVE() {
    assertFalse(RateSheetStatus.DRAFT.allowedTransitions().contains(RateSheetStatus.ACTIVE));
  }

  @Test
  void dag_DRAFT_cannotJumpTo_VALIDATED() {
    assertFalse(RateSheetStatus.DRAFT.allowedTransitions().contains(RateSheetStatus.VALIDATED));
  }

  @Test
  void dag_REJECTED_noOutgoing() {
    Set<RateSheetStatus> t = RateSheetStatus.REJECTED.allowedTransitions();
    assertTrue(t.isEmpty(), "REJECTED must have no outgoing transitions");
  }

  @Test
  void allStatuses_canActivate_returnsCorrect() {
    assertFalse(RateSheetStatus.DRAFT.canActivate());
    assertFalse(RateSheetStatus.PARSING.canActivate());
    assertTrue(RateSheetStatus.VALIDATED.canActivate());
    assertFalse(RateSheetStatus.ACTIVE.canActivate());
    assertFalse(RateSheetStatus.SUPERSEDED.canActivate());
    assertFalse(RateSheetStatus.REJECTED.canActivate());
  }

  @Test
  void allStatuses_canReject_returnsCorrect() {
    assertFalse(RateSheetStatus.DRAFT.canReject());
    assertTrue(RateSheetStatus.PARSING.canReject());
    assertTrue(RateSheetStatus.VALIDATED.canReject());
    assertFalse(RateSheetStatus.ACTIVE.canReject());
    assertFalse(RateSheetStatus.SUPERSEDED.canReject());
    assertFalse(RateSheetStatus.REJECTED.canReject());
  }
}
