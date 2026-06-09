package com.wcpe.scenario.domain;

import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/scenarios")
class ScenarioController {
  private final ScenarioService service;
  private final SubmissionProfileService profileService;

  ScenarioController(ScenarioService service, SubmissionProfileService profileService) {
    this.service = service;
    this.profileService = profileService;
  }

  @GetMapping("/submission-profiles/{channel}")
  ActiveChannelProfile profile(@PathVariable UUID tenantId, @PathVariable String channel,
      @RequestParam String quoteIntent) {
    return profileService.getActiveChannelProfile(tenantId, channel, quoteIntent);
  }

  @GetMapping("/intake-metadata")
  ScenarioIntakeMetadata intakeMetadata(@PathVariable UUID tenantId,
      @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
    return ScenarioIntakeMetadataCatalog.metadata(tenantId, correlationId);
  }

  @PostMapping
  ResponseEntity<ScenarioResponse> create(@PathVariable UUID tenantId, @RequestHeader("Idempotency-Key") String key,
      @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId, @RequestHeader(value = "X-Roles", required = false) String roles,
      @Valid @RequestBody CreateScenarioRequest request) {
    return withRoles(roles, () -> idempotentResponse(HttpStatus.CREATED, service.createDraft(tenantId, key, correlationId, request)));
  }

  @GetMapping("/{scenarioId}")
  ScenarioResponse get(@PathVariable UUID tenantId, @PathVariable UUID scenarioId) {
    return service.get(tenantId, scenarioId);
  }

  @PatchMapping("/{scenarioId}/borrowers-credit")
  ResponseEntity<BorrowerCreditResponse> borrowers(@PathVariable UUID tenantId, @PathVariable UUID scenarioId, @RequestHeader("Idempotency-Key") String key,
      @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId, @RequestHeader(value = "X-Roles", required = false) String roles,
      @RequestBody BorrowerCreditRequest request) {
    return withRoles(roles, () -> {
      BorrowerCreditResponse response = service.updateBorrowers(tenantId, scenarioId, key, correlationId, request);
      return idempotentResponse(HttpStatus.OK, response);
    });
  }

  @PatchMapping("/{scenarioId}/loan-structure")
  ResponseEntity<LoanStructureResponse> loan(@PathVariable UUID tenantId, @PathVariable UUID scenarioId, @RequestHeader("Idempotency-Key") String key,
      @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId, @RequestHeader(value = "X-Roles", required = false) String roles,
      @RequestBody LoanStructureRequest request) {
    return withRoles(roles, () -> idempotentResponse(HttpStatus.OK, service.updateLoan(tenantId, scenarioId, key, correlationId, request)));
  }

  @PatchMapping("/{scenarioId}/property")
  ResponseEntity<ScenarioResponse> property(@PathVariable UUID tenantId, @PathVariable UUID scenarioId, @RequestHeader("Idempotency-Key") String key,
      @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId, @RequestHeader(value = "X-Roles", required = false) String roles,
      @RequestBody PropertyRequest request) {
    return withRoles(roles, () -> idempotentResponse(HttpStatus.OK, service.updateProperty(tenantId, scenarioId, key, correlationId, request)));
  }

  @PatchMapping("/{scenarioId}/income-assets")
  ResponseEntity<ScenarioResponse> income(@PathVariable UUID tenantId, @PathVariable UUID scenarioId, @RequestHeader("Idempotency-Key") String key,
      @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId, @RequestHeader(value = "X-Roles", required = false) String roles,
      @RequestBody IncomeAssetRequest request) {
    return withRoles(roles, () -> idempotentResponse(HttpStatus.OK, service.updateIncomeAssets(tenantId, scenarioId, key, correlationId, request)));
  }

  @PostMapping("/{scenarioId}/normalize")
  ResponseEntity<ScenarioResponse> normalize(@PathVariable UUID tenantId, @PathVariable UUID scenarioId, @RequestHeader("Idempotency-Key") String key,
      @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId, @RequestHeader(value = "X-Roles", required = false) String roles) {
    return withRoles(roles, () -> idempotentResponse(HttpStatus.OK, service.normalize(tenantId, scenarioId, key, correlationId)));
  }

  @PostMapping("/{scenarioId}/submit")
  ResponseEntity<ScenarioResponse> submit(@PathVariable UUID tenantId, @PathVariable UUID scenarioId, @RequestHeader("Idempotency-Key") String key,
      @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId, @RequestHeader(value = "X-Roles", required = false) String roles) {
    return withRoles(roles, () -> idempotentResponse(HttpStatus.OK, service.submit(tenantId, scenarioId, key, correlationId)));
  }

  @PostMapping("/{scenarioId}/clone")
  ResponseEntity<ScenarioResponse> cloneScenario(@PathVariable UUID tenantId, @PathVariable UUID scenarioId, @RequestHeader("Idempotency-Key") String key,
      @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId, @RequestBody CloneScenarioRequest request) {
    return idempotentResponse(HttpStatus.OK, service.cloneScenario(tenantId, scenarioId, key, correlationId, request));
  }

  // S10: Scenario Replay Package API with redaction and hash verification
  @GetMapping("/{scenarioId}/replay-package")
  ReplayPackage replay(@PathVariable UUID tenantId, @PathVariable UUID scenarioId,
      @RequestParam(defaultValue = "latest") String version, @RequestParam(defaultValue = "role-default") String redaction,
      @RequestParam(defaultValue = "false") boolean export, @RequestParam(value = "accessReasonCode", required = false) String accessReasonCode,
      @RequestHeader(value = "X-Roles", required = false) String roles,
      @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
    return withRoles(roles, () -> service.replay(tenantId, scenarioId, new ScenarioReplayAccessRequest(version, redaction, export, accessReasonCode, correlationId)));
  }

  @GetMapping("/{scenarioId}/events")
  List<EventRecord> events(@PathVariable UUID tenantId, @PathVariable UUID scenarioId) {
    return service.events(tenantId, scenarioId);
  }

  private <T> T withRoles(String roles, java.util.function.Supplier<T> action) {
    try {
      RequestContext.roles(roles);
      return action.get();
    } finally {
      RequestContext.clear();
    }
  }

  private <T> ResponseEntity<T> idempotentResponse(HttpStatus status, T body) {
    ResponseEntity.BodyBuilder builder = ResponseEntity.status(status);
    if (service.wasIdempotencyReplayed()) builder.header("Idempotency-Replayed", "true");
    return builder.body(body);
  }
}
