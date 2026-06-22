package com.wcpe.exception.domain;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ExceptionServiceTest {

  @Test
  void createFailsClosedWhenDurablePersistenceIsNotWired() {
    ExceptionService service = new ExceptionService(new ExceptionRepository());

    ExceptionServiceException error = assertThrows(
      ExceptionServiceException.class,
      () -> service.create(new ExceptionModels.ExceptionRequestCreate("QUOTE-123", ExceptionModels.ExceptionType.CONCESSION))
    );

    assertEquals("PERSISTENCE_BACKEND_REQUIRED", error.code());
    assertTrue(error.getMessage().contains("durable persistence repository"));
  }

  @Test
  void transitionFailsClosedInsteadOfReadingProcessLocalState() {
    ExceptionService service = new ExceptionService(new ExceptionRepository());

    ExceptionServiceException error = assertThrows(
      ExceptionServiceException.class,
      () -> service.transition("EXC-1", new ExceptionModels.ExceptionTransitionRequest(ExceptionModels.ExceptionState.SUBMITTED))
    );

    assertEquals("PERSISTENCE_BACKEND_REQUIRED", error.code());
  }

  @Test
  void repositoryHasNoInMemoryFallbackForListReads() {
    ExceptionRepository repository = new ExceptionRepository();

    ExceptionServiceException error = assertThrows(ExceptionServiceException.class, repository::concessionRequests);

    assertEquals("PERSISTENCE_BACKEND_REQUIRED", error.code());
  }
}
