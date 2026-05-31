package com.wcpe.exception.domain;

import java.util.Objects;

/**
 * Mock-backed exception lifecycle service for the PII-11 walking skeleton.
 */
public class ExceptionService {

  private static final boolean MOCK_BACKED = true;
  private static final boolean AUTHORITATIVE_INTEGRATION = false;

  private final ExceptionRepository repository;

  public ExceptionService(ExceptionRepository repository) {
    this.repository = Objects.requireNonNull(repository, "repository");
  }

  public ExceptionModels.ExceptionRequestStatus create(ExceptionModels.ExceptionRequestCreate request) {
    if (request == null || isBlank(request.placeholderQuoteReference())) {
      throw new ExceptionServiceException(
        "MISSING_PLACEHOLDER_QUOTE_REFERENCE",
        "placeholderQuoteReference is required"
      );
    }
    if (request.requestType() == null) {
      throw new ExceptionServiceException("MISSING_REQUEST_TYPE", "requestType is required");
    }

    return toStatus(repository.create(request));
  }

  public ExceptionModels.ExceptionRequestStatus status(String exceptionRequestId) {
    return repository.findById(exceptionRequestId)
      .map(this::toStatus)
      .orElseThrow(() -> unknownRequest(exceptionRequestId));
  }

  public ExceptionModels.ExceptionTransitionResponse transition(
    String exceptionRequestId,
    ExceptionModels.ExceptionTransitionRequest request
  ) {
    if (request == null || request.requestedTransition() == null) {
      throw new ExceptionServiceException("UNKNOWN_TARGET_STATE", "requestedTransition is required");
    }

    ExceptionModels.ExceptionRequestRecord existing = repository.findById(exceptionRequestId)
      .orElseThrow(() -> unknownRequest(exceptionRequestId));
    ExceptionModels.ExceptionState previousState = existing.state();

    ExceptionModels.ExceptionRequestRecord updated = repository
      .transition(exceptionRequestId, request.requestedTransition())
      .orElseThrow(() -> unknownRequest(exceptionRequestId));

    return new ExceptionModels.ExceptionTransitionResponse(
      updated.exceptionRequestId(),
      previousState,
      updated.state(),
      request.requestedTransition(),
      MOCK_BACKED,
      AUTHORITATIVE_INTEGRATION,
      updated.updatedAt()
    );
  }

  public ExceptionModels.ExceptionError toError(ExceptionServiceException exception, String requestId) {
    return new ExceptionModels.ExceptionError(exception.code(), exception.getMessage(), requestId);
  }

  private ExceptionModels.ExceptionRequestStatus toStatus(ExceptionModels.ExceptionRequestRecord record) {
    return new ExceptionModels.ExceptionRequestStatus(
      record.exceptionRequestId(),
      record.placeholderQuoteReference(),
      record.requestType(),
      record.state(),
      MOCK_BACKED,
      AUTHORITATIVE_INTEGRATION,
      record.createdAt(),
      record.updatedAt()
    );
  }

  private ExceptionServiceException unknownRequest(String exceptionRequestId) {
    return new ExceptionServiceException(
      "UNKNOWN_EXCEPTION_REQUEST",
      "Unknown exception request id: " + exceptionRequestId
    );
  }

  private static boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }
}
