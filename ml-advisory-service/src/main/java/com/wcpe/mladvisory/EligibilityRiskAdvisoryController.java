package com.wcpe.mladvisory;

import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/ml-advisory/eligibility-risk")
public final class EligibilityRiskAdvisoryController {
  private final MlAdvisoryControlService service;
  private final LocalModelAdapter modelAdapter;

  public EligibilityRiskAdvisoryController() {
    this(new MlAdvisoryControlService(), new FakeLocalModelAdapter());
  }

  EligibilityRiskAdvisoryController(MlAdvisoryControlService service, LocalModelAdapter modelAdapter) {
    this.service = service;
    this.modelAdapter = modelAdapter;
  }

  @PostMapping(":evaluate")
  public ResponseEntity<?> evaluate(
      @PathVariable String tenantId, @RequestBody EligibilityRiskAdvisoryEvaluationRequest request) {
    EligibilityRiskAdvisoryEvaluationRequest safeRequest =
        request == null ? EligibilityRiskAdvisoryEvaluationRequest.empty() : request;
    MlAdvisoryResult<EligibilityRiskAdvisoryEvaluation> result =
        service.evaluateEligibilityRiskAdvisory(safeRequest.toCommand(tenantId), modelAdapter);
    if (!result.valid()) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(new EligibilityRiskAdvisoryError(result.errorCode().orElse("ML_ELIGIBILITY_ADVISORY_REJECTED")));
    }
    return ResponseEntity.ok(result.value().orElseThrow());
  }

  @GetMapping("/{advisoryId}")
  public ResponseEntity<?> get(
      @PathVariable String tenantId, @PathVariable String advisoryId) {
    MlAdvisoryResult<EligibilityRiskAdvisoryEvaluation> result =
        service.getEligibilityRiskAdvisory(tenantId, advisoryId, "eligibility-risk-read", "corr-read-eligibility-risk");
    if (!result.valid()) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(new EligibilityRiskAdvisoryError(result.errorCode().orElse("ML_ELIGIBILITY_ADVISORY_NOT_FOUND")));
    }
    return ResponseEntity.ok(result.value().orElseThrow());
  }

  public record EligibilityRiskAdvisoryEvaluationRequest(
      String idempotencyKey,
      String actorId,
      String scenarioId,
      String eligibilityResultId,
      String eligibilityResultVersion,
      String snapshotId,
      ModelArtifactRef modelArtifactRef,
      String riskBand,
      double confidence,
      AdvisoryDisplayPolicy displayPolicy,
      List<AdvisoryReason> reviewPrompts,
      Instant generatedAt,
      Instant expiresAt,
      String correlationId,
      long timeoutMillis,
      boolean simulateModelTimeout,
      boolean simulateModelFailure,
      boolean simulateAuthoritativeOutput) {
    public EligibilityRiskAdvisoryEvaluationRequest {
      reviewPrompts = reviewPrompts == null ? List.of() : List.copyOf(reviewPrompts);
    }

    static EligibilityRiskAdvisoryEvaluationRequest empty() {
      return new EligibilityRiskAdvisoryEvaluationRequest(
          "", "", "", "", "", "", null, "", 0.0d, null, List.of(), null, null, "", 0L, false, false, false);
    }

    EvaluateEligibilityRiskAdvisoryCommand toCommand(String tenantId) {
      return new EvaluateEligibilityRiskAdvisoryCommand(
          tenantId,
          idempotencyKey,
          actorId,
          scenarioId,
          eligibilityResultId,
          eligibilityResultVersion,
          snapshotId,
          modelArtifactRef,
          riskBand,
          confidence,
          displayPolicy,
          reviewPrompts,
          generatedAt,
          expiresAt,
          correlationId,
          timeoutMillis,
          simulateModelTimeout,
          simulateModelFailure,
          simulateAuthoritativeOutput);
    }
  }

  public record EligibilityRiskAdvisoryError(String code) {}
}
