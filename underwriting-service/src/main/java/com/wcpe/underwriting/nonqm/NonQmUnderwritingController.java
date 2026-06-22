package com.wcpe.underwriting.nonqm;

import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/underwriting/non-qm")
class NonQmUnderwritingController {
  private final NonQmUnderwritingApi api;
  private final UnderwritingResultStore resultStore;

  NonQmUnderwritingController() {
    this(new NonQmUnderwritingApi(), UnderwritingResultStore.unavailable());
  }

  NonQmUnderwritingController(NonQmUnderwritingApi api) {
    this(api, UnderwritingResultStore.unavailable());
  }

  NonQmUnderwritingController(NonQmUnderwritingApi api, UnderwritingResultStore resultStore) {
    this.api = api;
    this.resultStore = resultStore;
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
  ResponseEntity<NonQmUnderwritingApi.UnderwritingFindingsReport> findings(@PathVariable String scenarioId) {
    return resultStore.findByScenarioId(scenarioId)
        .map(result -> ResponseEntity.ok(result.findingsReport()))
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @GetMapping("/conditions/{scenarioId}")
  ResponseEntity<NonQmUnderwritingApi.UnderwritingResult> conditions(@PathVariable String scenarioId) {
    return resultStore.findByScenarioId(scenarioId)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }
}

interface UnderwritingResultStore {
  NonQmUnderwritingApi.UnderwritingResult save(NonQmUnderwritingApi.UnderwritingResult result);

  Optional<NonQmUnderwritingApi.UnderwritingResult> findByScenarioId(String scenarioId);

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
  public Optional<NonQmUnderwritingApi.UnderwritingResult> findByScenarioId(String scenarioId) {
    throw unavailable();
  }

  private ResponseStatusException unavailable() {
    return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, MESSAGE);
  }
}
