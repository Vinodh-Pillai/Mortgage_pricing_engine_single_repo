package com.wcpe.underwriting.nonqm;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/underwriting/non-qm")
class NonQmUnderwritingController {
  private final NonQmUnderwritingApi api;
  private final Map<String, NonQmUnderwritingApi.UnderwritingResult> results = new ConcurrentHashMap<>();

  NonQmUnderwritingController() {
    this(new NonQmUnderwritingApi());
  }

  NonQmUnderwritingController(NonQmUnderwritingApi api) {
    this.api = api;
  }

  @PostMapping("/aus/evaluate")
  NonQmUnderwritingApi.UnderwritingResult evaluate(@RequestBody NonQmUnderwritingApi.UnderwritingRequest request) {
    NonQmUnderwritingApi.UnderwritingResult result = api.evaluate(request);
    results.put(result.scenarioId(), result);
    return result;
  }

  @PostMapping("/conditions/generate")
  NonQmUnderwritingApi.UnderwritingResult generateConditions(@RequestBody NonQmUnderwritingApi.UnderwritingRequest request) {
    return evaluate(request);
  }

  @GetMapping("/findings/{scenarioId}")
  ResponseEntity<NonQmUnderwritingApi.UnderwritingFindingsReport> findings(@PathVariable String scenarioId) {
    NonQmUnderwritingApi.UnderwritingResult result = results.get(scenarioId);
    return result == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(result.findingsReport());
  }

  @GetMapping("/conditions/{scenarioId}")
  ResponseEntity<NonQmUnderwritingApi.UnderwritingResult> conditions(@PathVariable String scenarioId) {
    NonQmUnderwritingApi.UnderwritingResult result = results.get(scenarioId);
    return result == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(result);
  }
}
