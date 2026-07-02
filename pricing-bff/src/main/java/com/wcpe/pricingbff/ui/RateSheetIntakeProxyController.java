package com.wcpe.pricingbff.ui;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/rate-sheets/uploads")
class RateSheetIntakeProxyController {
  private static final String PDF_OCR_BLOCKED_MESSAGE =
      "PDF/OCR rate sheet intake requires an approved external document extractor/OCR handoff. This repository currently exposes rate-feed OCR review contracts but no PDF table extraction adapter, so PDF upload is blocked before parser execution.";
  private static final String REQUIRED_METADATA_MESSAGE =
      "CSV rate sheet upload requires investorId, channelId, productCode, and effectiveAt supplied by the user before forwarding to rate-feed-service.";

  private final RestClient restClient;
  private final String baseUrl;

  RateSheetIntakeProxyController(RestClient.Builder restClientBuilder,
      @Value("${loanweft.integrations.rate-feed-service.base-url:${RATE_FEED_SERVICE_BASE_URL:}}") String baseUrl) {
    this.restClient = restClientBuilder.build();
    this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  ResponseEntity<RateSheetUploadView> upload(@PathVariable String tenantId,
      @RequestParam("file") MultipartFile file,
      @RequestParam(value = "sourceHash", required = false) String sourceHash,
      @RequestParam(value = "investorId", required = false) String investorId,
      @RequestParam(value = "channelId", required = false) String channelId,
      @RequestParam(value = "productCode", required = false) String productCode,
      @RequestParam(value = "effectiveAt", required = false) String effectiveAt,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    String extension = extension(file.getOriginalFilename());
    String traceId = trace(uiTraceId);
    if ("pdf".equals(extension)) {
      return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(blocked("PDF_OCR_EXTERNAL_EXTRACTOR_REQUIRED", PDF_OCR_BLOCKED_MESSAGE, sourceHash, traceId));
    }
    if (SetLike.contains(extension, "xlsx", "xlsm")) {
      return analyzeStructure(tenantId, file, sourceHash, traceId);
    }
    if (!"csv".equals(extension)) {
      return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(blocked("UNSUPPORTED_RATE_SHEET_FORMAT", "Supported repo-backed formats are CSV parser upload and XLSX/XLSM structural/profile analysis. PDF/OCR remains externally blocked.", sourceHash, traceId));
    }
    if (blank(investorId) || blank(channelId) || blank(productCode) || blank(effectiveAt)) {
      return ResponseEntity.badRequest().body(blocked("RATE_SHEET_METADATA_REQUIRED", REQUIRED_METADATA_MESSAGE, sourceHash, traceId));
    }
    requireConfigured();
    try {
      JsonNode imported = importCsv(tenantId, file, investorId, channelId, productCode, effectiveAt, traceId);
      String sheetId = text(imported, "sheetId");
      int version = imported.path("version").asInt(1);
      JsonNode validation = validateSheet(tenantId, sheetId, traceId);
      JsonNode grid = grid(tenantId, sheetId, version, traceId);
      return ResponseEntity.status(HttpStatus.CREATED).body(uploadView(sheetId, imported, validation, grid, productCode, sourceHash, traceId));
    } catch (RestClientException | IllegalArgumentException ex) {
      return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(blocked("RATE_FEED_SERVICE_UNAVAILABLE", "rate-feed-service upload/parse/validate API could not be reached or normalized; pricing-bff did not synthesize parser rows.", sourceHash, traceId));
    }
  }

  @GetMapping("/{uploadId}/validation")
  ResponseEntity<RateSheetUploadView> validation(@PathVariable String tenantId, @PathVariable String uploadId,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    requireConfigured();
    String traceId = trace(uiTraceId);
    try {
      JsonNode detail = sheetDetail(tenantId, uploadId, traceId);
      int version = detail.path("version").asInt(1);
      JsonNode validation = tryValidateSheet(tenantId, uploadId, traceId);
      JsonNode grid = grid(tenantId, uploadId, version, traceId);
      return ResponseEntity.ok(uploadView(uploadId, detail, validation, grid, text(detail, "productCode"), text(detail, "gridHash"), traceId));
    } catch (RestClientException | IllegalArgumentException ex) {
      return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(blocked("RATE_FEED_SERVICE_VALIDATION_UNAVAILABLE", "rate-feed-service validation/grid API could not be reached or normalized; pricing-bff did not synthesize validation rows.", "", traceId));
    }
  }

  @PostMapping("/{uploadId}/publish")
  ResponseEntity<RateSheetPublishView> publish(@PathVariable String tenantId, @PathVariable String uploadId,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId,
      @RequestBody(required = false) Map<String, Object> request) {
    requireConfigured();
    String traceId = trace(uiTraceId);
    try {
      String approvalReference = Optional.ofNullable(request == null ? null : request.get("approvalReference"))
          .map(Object::toString).filter(value -> !value.isBlank()).orElse("pricing-workbench-rate-sheet-intake");
      String expectedValidationResultHash = text(request, "expectedValidationResultHash");
      String expectedVersionHash = text(request, "expectedVersionHash");
      if (!blank(expectedValidationResultHash) && !blank(expectedVersionHash)) {
        JsonNode published = restClient.post()
            .uri(URI.create(baseUrl + "/api/v1/tenants/" + tenantPath(tenantId) + "/rate-sheet-versions/" + uploadId + "/publish"))
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Roles", "RATE_FEED_ACTIVATE")
            .header("X-Actor-Id", "pricing-bff-rate-sheet-intake")
            .header("X-Correlation-Id", traceId)
            .header("X-Idempotency-Key", "rate-sheet-publish:" + uploadId)
            .body(Map.of("expectedValidationResultHash", expectedValidationResultHash, "expectedVersionHash", expectedVersionHash, "publishAt", Instant.now().toString()))
            .retrieve()
            .body(JsonNode.class);
        return ResponseEntity.ok(new RateSheetPublishView(status(published, "PUBLISHED"), "Rate sheet publish was accepted by rate-feed-service.", refs("rate-feed:publish:" + uploadId, text(published, "resultHash"))));
      }
      JsonNode activated = restClient.post()
          .uri(URI.create(baseUrl + "/api/v1/tenants/" + tenantPath(tenantId) + "/rate-sheets/" + uploadId + "/activate"))
          .contentType(MediaType.APPLICATION_JSON)
          .header("X-Roles", "RATE_FEED_ACTIVATE")
          .header("X-Actor-Id", "pricing-bff-rate-sheet-intake")
          .header("X-Correlation-Id", traceId)
          .body(Map.of("idempotencyKey", "rate-sheet-publish:" + uploadId, "approvalReference", approvalReference, "notes", "Published through pricing-workbench rate sheet intake."))
          .retrieve()
          .body(JsonNode.class);
      return ResponseEntity.ok(new RateSheetPublishView(status(activated, "PUBLISHED"), "Rate sheet activation was accepted by rate-feed-service.", refs("rate-feed:activate:" + uploadId, text(activated, "gridHash"))));
    } catch (RestClientException | IllegalArgumentException ex) {
      return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new RateSheetPublishView("BLOCKED", "rate-feed-service publish API could not be reached or normalized; pricing-bff did not synthesize activation.", List.of("RATE_FEED_PUBLISH_UNAVAILABLE")));
    }
  }

