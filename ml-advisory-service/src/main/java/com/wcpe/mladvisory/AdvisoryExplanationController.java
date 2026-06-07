package com.wcpe.mladvisory;

import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/ml-advisory")
public final class AdvisoryExplanationController {
  private final MlAdvisoryControlService service;

  public AdvisoryExplanationController() {
    this(new MlAdvisoryControlService());
  }

  AdvisoryExplanationController(MlAdvisoryControlService service) {
    this.service = service;
  }

  @GetMapping("/advisories/{advisoryId}/explanation")
  public ResponseEntity<?> explanation(
      @PathVariable String tenantId,
      @PathVariable String advisoryId,
      @RequestParam(defaultValue = "ml-advisory-reader") String actorId,
      @RequestParam(defaultValue = MlAdvisoryControlService.EXPLANATION_READ_ROLE) Set<String> actorRoles,
      @RequestParam(defaultValue = "pricing-workbench") String sourceSurface,
      @RequestParam(defaultValue = "corr-explanation-api") String correlationId) {
    MlAdvisoryResult<AdvisoryExplanation> result =
        service.getAdvisoryExplanation(tenantId, advisoryId, actorId, actorRoles, sourceSurface, correlationId);
    if (!result.valid()) {
      String code = result.errorCode().orElse("ML_EXPLANATION_REJECTED");
      return ResponseEntity.status(statusFor(code)).body(new AdvisoryExplanationError(code));
    }
    return ResponseEntity.ok(result.value().orElseThrow());
  }

  @GetMapping("/explanations/{explanationId}/audit-export")
  public ResponseEntity<?> auditExport(
      @PathVariable String tenantId,
      @PathVariable String explanationId,
      @RequestParam(defaultValue = "ml-advisory-exporter") String actorId,
      @RequestParam(defaultValue = MlAdvisoryControlService.EXPLANATION_EXPORT_ROLE) Set<String> actorRoles,
      @RequestParam(defaultValue = "corr-explanation-export") String correlationId) {
    MlAdvisoryResult<AuditSafeExport> result =
        service.auditSafeExplanationExport(tenantId, explanationId, actorId, actorRoles, correlationId);
    if (!result.valid()) {
      String code = result.errorCode().orElse("ML_EXPLANATION_EXPORT_REJECTED");
      return ResponseEntity.status(statusFor(code)).body(new AdvisoryExplanationError(code));
    }
    return ResponseEntity.ok(result.value().orElseThrow());
  }

  private HttpStatus statusFor(String code) {
    return switch (code) {
      case "ML_EXPLANATION_ACCESS_DENIED" -> HttpStatus.FORBIDDEN;
      case "ML_ADVISORY_NOT_FOUND" -> HttpStatus.NOT_FOUND;
      case "ML_EXPLANATION_EXPIRED" -> HttpStatus.UNPROCESSABLE_ENTITY;
      default -> HttpStatus.BAD_REQUEST;
    };
  }

  public record AdvisoryExplanationError(String code) {}
}
