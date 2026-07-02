package com.wcpe.underwriting.nonqm;

import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/underwriting/non-qm")
class NonQmUnderwritingController {
  private static final String TENANT_HEADER = "X-Tenant-Id";

  private final NonQmUnderwritingApi api;
  private final UnderwritingResultStore resultStore;

  @Autowired
  NonQmUnderwritingController(NonQmUnderwritingApi api, UnderwritingResultStore resultStore) {
    this.api = api;
    this.resultStore = resultStore;
  }

  NonQmUnderwritingController() {
    this(new NonQmUnderwritingApi(), UnderwritingResultStore.unavailable());
  }

  NonQmUnderwritingController(NonQmUnderwritingApi api) {
    this(api, UnderwritingResultStore.unavailable());
  }

  @PostMapping("/aus/evaluate")
  NonQmUnderwritingApi.UnderwritingResult evaluate(@RequestBody NonQmUnderwritingApi.UnderwritingRequest request) {
    NonQmUnderwritingApi.UnderwritingResult result = api.evaluate(request);
    return resultStore.save(result);
  }

  @PostMapping("/conditions/generate")
  NonQmUnderwritingApi.UnderwritingResult generateConditions(@RequestBody NonQmUnderwritingApi.UnderwritingRequest request) {
    return evaluate(request);
  }

  @GetMapping("/findings/{scenarioId}")
  ResponseEntity<NonQmUnderwritingApi.UnderwritingFindingsReport> findings(
      @RequestHeader(name = TENANT_HEADER, required = false) String tenantId,
      @PathVariable String scenarioId) {
    return resultStore.findByScenarioId(requireTenantId(tenantId), scenarioId)
        .map(result -> ResponseEntity.ok(result.findingsReport()))
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @GetMapping("/conditions/{scenarioId}")
  ResponseEntity<NonQmUnderwritingApi.UnderwritingResult> conditions(
      @RequestHeader(name = TENANT_HEADER, required = false) String tenantId,
      @PathVariable String scenarioId) {
    return resultStore.findByScenarioId(requireTenantId(tenantId), scenarioId)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  private String requireTenantId(String tenantId) {
    if (tenantId == null || tenantId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tenant_id is required for underwriting result lookup");
    }
    return tenantId;
  }
}

interface UnderwritingResultStore {
  NonQmUnderwritingApi.UnderwritingResult save(NonQmUnderwritingApi.UnderwritingResult result);

  Optional<NonQmUnderwritingApi.UnderwritingResult> findByScenarioId(String tenantId, String scenarioId);

  static UnderwritingResultStore unavailable() {
    return new UnavailableUnderwritingResultStore();
  }
}

final class UnavailableUnderwritingResultStore implements UnderwritingResultStore {
  private static final String MESSAGE = "Durable underwriting result persistence is not configured";

  @Override
  public NonQmUnderwritingApi.UnderwritingResult save(NonQmUnderwritingApi.UnderwritingResult result) {
    throw unavailable();
  }

  @Override
  public Optional<NonQmUnderwritingApi.UnderwritingResult> findByScenarioId(String tenantId, String scenarioId) {
    throw unavailable();
  }

  private ResponseStatusException unavailable() {
    return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, MESSAGE);
  }
}