  private ResponseEntity<RateSheetUploadView> analyzeStructure(String tenantId, MultipartFile file, String sourceHash, String traceId) {
    requireConfigured();
    try {
      JsonNode proposal = restClient.post()
          .uri(URI.create(baseUrl + "/api/v1/tenants/" + tenantPath(tenantId) + "/mapping-wizard/analyze?mode=HEURISTIC"))
          .contentType(MediaType.MULTIPART_FORM_DATA)
          .header("X-Roles", "RATE_FEED_NORMALIZE,RATE_FEED_VIEW")
          .header("X-Actor-Id", "pricing-bff-rate-sheet-intake")
          .header("X-Correlation-Id", traceId)
          .body(fileBody(file))
          .retrieve()
          .body(JsonNode.class);
      JsonNode profileMatch = autoMatch(tenantId, proposal, traceId);
      StructuralAnalysisView structural = new StructuralAnalysisView(status(proposal, "STRUCTURAL_ANALYSIS_READY"), text(proposal, "formatType"), proposal.path("confidence").asText("LOW"), proposal.path("sourcePreview"), proposal.path("mappings"), profileMatch);
      return ResponseEntity.ok(new RateSheetUploadView("structural:" + safeSourceId(file.getOriginalFilename(), sourceHash), "STRUCTURAL_ANALYSIS_READY", valueOr(sourceHash, text(proposal.path("audit"), "responseHash")), "", "", List.of(), List.of(), false, refs("rate-feed:mapping-wizard:analyze", text(proposal.path("audit"), "responseHash")), traceId, structural));
    } catch (RestClientException | IllegalArgumentException ex) {
      return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(blocked("RATE_FEED_PROFILE_ANALYSIS_UNAVAILABLE", "rate-feed-service XLSX/XLSM structural/profile analysis API could not be reached or normalized; pricing-bff did not invent parser mappings.", sourceHash, traceId));
    }
  }

