package com.wcpe.scenario.domain;

import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/scenario-imports")
class BatchScenarioController {
  private final BatchImportService service;
  private final BatchImportRepository repository;

  BatchScenarioController(BatchImportService service, BatchImportRepository repository) {
    this.service = service;
    this.repository = repository;
  }

  @PostMapping(consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
  ResponseEntity<ImportJobResponse> upload(@PathVariable UUID tenantId,
      @RequestHeader("Idempotency-Key") String key,
      @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
      @RequestHeader(value = "X-Roles", required = false) String roles,
      @RequestHeader(value = "X-Actor-Id", defaultValue = "system") String actorId,
      @RequestParam("file") MultipartFile file,
      @RequestParam("templateVersion") String templateVersion,
      @RequestParam("channel") String channel,
      @RequestParam("quoteIntent") String quoteIntent,
      @RequestParam(value = "partialSuccessPolicy", defaultValue = "ALLOW_VALID_ROWS") String partialSuccessPolicy) {
    PartialSuccessPolicy parsedPolicy = PartialSuccessPolicy.ALLOW_VALID_ROWS;
    try { parsedPolicy = PartialSuccessPolicy.valueOf(partialSuccessPolicy); } catch (IllegalArgumentException ignored) {}
    final PartialSuccessPolicy policy = parsedPolicy;
    return withRoles(roles, () -> ResponseEntity.status(HttpStatus.ACCEPTED).body(
        service.upload(file, templateVersion, channel, quoteIntent, policy, tenantId, key, correlationId, actorId)));
  }

  @PostMapping
  BatchImportResponse importBatch(@PathVariable UUID tenantId, @RequestHeader("Idempotency-Key") String key,
      @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId, @RequestBody BatchImportRequest request) {
    return service.importBatchOld(tenantId, key, correlationId, request);
  }

  @GetMapping("/{jobId}")
  ImportJobStatusResponse getStatus(@PathVariable UUID tenantId, @PathVariable UUID jobId) {
    return service.getJobStatus(tenantId, jobId);
  }

  @GetMapping("/{jobId}/rows")
  List<ImportRow> getRows(@PathVariable UUID tenantId, @PathVariable UUID jobId) {
    return service.getJobRows(tenantId, jobId);
  }

  @GetMapping("/{jobId}/errors")
  List<ImportError> getErrors(@PathVariable UUID tenantId, @PathVariable UUID jobId) {
    return service.getJobErrors(tenantId, jobId);
  }

  private <T> T withRoles(String roles, java.util.function.Supplier<T> action) {
    try {
      RequestContext.roles(roles);
      return action.get();
    } finally {
      RequestContext.clear();
    }
  }
}
