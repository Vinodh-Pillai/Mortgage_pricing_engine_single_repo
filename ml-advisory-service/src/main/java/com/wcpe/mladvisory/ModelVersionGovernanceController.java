package com.wcpe.mladvisory;

import java.time.Clock;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/ml-advisory/model-versions")
public final class ModelVersionGovernanceController {
  private final ModelRegistryService registryService;

  @Autowired
  public ModelVersionGovernanceController(Environment environment) {
    this(new ModelRegistryService(Clock.systemUTC(), new JdbcModelVersionRepository(dataSource(environment))));
  }

  public ModelVersionGovernanceController(DataSource dataSource) {
    this(new ModelRegistryService(Clock.systemUTC(), new JdbcModelVersionRepository(dataSource)));
  }

  ModelVersionGovernanceController(ModelRegistryService registryService) {
    this.registryService = registryService;
  }

  private static DriverManagerDataSource dataSource(Environment environment) {
    DriverManagerDataSource dataSource = new DriverManagerDataSource();
    dataSource.setDriverClassName("org.postgresql.Driver");
    dataSource.setUrl(required(environment, "spring.datasource.url"));
    dataSource.setUsername(required(environment, "spring.datasource.username"));
    dataSource.setPassword(required(environment, "spring.datasource.password"));
    return dataSource;
  }

  private static String required(Environment environment, String key) {
    String value = environment.getProperty(key);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(key + " is required");
    }
    return value;
  }

  @PostMapping
  public ResponseEntity<?> register(@PathVariable String tenantId, @RequestBody RegisterModelVersionRequest request) {
    RegisterModelVersionRequest safeRequest = request == null ? RegisterModelVersionRequest.empty() : request;
    MlAdvisoryResult<ModelVersionResponse> result = registryService.register(safeRequest.toCommand(tenantId));
    if (!result.valid()) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ModelVersionError(result.errorCode().orElse("VALIDATION_FAILED")));
    }
    return ResponseEntity.ok(result.value().orElseThrow());
  }

  @PostMapping("/{id}:submit-review")
  public ResponseEntity<?> submitReview(@PathVariable String tenantId, @PathVariable String id, @RequestBody ReviewRequest request) {
    ReviewRequest safeRequest = request == null ? ReviewRequest.empty() : request;
    MlAdvisoryResult<ModelVersionResponse> result = registryService.submitReview(tenantId, id, safeRequest.actorId(), safeRequest.correlationId());
    if (!result.valid()) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ModelVersionError(result.errorCode().orElse("VALIDATION_FAILED")));
    }
    return ResponseEntity.ok(result.value().orElseThrow());
  }

  @PostMapping("/{id}:approve")
  public ResponseEntity<?> approve(@PathVariable String tenantId, @PathVariable String id, @RequestBody ApprovalRequest request) {
    ApprovalRequest safeRequest = request == null ? ApprovalRequest.empty() : request;
    MlAdvisoryResult<ModelVersionResponse> result = registryService.approve(safeRequest.toCommand(tenantId, id));
    if (!result.valid()) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ModelVersionError(result.errorCode().orElse("VALIDATION_FAILED")));
    }
    return ResponseEntity.ok(result.value().orElseThrow());
  }

  @PostMapping("/{id}:suspend")
  public ResponseEntity<?> suspend(@PathVariable String tenantId, @PathVariable String id, @RequestBody SuspensionRequest request) {
    SuspensionRequest safeRequest = request == null ? SuspensionRequest.empty() : request;
    MlAdvisoryResult<ModelVersionResponse> result = registryService.suspend(safeRequest.toCommand(tenantId, id));
    if (!result.valid()) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ModelVersionError(result.errorCode().orElse("VALIDATION_FAILED")));
    }
    return ResponseEntity.ok(result.value().orElseThrow());
  }

  @GetMapping
  public List<ModelVersion> list(@PathVariable String tenantId, @RequestParam(required = false) ModelStatus status, @RequestParam(required = false) AdvisoryType advisoryType) {
    return registryService.list(tenantId, status, advisoryType);
  }

  public record RegisterModelVersionRequest(
      String idempotencyKey,
      String actorId,
      String modelName,
      String semanticVersion,
      List<AdvisoryType> advisoryTypes,
      AllowedUse allowedUse,
      String artifactUri,
      String artifactChecksum,
      String featureSchemaVersion,
      String owner,
      List<ModelEvidence> evidence,
      java.util.Map<String, String> lineageRefs,
      String correlationId) {
    public RegisterModelVersionRequest {
      advisoryTypes = advisoryTypes == null ? List.of() : List.copyOf(advisoryTypes);
      evidence = evidence == null ? List.of() : List.copyOf(evidence);
      lineageRefs = lineageRefs == null ? java.util.Map.of() : java.util.Map.copyOf(lineageRefs);
    }

    static RegisterModelVersionRequest empty() {
      return new RegisterModelVersionRequest("", "", "", "", List.of(), null, "", "", "", "", List.of(), java.util.Map.of(), "");
    }

    RegisterModelVersionCommand toCommand(String tenantId) {
      return new RegisterModelVersionCommand(
          tenantId,
          idempotencyKey,
          actorId,
          modelName,
          semanticVersion,
          advisoryTypes,
          allowedUse,
          artifactUri,
          artifactChecksum,
          featureSchemaVersion,
          owner,
          evidence,
          lineageRefs,
          correlationId);
    }
  }

  public record ReviewRequest(String actorId, String correlationId) {
    static ReviewRequest empty() {
      return new ReviewRequest("", "");
    }
  }

  public record ApprovalRequest(String actorId, ModelStatus targetStatus, String governanceTicket, String reason, String correlationId) {
    static ApprovalRequest empty() {
      return new ApprovalRequest("", null, "", "", "");
    }

    ApproveModelVersionCommand toCommand(String tenantId, String modelVersionId) {
      return new ApproveModelVersionCommand(tenantId, modelVersionId, actorId, targetStatus, governanceTicket, reason, correlationId);
    }
  }

  public record SuspensionRequest(String actorId, String governanceTicket, String reason, String correlationId) {
    static SuspensionRequest empty() {
      return new SuspensionRequest("", "", "", "");
    }

    SuspendModelVersionCommand toCommand(String tenantId, String modelVersionId) {
      return new SuspendModelVersionCommand(tenantId, modelVersionId, actorId, governanceTicket, reason, correlationId);
    }
  }

  public record ModelVersionError(String code) {}
}
