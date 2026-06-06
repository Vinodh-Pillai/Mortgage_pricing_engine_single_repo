package com.wcpe.scenario.domain;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
class BatchImportService {
  private static final Set<String> WRITER_ROLES = Set.of("SCENARIO_WRITER", "SCENARIO_ADMIN");
  private static final Set<String> KNOWN_COLUMNS = Set.of("scenario_name", "external_loan_id", "source_system",
      "property_state", "property_zip", "property_type", "occupancy_type",
      "units", "purchase_price", "credit_score", "credit_status", "credit_score_source",
      "monthly_income", "monthly_debt", "liquid_assets", "loan_amount", "term_months");
  private static final Set<String> REQUIRED_COLUMNS = Set.of("scenario_name", "external_loan_id", "source_system");
  private static final Set<String> ALLOWED_MIME_TYPES = Set.of("text/csv", "application/csv");
  private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

  private static final char[] CSV_INJECTION_PREFIXES = {'=', '+', '-', '@', '`'};

  private final BatchImportRepository importRepository;
  private final ScenarioRepository scenarioRepository;
  private final ScenarioService scenarioService;

  BatchImportService(BatchImportRepository importRepository, ScenarioRepository scenarioRepository, ScenarioService scenarioService) {
    this.importRepository = importRepository;
    this.scenarioRepository = scenarioRepository;
    this.scenarioService = scenarioService;
  }

  ImportJobResponse upload(MultipartFile file, String templateVersion, String channel, String quoteIntent,
      PartialSuccessPolicy policy, UUID tenantId, String idempotencyKey, String correlationId, String submittedBy) {
    requireRole("SCENARIO_WRITER", WRITER_ROLES);
    requireIdempotencyKey(idempotencyKey);
    validateFileUpload(file);
    String fileHash = fileHash(file);
    Map<String, Object> requestIdentity = Map.of("fileName", Optional.ofNullable(file.getOriginalFilename()).orElse(""),
        "fileHash", fileHash, "templateVersion", templateVersion, "channel", channel, "quoteIntent", quoteIntent,
        "partialSuccessPolicy", policy.name());
    Optional<Object> replay = scenarioRepository.idempotent(tenantId.toString() + ":import", idempotencyKey, requestIdentity);
    if (replay.isPresent()) return (ImportJobResponse) replay.get();
    List<String[]> parsedRows = parseCsv(file);
    String[] headers = parsedRows.isEmpty() ? new String[0] : parsedRows.get(0);
    int dataRows = Math.max(0, parsedRows.size() - 1);
    UUID jobId = importRepository.createJob(tenantId, file.getOriginalFilename(), fileHash,
        templateVersion, channel, quoteIntent, policy, submittedBy, dataRows);
    if (!isTemplateSupported(templateVersion)) {
      importRepository.markJobComplete(tenantId, jobId, ImportJobStatus.REJECTED_TEMPLATE_MISMATCH, Instant.now(), 0, 0);
      emitImportCompleted(tenantId, jobId, ImportJobStatus.REJECTED_TEMPLATE_MISMATCH, 0, 0, correlationId, fileHash, templateVersion);
      ImportJobResponse response = new ImportJobResponse(jobId, ImportJobStatus.REJECTED_TEMPLATE_MISMATCH, templateVersion, dataRows, Map.of(
          "status", "/api/v1/tenants/" + tenantId + "/scenario-imports/" + jobId,
          "ui", "/pricing/scenario-imports/" + jobId));
      scenarioRepository.remember(tenantId.toString() + ":import", idempotencyKey, requestIdentity, response);
      return response;
    }
    validateHeaders(headers);
    ImportJobResponse response = new ImportJobResponse(jobId, ImportJobStatus.QUEUED, templateVersion, dataRows, Map.of(
        "status", "/api/v1/tenants/" + tenantId + "/scenario-imports/" + jobId,
        "ui", "/pricing/scenario-imports/" + jobId));
    processRows(tenantId, jobId, headers, parsedRows.subList(1, parsedRows.size()), channel, quoteIntent, policy, correlationId, fileHash, templateVersion);
    scenarioRepository.remember(tenantId.toString() + ":import", idempotencyKey, requestIdentity, response);
    return response;
  }

  ImportJobStatusResponse getJobStatus(UUID tenantId, UUID jobId) {
    ImportJob job = importRepository.getJob(tenantId, jobId);
    return new ImportJobStatusResponse(job.importJobId(), job.status(), job.templateVersion(),
        job.totalRows(), job.createdRows(), job.failedRows(), job.startedAtUtc(), job.completedAtUtc());
  }

  List<ImportRow> getJobRows(UUID tenantId, UUID jobId) {
    return importRepository.getRows(tenantId, jobId);
  }

