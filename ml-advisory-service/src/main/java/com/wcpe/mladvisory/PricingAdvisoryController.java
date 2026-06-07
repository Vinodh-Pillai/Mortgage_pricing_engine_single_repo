package com.wcpe.mladvisory;

import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/ml-advisory/pricing-advisories")
public final class PricingAdvisoryController {
  private final MlAdvisoryControlService service;
  private final LocalModelAdapter modelAdapter;

  public PricingAdvisoryController() {
    this(new MlAdvisoryControlService(), new FakeLocalModelAdapter());
  }

  PricingAdvisoryController(MlAdvisoryControlService service, LocalModelAdapter modelAdapter) {
    this.service = service;
    this.modelAdapter = modelAdapter;
  }

  @PostMapping(":evaluate")
  public ResponseEntity<?> evaluate(
      @PathVariable String tenantId, @RequestBody PricingAdvisoryEvaluationRequest request) {
    PricingAdvisoryEvaluationRequest safeRequest = request == null ? PricingAdvisoryEvaluationRequest.empty() : request;
    MlAdvisoryResult<PricingAdvisoryEvaluation> result =
        service.evaluatePricingAdvisory(safeRequest.toCommand(tenantId), modelAdapter);
    if (!result.valid()) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(new PricingAdvisoryError(result.errorCode().orElse("ML_PRICING_ADVISORY_REJECTED")));
    }
    return ResponseEntity.ok(result.value().orElseThrow());
  }

  public record PricingAdvisoryEvaluationRequest(
      String idempotencyKey,
      String actorId,
      String scenarioId,
      String pricingResultId,
      String snapshotId,
      ModelArtifactRef modelArtifactRef,
      double confidence,
      AdvisoryDisplayPolicy displayPolicy,
      List<AdvisoryReason> reasons,
      Instant generatedAt,
      Instant expiresAt,
      String correlationId,
      long timeoutMillis,
      boolean simulateModelTimeout,
      boolean simulateModelFailure,
      boolean simulateAuthoritativeOutput) {
    public PricingAdvisoryEvaluationRequest {
      reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }

    static PricingAdvisoryEvaluationRequest empty() {
      return new PricingAdvisoryEvaluationRequest(
          "", "", "", "", "", null, 0.0d, null, List.of(), null, null, "", 0L, false, false, false);
    }

    EvaluatePricingAdvisoryCommand toCommand(String tenantId) {
      return new EvaluatePricingAdvisoryCommand(
          tenantId,
          idempotencyKey,
          actorId,
          scenarioId,
          pricingResultId,
          snapshotId,
          modelArtifactRef,
          confidence,
          displayPolicy,
          reasons,
          generatedAt,
          expiresAt,
          correlationId,
          timeoutMillis,
          simulateModelTimeout,
          simulateModelFailure,
          simulateAuthoritativeOutput);
    }
  }

  public record PricingAdvisoryError(String code) {}
}