  private JsonNode importCsv(String tenantId, MultipartFile file, String investorId, String channelId, String productCode, String effectiveAt, String traceId) {
    return restClient.post()
        .uri(URI.create(baseUrl + "/api/v1/tenants/" + tenantPath(tenantId) + "/rate-sheets/import"))
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .header("X-Roles", "RATE_FEED_UPLOAD")
        .header("X-Actor-Id", "pricing-bff-rate-sheet-intake")
        .header("X-Correlation-Id", traceId)
        .body(fileBody(file, Map.of("investorId", investorId, "channelId", channelId, "productCode", productCode, "effectiveAt", effectiveAt)))
        .retrieve()
        .body(JsonNode.class);
  }

  private JsonNode validateSheet(String tenantId, String sheetId, String traceId) {
    return restClient.post()
        .uri(URI.create(baseUrl + "/api/v1/tenants/" + tenantPath(tenantId) + "/rate-sheets/" + sheetId + "/validate"))
        .contentType(MediaType.APPLICATION_JSON)
        .header("X-Roles", "RATE_FEED_UPLOAD")
        .header("X-Actor-Id", "pricing-bff-rate-sheet-intake")
        .header("X-Correlation-Id", traceId)
        .body(Map.of("idempotencyKey", "rate-sheet-validate:" + sheetId, "strict", true))
        .retrieve()
        .body(JsonNode.class);
  }

  private JsonNode tryValidateSheet(String tenantId, String sheetId, String traceId) {
    try { return validateSheet(tenantId, sheetId, traceId); }
    catch (RestClientException ex) { return JsonNodeFactories.emptyValidation(sheetId); }
  }

  private JsonNode grid(String tenantId, String sheetId, int version, String traceId) {
    return restClient.get()
        .uri(URI.create(baseUrl + "/api/v1/tenants/" + tenantPath(tenantId) + "/rates/" + sheetId + "/" + version + "/grid"))
        .header("X-Roles", "RATE_FEED_VIEW")
        .header("X-Actor-Id", "pricing-bff-rate-sheet-intake")
        .header("X-Correlation-Id", traceId)
        .retrieve()
        .body(JsonNode.class);
  }

  private JsonNode sheetDetail(String tenantId, String sheetId, String traceId) {
    return restClient.get()
        .uri(URI.create(baseUrl + "/api/v1/tenants/" + tenantPath(tenantId) + "/rate-sheets/" + sheetId))
        .header("X-Roles", "RATE_FEED_VIEW")
        .header("X-Actor-Id", "pricing-bff-rate-sheet-intake")
        .header("X-Correlation-Id", traceId)
        .retrieve()
        .body(JsonNode.class);
  }

