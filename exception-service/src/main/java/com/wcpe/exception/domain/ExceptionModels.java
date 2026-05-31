package com.wcpe.exception.domain;

import java.time.Instant;
import java.util.*;

/**
 * Exception domain models, states, request/response types, and exception class.
 */
public final class ExceptionModels {

  private ExceptionModels() {}

  // ── State enum ──────────────────────────────────────────────────────────────

  public enum ExceptionState {
    DRAFT, SUBMITTED, APPROVED, REJECTED, CANCELLED;

    static Set<ExceptionState> allowedTransitions(ExceptionState current) {
      return switch (current) {
        case DRAFT -> Set.of(SUBMITTED, CANCELLED);
        case SUBMITTED -> Set.of(APPROVED, REJECTED, CANCELLED);
        case APPROVED -> Set.of();
        case REJECTED -> Set.of();
        case CANCELLED -> Set.of();
      };
    }

    boolean isTerminal() {
      return this == APPROVED || this == REJECTED || this == CANCELLED;
    }

    static ExceptionState from(String value) {
      if (value == null) return null;
      try { return valueOf(value.toUpperCase(Locale.ROOT)); }
      catch (IllegalArgumentException e) { return null; }
    }
  }

  public enum ExceptionType {
    CONCESSION, EXCEPTION
  }

  // ── Request/Response records ────────────────────────────────────────────────

  public record ExceptionRequestCreate(
    String placeholderQuoteReference,
    ExceptionType requestType
  ) {}

  public record ExceptionRequestStatus(
    String exceptionRequestId,
    String placeholderQuoteReference,
    ExceptionType requestType,
    ExceptionState state,
    boolean mockBacked,
    boolean authoritativeIntegration,
    Instant createdAt,
    Instant updatedAt
  ) {}

  public record ExceptionTransitionRequest(
    ExceptionState requestedTransition
  ) {}

  public record ExceptionTransitionResponse(
    String exceptionRequestId,
    ExceptionState previousState,
    ExceptionState newState,
    ExceptionState requestedTransition,
    boolean mockBacked,
    boolean authoritativeIntegration,
    Instant transitionedAt
  ) {}

  public record ExceptionError(
    String code,
    String message,
    String requestId
  ) {}

  // ── Internal request record (used by repository/service) ────────────────────

  public record ExceptionRequestRecord(
    String exceptionRequestId,
    String placeholderQuoteReference,
    ExceptionType requestType,
    ExceptionState state,
    Instant createdAt,
    Instant updatedAt
  ) {}
}
