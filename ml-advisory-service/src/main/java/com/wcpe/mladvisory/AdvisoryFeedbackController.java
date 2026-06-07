package com.wcpe.mladvisory;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/ml-advisory")
public final class AdvisoryFeedbackController {
  private final MlAdvisoryControlService service;

  public AdvisoryFeedbackController() {
    this(new MlAdvisoryControlService());
  }

  AdvisoryFeedbackController(MlAdvisoryControlService service) {
    this.service = service;
  }

  @PostMapping("/advisories/{advisoryId}/feedback")
  public ResponseEntity<?> capture(
      @PathVariable String tenantId, @PathVariable String advisoryId, @RequestBody AdvisoryFeedbackRequest request) {
    AdvisoryFeedbackRequest safeRequest = request == null ? AdvisoryFeedbackRequest.empty() : request;
    MlAdvisoryResult<AdvisoryFeedback> result = service.captureAdvisoryFeedback(safeRequest.toCommand(tenantId, advisoryId));
    if (!result.valid()) {
      String code = result.errorCode().orElse("ML_FEEDBACK_REJECTED");
      return ResponseEntity.status(statusFor(code)).body(new AdvisoryFeedbackError(code));
    }
    return ResponseEntity.status(HttpStatus.CREATED).body(result.value().orElseThrow());
  }

  @GetMapping("/feedback/aggregates")
  public ResponseEntity<List<FeedbackAggregate>> aggregates(
      @PathVariable String tenantId,
      @RequestParam(required = false) String modelVersionId,
      @RequestParam(required = false) AdvisoryType advisoryType,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to) {
    return ResponseEntity.ok(service.feedbackAggregates(new FeedbackAggregateQuery(tenantId, modelVersionId, advisoryType, from, to)));
  }

  private HttpStatus statusFor(String code) {
    return switch (code) {
      case "ML_FEEDBACK_ACCESS_DENIED" -> HttpStatus.FORBIDDEN;
      case "ML_FEEDBACK_ADVISORY_NOT_FOUND" -> HttpStatus.NOT_FOUND;
      case "ML_FEEDBACK_DUPLICATE", "IDEMPOTENCY_CONFLICT" -> HttpStatus.CONFLICT;
      case "ML_FEEDBACK_COMMENT_REJECTED" -> HttpStatus.UNPROCESSABLE_ENTITY;
      default -> HttpStatus.BAD_REQUEST;
    };
  }

  public record AdvisoryFeedbackRequest(
      String idempotencyKey,
      String actorId,
      Set<String> actorRoles,
      String outcome,
      String reasonCode,
      String comment,
      String sourceSurface,
      String supersedesFeedbackId,
      String correlationId) {
    public AdvisoryFeedbackRequest {
      actorRoles = actorRoles == null ? Set.of() : Set.copyOf(actorRoles);
    }

    static AdvisoryFeedbackRequest empty() {
      return new AdvisoryFeedbackRequest("", "", Set.of(), "", "", "", "", "", "");
    }

    CaptureAdvisoryFeedbackCommand toCommand(String tenantId, String advisoryId) {
      FeedbackOutcome parsedOutcome;
      try {
        parsedOutcome = FeedbackOutcome.from(outcome);
      } catch (RuntimeException ignored) {
        parsedOutcome = null;
      }
      return new CaptureAdvisoryFeedbackCommand(
          tenantId,
          idempotencyKey,
          actorId,
          actorRoles,
          advisoryId,
          parsedOutcome,
          reasonCode,
          comment,
          sourceSurface,
          supersedesFeedbackId,
          correlationId);
    }
  }

  public record AdvisoryFeedbackError(String code) {}
}