  private JsonNode autoMatch(String tenantId, JsonNode proposal, String traceId) {
    JsonNode fingerprint = proposal == null ? null : proposal.path("formatFingerprint");
    if (fingerprint == null || fingerprint.isMissingNode() || fingerprint.isNull()) return null;
    try {
      return restClient.post()
          .uri(URI.create(baseUrl + "/api/v1/tenants/" + tenantPath(tenantId) + "/mapping-wizard/profiles/auto-match"))
          .contentType(MediaType.APPLICATION_JSON)
          .header("X-Roles", "RATE_FEED_VIEW")
          .header("X-Actor-Id", "pricing-bff-rate-sheet-intake")
          .header("X-Correlation-Id", traceId)
          .body(fingerprint)
          .retrieve()
          .body(JsonNode.class);
    } catch (RuntimeException ignored) { return null; }
  }

  private RateSheetUploadView uploadView(String uploadId, JsonNode source, JsonNode validation, JsonNode grid, String productCode, String sourceHash, String traceId) {
    List<RateSheetParsedRowView> rows = new ArrayList<>();
    JsonNode points = grid.path("pricePoints");
    if (points.isArray()) {
      for (JsonNode point : points) {
        int row = point.path("gridPosition").asInt(rows.size()) + 1;
        rows.add(new RateSheetParsedRowView(uploadId + ":row:" + row, row, valueOr(productCode, "product-ref-unavailable"), "noteRate:" + point.path("noteRate").asText("") + ":lockPeriod:" + point.path("lockPeriod").asText(""), status(validation, "VALIDATED"), List.of()));
      }
    }
    List<RateSheetValidationIssueView> issues = validationIssues(validation);
    boolean publishReady = !rows.isEmpty() && issues.stream().noneMatch(issue -> "BLOCKING".equals(issue.severity()));
    String status = publishReady ? "VALIDATED" : status(validation, rows.isEmpty() ? "BLOCKED" : "NEEDS_ATTENTION");
    String validationResultHash = valueOr(text(validation, "resultHash"), text(validation.path("validationResult"), "resultHash"));
    String versionHash = text(source, "resultHash");
    return new RateSheetUploadView(uploadId, status, valueOr(sourceHash, text(grid, "gridHash")), validationResultHash, versionHash, rows, issues, publishReady, refs("rate-feed:sheet:" + uploadId, text(source, "resultHash"), validationResultHash, text(grid, "gridHash")), traceId, null);
  }

  private List<RateSheetValidationIssueView> validationIssues(JsonNode validation) {
    List<RateSheetValidationIssueView> issues = new ArrayList<>();
    JsonNode result = validation.path("validationResult");
    JsonNode errors = result.path("errors");
    if (errors.isArray()) {
      for (JsonNode error : errors) issues.add(new RateSheetValidationIssueView(nullableInt(error, "row"), valueOr(text(error, "field"), "row"), "BLOCKING", valueOr(text(error, "message"), text(error, "code"))));
    }
    JsonNode warnings = result.path("warnings");
    if (warnings.isArray()) {
      for (JsonNode warning : warnings) issues.add(new RateSheetValidationIssueView(null, "row", "WARNING", valueOr(text(warning, "message"), text(warning, "code"))));
    }
    return issues;
  }

  private MultiValueMap<String, Object> fileBody(MultipartFile file) { return fileBody(file, Map.of()); }

  private MultiValueMap<String, Object> fileBody(MultipartFile file, Map<String, String> fields) {
    try {
      MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
      body.add("file", new NamedByteArrayResource(file.getBytes(), valueOr(file.getOriginalFilename(), "rate-sheet-upload")));
      fields.forEach(body::add);
      return body;
    } catch (Exception ex) {
      throw new IllegalArgumentException("Unable to read rate sheet upload bytes.", ex);
    }
  }

  private void requireConfigured() {
    if (baseUrl.isBlank()) throw new RateFeedProxyNotConfiguredException();
  }

