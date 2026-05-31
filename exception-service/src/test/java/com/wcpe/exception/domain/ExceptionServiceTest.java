package com.wcpe.exception.domain;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExceptionServiceTest {

  private ExceptionService service;

  @BeforeEach
  void setUp() {
    service = new ExceptionService(new ExceptionRepository());
  }

  @Test
  void createReturnsMockBackedDraftStatus() {
    ExceptionModels.ExceptionRequestStatus status = service.create(
      new ExceptionModels.ExceptionRequestCreate("QUOTE-123", ExceptionModels.ExceptionType.CONCESSION)
    );

    assertEquals("QUOTE-123", status.placeholderQuoteReference());
    assertEquals(ExceptionModels.ExceptionState.DRAFT, status.state());
    assertTrue(status.mockBacked());
    assertFalse(status.authoritativeIntegration());
    assertNotNull(status.exceptionRequestId());
  }

  @Test
  void createRejectsMissingPlaceholderQuoteReferenceWithDeterministicError() {
    ExceptionServiceException error = assertThrows(
      ExceptionServiceException.class,
      () -> service.create(new ExceptionModels.ExceptionRequestCreate(" ", ExceptionModels.ExceptionType.CONCESSION))
    );

    assertEquals("MISSING_PLACEHOLDER_QUOTE_REFERENCE", error.code());
    ExceptionModels.ExceptionError contractError = service.toError(error, null);
    assertEquals("MISSING_PLACEHOLDER_QUOTE_REFERENCE", contractError.code());
    assertEquals("placeholderQuoteReference is required", contractError.message());
  }

  @Test
  void transitionAllowsApprovedLifecyclePath() {
    ExceptionModels.ExceptionRequestStatus created = service.create(
      new ExceptionModels.ExceptionRequestCreate("QUOTE-456", ExceptionModels.ExceptionType.EXCEPTION)
    );

    ExceptionModels.ExceptionTransitionResponse submitted = service.transition(
      created.exceptionRequestId(),
      new ExceptionModels.ExceptionTransitionRequest(ExceptionModels.ExceptionState.SUBMITTED)
    );
    ExceptionModels.ExceptionTransitionResponse approved = service.transition(
      created.exceptionRequestId(),
      new ExceptionModels.ExceptionTransitionRequest(ExceptionModels.ExceptionState.APPROVED)
    );

    assertEquals(ExceptionModels.ExceptionState.DRAFT, submitted.previousState());
    assertEquals(ExceptionModels.ExceptionState.SUBMITTED, submitted.newState());
    assertEquals(ExceptionModels.ExceptionState.SUBMITTED, approved.previousState());
    assertEquals(ExceptionModels.ExceptionState.APPROVED, approved.newState());
    assertTrue(approved.mockBacked());
    assertFalse(approved.authoritativeIntegration());
  }

  @Test
  void transitionRejectsTerminalStateRepeatWithDeterministicError() {
    ExceptionModels.ExceptionRequestStatus created = service.create(
      new ExceptionModels.ExceptionRequestCreate("QUOTE-789", ExceptionModels.ExceptionType.CONCESSION)
    );
    service.transition(created.exceptionRequestId(), new ExceptionModels.ExceptionTransitionRequest(ExceptionModels.ExceptionState.SUBMITTED));
    service.transition(created.exceptionRequestId(), new ExceptionModels.ExceptionTransitionRequest(ExceptionModels.ExceptionState.APPROVED));

    ExceptionServiceException error = assertThrows(
      ExceptionServiceException.class,
      () -> service.transition(
        created.exceptionRequestId(),
        new ExceptionModels.ExceptionTransitionRequest(ExceptionModels.ExceptionState.APPROVED)
      )
    );

    assertEquals("INVALID_TRANSITION", error.code());
    assertTrue(error.getMessage().contains("Cannot transition from APPROVED to APPROVED"));
  }

  @Test
  void statusRejectsUnknownRequestIdWithDeterministicError() {
    ExceptionServiceException error = assertThrows(
      ExceptionServiceException.class,
      () -> service.status("EXC-404")
    );

    assertEquals("UNKNOWN_EXCEPTION_REQUEST", error.code());
    assertEquals("Unknown exception request id: EXC-404", error.getMessage());
  }
}