  List<ImportError> getJobErrors(UUID tenantId, UUID jobId) {
    return importRepository.getErrors(tenantId, jobId);
  }

  // Backward compatibility: delegates to ScenarioService.importBatch for JSON-based batch import
  BatchImportResponse importBatchOld(UUID tenantId, String key, String correlationId, BatchImportRequest request) {
    return scenarioService.importBatch(tenantId, key, correlationId, request);
  }

  List<String[]> parseCsv(MultipartFile file) {
    List<String[]> rows = new ArrayList<>();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
      String line;
      while ((line = reader.readLine()) != null) {
        rows.add(parseCsvLine(line));
      }
    } catch (IOException ex) {
      throw new ScenarioException(org.springframework.http.HttpStatus.BAD_REQUEST, "CSV_PARSE_ERROR",
          "Failed to parse CSV file.", List.of());
    }
    return rows;
  }

  String[] parseCsvLine(String line) {
    List<String> fields = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    boolean inQuotes = false;
    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);
      if (inQuotes) {
        if (c == '"') {
          if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
            current.append('"');
            i++;
          } else {
            inQuotes = false;
          }
        } else {
          current.append(c);
        }
      } else if (c == '"') {
        inQuotes = true;
      } else if (c == ',') {
        fields.add(current.toString().trim());
        current.setLength(0);
      } else {
        current.append(c);
      }
    }
    fields.add(current.toString().trim());
    for (int i = 0; i < fields.size(); i++) fields.set(i, neutralizeCsvInjection(fields.get(i)));
    return fields.toArray(String[]::new);
  }

  String[] parseLine(String line) {
    return parseCsvLine(line);
  }

  private void processRows(UUID tenantId, UUID jobId, String[] headers, List<String[]> rows, String channel, String quoteIntent,
      PartialSuccessPolicy policy, String correlationId, String fileHash, String templateVersion) {
    Instant startedAt = Instant.now();
    importRepository.startJob(tenantId, jobId, startedAt);
    int created = 0, failed = 0;

    if (policy == PartialSuccessPolicy.REJECT_ALL_ON_ANY_ERROR) {
      int prevalidationFailures = recordPrevalidationFailures(tenantId, jobId, headers, rows);
      if (prevalidationFailures > 0) {
        importRepository.markJobComplete(tenantId, jobId, ImportJobStatus.FAILED, Instant.now(), 0, prevalidationFailures);
        emitImportCompleted(tenantId, jobId, ImportJobStatus.FAILED, 0, prevalidationFailures, correlationId, fileHash, templateVersion);
        return;
      }
    }

    for (int i = 0; i < rows.size(); i++) {
      int rowNum = i + 2; // header is row 1, first data row is 2
      String[] cols = rows.get(i);
      String rowHash = Hashing.sha256(rowNum + ":" + String.join("|", rows.get(i)));
      String rowIdempotencyKey = jobId + ":row:" + rowNum;

      try {
        CreateScenarioRequest request = mapRowToRequest(headers, cols, channel, quoteIntent);
        if (request == null) throw new ScenarioException(org.springframework.http.HttpStatus.BAD_REQUEST, "INVALID_ROW", "Row " + rowNum + " could not be mapped.", List.of());
        List<ValidationIssue> issues = validateRow(headers, cols);
        if (!issues.isEmpty() && issues.stream().anyMatch(iss -> iss.severity() == Severity.BLOCKING)) {
          if (policy == PartialSuccessPolicy.REJECT_ALL_ON_ANY_ERROR) {
            UUID rowId = importRepository.addRow(tenantId, jobId, rowNum, rowHash, ImportRowStatus.FAILED_VALIDATION, null, rowIdempotencyKey);
            for (ValidationIssue issue : issues) {
              importRepository.addError(tenantId, rowId, issue.fieldPath(), issue.code(), issue.message(), "");
            }
            importRepository.markJobComplete(tenantId, jobId, ImportJobStatus.FAILED, Instant.now(), 0, failed + 1);
            emitImportCompleted(tenantId, jobId, ImportJobStatus.FAILED, 0, failed + 1, correlationId, fileHash, templateVersion);
            return;
          }
          UUID rowId = importRepository.addRow(tenantId, jobId, rowNum, rowHash, ImportRowStatus.FAILED_VALIDATION, null, rowIdempotencyKey);
          for (ValidationIssue issue : issues) {
            importRepository.addError(tenantId, rowId, issue.fieldPath(), issue.code(), issue.message(), "");
          }
          failed++;
          continue;
        }
        try {
          ScenarioResponse result = scenarioService.createDraft(tenantId, rowIdempotencyKey, correlationId, request);
          importRepository.addRow(tenantId, jobId, rowNum, rowHash, ImportRowStatus.CREATED, result.scenarioId(), rowIdempotencyKey);
          created++;
        } catch (ScenarioException ex) {
          ImportRowStatus status = ex.fieldErrors().isEmpty() ? ImportRowStatus.SYSTEM_FAILED : ImportRowStatus.FAILED_VALIDATION;
          UUID rowId = importRepository.addRow(tenantId, jobId, rowNum, rowHash, status, null, rowIdempotencyKey);
          for (ValidationIssue issue : ex.fieldErrors()) {
            importRepository.addError(tenantId, rowId, issue.fieldPath(), issue.code(), issue.message(), "");
          }
          failed++;
        }
      } catch (Exception ex) {
        importRepository.addRow(tenantId, jobId, rowNum, rowHash, ImportRowStatus.SYSTEM_FAILED, null, rowIdempotencyKey);
        failed++;
      }
    }

    ImportJobStatus finalStatus = failed > 0 && created == 0 ? ImportJobStatus.FAILED : ImportJobStatus.COMPLETED;
    importRepository.markJobComplete(tenantId, jobId, finalStatus, Instant.now(), created, failed);
    emitImportCompleted(tenantId, jobId, finalStatus, created, failed, correlationId, fileHash, templateVersion);
  }

  CreateScenarioRequest mapRowToRequest(String[] headers, String[] cols, String channel, String quoteIntent) {
    if (headers == null || cols == null) return null;
    Map<String, String> row = rowByHeader(headers, cols);
    Map<String, Object> facts = new LinkedHashMap<>();
    facts.put("channel", channel);
    facts.put("quoteIntent", quoteIntent);
    for (String column : KNOWN_COLUMNS) {
      if (row.containsKey(column)) facts.put(column, row.get(column));
    }
    return new CreateScenarioRequest(quoteIntent, channel,
        row.get("scenario_name"), row.get("external_loan_id"),
        row.get("source_system") != null ? row.get("source_system") : "BATCH_IMPORT", facts);
  }

  private List<ValidationIssue> validateRow(String[] headers, String[] cols) {
    List<ValidationIssue> issues = new ArrayList<>();
    if (cols == null) issues.add(new ValidationIssue("INSUFFICIENT_COLUMNS", "_row", Severity.BLOCKING, "Row has insufficient columns."));
    Map<String, String> row = rowByHeader(headers, cols);
    for (String required : REQUIRED_COLUMNS) {
      if (row.get(required) == null || row.get(required).isBlank()) issues.add(new ValidationIssue("MISSING_REQUIRED_VALUE", required, Severity.BLOCKING, "Required CSV value is missing."));
    }
    return issues;
  }

  private void validateFileUpload(MultipartFile file) {
    if (file == null || file.isEmpty()) throw new ScenarioException(org.springframework.http.HttpStatus.UNSUPPORTED_MEDIA_TYPE,
        "UNSUPPORTED_FILE_TYPE", "File is required.", List.of());
    if (file.getSize() > MAX_FILE_SIZE) throw new ScenarioException(org.springframework.http.HttpStatus.BAD_REQUEST,
        "FILE_TOO_LARGE", "File must not exceed 10MB.", List.of());
    String contentType = file.getContentType();
    if (contentType != null && !ALLOWED_MIME_TYPES.contains(contentType.toLowerCase())) {
      String name = Optional.ofNullable(file.getOriginalFilename()).orElse("");
      if (!name.toLowerCase(Locale.ROOT).endsWith(".csv")) throw new ScenarioException(
          org.springframework.http.HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE_TYPE",
          "Only CSV files are accepted.", List.of());
    }
  }

  private void validateTemplate(String templateVersion) {
    if (!isTemplateSupported(templateVersion)) {
      throw new ScenarioException(org.springframework.http.HttpStatus.UNSUPPORTED_MEDIA_TYPE,
          "TEMPLATE_MISMATCH", "Only template version scenario-import-v1 is supported.", List.of());
    }
  }

  private boolean isTemplateSupported(String templateVersion) {
    return "scenario-import-v1".equals(templateVersion);
  }

  private void validateHeaders(String[] headers) {
    if (headers == null || headers.length == 0) throw new ScenarioException(HttpStatus.BAD_REQUEST,
        "INVALID_CSV_HEADER", "CSV header row is required.", List.of());
    Set<String> seen = new LinkedHashSet<>();
    List<ValidationIssue> issues = new ArrayList<>();
    for (String header : headers) {
      String normalized = Optional.ofNullable(header).orElse("").trim().toLowerCase(Locale.ROOT);
      if (normalized.isBlank()) continue;
      seen.add(normalized);
      if (!KNOWN_COLUMNS.contains(normalized)) issues.add(new ValidationIssue("UNKNOWN_COLUMN", normalized,
          Severity.BLOCKING, "CSV column is not part of scenario-import-v1."));
    }
    for (String required : REQUIRED_COLUMNS) {
      if (!seen.contains(required)) issues.add(new ValidationIssue("MISSING_REQUIRED_COLUMN", required,
          Severity.BLOCKING, "CSV column is required by scenario-import-v1."));
    }
    if (!issues.isEmpty()) throw new ScenarioException(HttpStatus.BAD_REQUEST, "INVALID_CSV_HEADER",
        "CSV header does not match scenario-import-v1.", issues);
  }

  private static String getSafe(String[] cols, int idx) {
    if (idx < cols.length && cols[idx] != null) {
      String val = cols[idx].trim();
      if (isCsvInjection(val)) return neutralizeCsvInjection(val);
      return val;
    }
    return null;
  }

  private Map<String, String> rowByHeader(String[] headers, String[] cols) {
    Map<String, String> row = new LinkedHashMap<>();
    if (headers == null || cols == null) return row;
    for (int i = 0; i < headers.length && i < cols.length; i++) {
      String header = Optional.ofNullable(headers[i]).orElse("").trim().toLowerCase(Locale.ROOT);
      if (!header.isBlank()) row.put(header, getSafe(cols, i));
    }
    return row;
  }

  private int recordPrevalidationFailures(UUID tenantId, UUID jobId, String[] headers, List<String[]> rows) {
    int failed = 0;
    for (int i = 0; i < rows.size(); i++) {
      int rowNum = i + 2;
      String[] cols = rows.get(i);
      List<ValidationIssue> issues = validateRow(headers, cols);
      if (issues.isEmpty()) continue;
      String rowHash = Hashing.sha256(rowNum + ":" + String.join("|", cols));
      String rowIdempotencyKey = jobId + ":row:" + rowNum;
      UUID rowId = importRepository.addRow(tenantId, jobId, rowNum, rowHash, ImportRowStatus.FAILED_VALIDATION, null, rowIdempotencyKey);
      for (ValidationIssue issue : issues) {
        importRepository.addError(tenantId, rowId, issue.fieldPath(), issue.code(), issue.message(), "");
      }
      failed++;
    }
    return failed;
  }

  private static boolean isCsvInjection(String value) {
    if (value == null || value.isEmpty()) return false;
    for (char prefix : CSV_INJECTION_PREFIXES) {
      if (value.charAt(0) == prefix) return true;
    }
    return false;
  }

  private static String neutralizeCsvInjection(String value) {
    if (value == null || value.isEmpty()) return value;
    return isCsvInjection(value) ? value.substring(1) : value;
  }

  private static void requireIdempotencyKey(String key) {
    if (key == null || key.isBlank()) {
      throw new ScenarioException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED",
          "Idempotency-Key is required for scenario import mutations.", List.of());
    }
  }

  private static String fileHash(MultipartFile file) {
    try (InputStream in = file.getInputStream()) {
      return Hashing.sha256(new String(in.readAllBytes(), StandardCharsets.UTF_8));
    } catch (IOException ex) {
      throw new ScenarioException(HttpStatus.BAD_REQUEST, "CSV_PARSE_ERROR", "Failed to read CSV file.", List.of());
    }
  }

  private void emitImportCompleted(UUID tenantId, UUID jobId, ImportJobStatus status, int created, int failed,
      String correlationId, String fileHash, String templateVersion) {
    String corr = correlationId == null || correlationId.isBlank() ? UUID.randomUUID().toString() : correlationId;
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("importJobId", jobId.toString());
    payload.put("status", status.name());
    payload.put("createdRows", created);
    payload.put("failedRows", failed);
    payload.put("templateVersion", templateVersion);
    payload.put("fileHash", fileHash);
    payload.put("createdScenarioIdsCount", created);
    UUID auditPackageId = UUID.randomUUID();
    payload.put("auditPackageId", auditPackageId.toString());
    scenarioRepository.event(new EventRecord(UUID.randomUUID(), tenantId, jobId,
        "ScenarioImportCompleted.v1", 1, corr, Instant.now(), payload));
    scenarioRepository.audit(new AuditRecord(auditPackageId, tenantId, jobId,
        "SCENARIO_IMPORT_COMPLETED", corr, Instant.now(), fileHash));
  }

  private static void requireRole(String required, Set<String> allowed) {
    String roles = Optional.ofNullable(RequestContext.roles()).orElse("");
    boolean ok = Arrays.stream(roles.split(",")).map(String::trim).anyMatch(allowed::contains);
    if (!ok) throw new ScenarioException(org.springframework.http.HttpStatus.FORBIDDEN, "ROLE_REQUIRED", required + " role is required.", List.of());
  }
}