  @org.springframework.web.bind.annotation.ExceptionHandler(RateFeedProxyNotConfiguredException.class)
  ResponseEntity<RateSheetUploadView> notConfigured(RateFeedProxyNotConfiguredException ex) {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(blocked("RATE_FEED_SERVICE_BASE_URL_REQUIRED", "Configure loanweft.integrations.rate-feed-service.base-url or RATE_FEED_SERVICE_BASE_URL before rate sheet UI operations can call rate-feed-service.", "", "rate-sheet-live-intake-ui"));
  }

  private static RateSheetUploadView blocked(String code, String message, String sourceHash, String traceId) {
    return new RateSheetUploadView("", "BLOCKED", valueOr(sourceHash, ""), "", "", List.of(), List.of(new RateSheetValidationIssueView(null, "file", "BLOCKING", message)), false, List.of(code), traceId, null);
  }

  private static String tenantPath(String tenantId) {
    String normalized = tenantId == null ? "" : tenantId.trim();
    if (normalized.isBlank()) throw new IllegalArgumentException("Tenant context is required.");
    try { return UUID.fromString(normalized).toString(); }
    catch (IllegalArgumentException ex) { return UUID.nameUUIDFromBytes(("rate-sheet-intake:" + normalized).getBytes(StandardCharsets.UTF_8)).toString(); }
  }

  private static String extension(String fileName) {
    String normalized = fileName == null ? "" : fileName.trim().toLowerCase(Locale.ROOT);
    int dot = normalized.lastIndexOf('.');
    return dot < 0 ? "" : normalized.substring(dot + 1);
  }

  private static String trace(String trace) { return blank(trace) ? "rate-sheet-live-intake-ui" : trace.trim(); }
  private static boolean blank(String value) { return value == null || value.trim().isBlank(); }
  private static String text(JsonNode node, String field) { return node == null || node.path(field).isMissingNode() || node.path(field).isNull() ? "" : node.path(field).asText(""); }
  private static String text(Map<String, Object> map, String field) { Object value = map == null ? null : map.get(field); return value == null ? "" : value.toString().trim(); }
  private static String status(JsonNode node, String fallback) { return valueOr(text(node, "status"), fallback); }
  private static Integer nullableInt(JsonNode node, String field) { return node.path(field).isInt() ? node.path(field).asInt() : null; }
  private static String valueOr(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
  private static String safeSourceId(String fileName, String sourceHash) { return (valueOr(sourceHash, valueOr(fileName, "upload"))).replaceAll("[^A-Za-z0-9._:-]", "-"); }
  private static List<String> refs(String... refs) { return java.util.Arrays.stream(refs).filter(ref -> ref != null && !ref.isBlank()).toList(); }

  record RateSheetUploadView(String uploadId, String status, String sourceHash, String validationResultHash, String versionHash, List<RateSheetParsedRowView> parsedRows,
      List<RateSheetValidationIssueView> validationIssues, boolean publishReady, List<String> auditRefs, String uiTraceId,
      StructuralAnalysisView structuralAnalysis) {}
  record RateSheetParsedRowView(String rowId, Integer rowNumber, String productRef, String rateRef, String status,
      List<RateSheetValidationIssueView> validationIssues) {}
  record RateSheetValidationIssueView(Integer rowNumber, String column, String severity, String message) {}
  record RateSheetPublishView(String status, String message, List<String> auditRefs) {}
  record StructuralAnalysisView(String status, String formatType, String confidence, JsonNode sourcePreview, JsonNode mappings,
      JsonNode profileMatch) {}

  private static final class RateFeedProxyNotConfiguredException extends RuntimeException {}
  private static final class SetLike { static boolean contains(String value, String left, String right) { return left.equals(value) || right.equals(value); } }
  private static final class NamedByteArrayResource extends ByteArrayResource {
    private final String filename;
    NamedByteArrayResource(byte[] byteArray, String filename) { super(byteArray); this.filename = filename; }
    @Override public String getFilename() { return filename; }
  }
  private static final class JsonNodeFactories {
    static JsonNode emptyValidation(String sheetId) { return com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode().put("sheetId", sheetId).put("status", "VALIDATED").set("validationResult", com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode().put("valid", true)); }
  }
}
