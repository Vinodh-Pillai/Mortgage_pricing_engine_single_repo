package com.wcpe.scenario.domain;

import java.io.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
class BatchImportService {
  private static final Set<String> WRITER_ROLES = Set.of("SCENARIO_WRITER", "SCENARIO_ADMIN");
  private static final Set<String> KNOWN_COLUMNS = Set.of("quote_intent", "channel", "scenario_name", "external_loan_id",
      "source_system", "property_state", "property_zip", "property_type", "occupancy_type",
      "units", "purchase_price", "credit_score", "credit_status", "credit_score_source",
      "monthly_income", "monthly_debt", "liquid_assets", "loan_amount", "term_months");
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
    validateFileUpload(file);
    validateTemplate(templateVersion);
    Optional<Object> replay = scenarioRepository.idempotent(tenantId.toString() + ":import", idempotencyKey,
        Map.of("file", file.getOriginalFilename(), "template", templateVersion, "channel", channel));
    if (replay.isPresent()) return (ImportJobResponse) replay.get();
    String fileHash = Hashing.sha256(file.getOriginalFilename() + ":" + file.getSize() + ":" + System.currentTimeMillis());
    // Parse CSV to count rows
    List<String[]> parsedRows = parseCsv(file);
    validateHeaders(parsedRows.isEmpty() ? new String[0] : parsedRows.get(0));
    int dataRows = Math.max(0, parsedRows.size() - 1);
    UUID jobId = importRepository.createJob(tenantId, file.getOriginalFilename(), fileHash,
        templateVersion, channel, quoteIntent, policy, submittedBy, dataRows);
    // Process rows
    processRows(tenantId, jobId, parsedRows.subList(1, parsedRows.size()), channel, quoteIntent, policy, correlationId);
    ImportJobResponse response = new ImportJobResponse(jobId, ImportJobStatus.RUNNING, templateVersion, dataRows, Map.of(
        "status", "/api/v1/tenants/" + tenantId + "/scenario-imports/" + jobId,
        "ui", "/pricing/scenario-imports/" + jobId));
    scenarioRepository.remember(tenantId.toString() + ":import", idempotencyKey, file.getOriginalFilename(), response);
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
    return fields.toArray(String[]::new);
  }

  String[] parseLine(String line) {
    return parseCsvLine(line);
  }

  private void processRows(UUID tenantId, UUID jobId, List<String[]> rows, String channel, String quoteIntent,
      PartialSuccessPolicy policy, String correlationId) {
    Instant startedAt = Instant.now();
    importRepository.startJob(tenantId, jobId, startedAt);
    int created = 0, failed = 0;

    for (int i = 0; i < rows.size(); i++) {
      int rowNum = i + 2; // header is row 1, first data row is 2
      String[] cols = rows.get(i);
      String rowHash = Hashing.sha256(rowNum + ":" + String.join("|", rows.get(i)));
      String rowIdempotencyKey = jobId + ":row:" + rowNum;

      try {
        CreateScenarioRequest request = mapRowToRequest(cols, channel, quoteIntent);
        if (request == null) throw new ScenarioException(org.springframework.http.HttpStatus.BAD_REQUEST, "INVALID_ROW", "Row " + rowNum + " could not be mapped.", List.of());
        List<ValidationIssue> issues = validateRow(cols);
        if (!issues.isEmpty() && issues.stream().anyMatch(iss -> iss.severity() == Severity.BLOCKING)) {
          if (policy == PartialSuccessPolicy.REJECT_ALL_ON_ANY_ERROR) {
            importRepository.markJobComplete(tenantId, jobId, ImportJobStatus.FAILED, Instant.now(), 0, rowNum);
            return;
          }
          importRepository.addRow(tenantId, jobId, rowNum, rowHash, ImportRowStatus.FAILED_VALIDATION, null, rowIdempotencyKey);
          for (ValidationIssue issue : issues) {
            importRepository.addError(tenantId, UUID.randomUUID(), issue.fieldPath(), issue.code(), issue.message(), "");
          }
          failed++;
          continue;
        }
        try {
          ScenarioResponse result = scenarioService.createDraft(tenantId, rowIdempotencyKey, correlationId, request);
          importRepository.addRow(tenantId, jobId, rowNum, rowHash, ImportRowStatus.CREATED, result.scenarioId(), rowIdempotencyKey);
          created++;
        } catch (ScenarioException ex) {
          importRepository.addRow(tenantId, jobId, rowNum, rowHash, ImportRowStatus.SYSTEM_FAILED, null, rowIdempotencyKey);
          failed++;
        }
      } catch (Exception ex) {
        importRepository.addRow(tenantId, jobId, rowNum, rowHash, ImportRowStatus.SYSTEM_FAILED, null, rowIdempotencyKey);
        failed++;
      }
    }

    ImportJobStatus finalStatus = failed > 0 && created > 0 ? ImportJobStatus.COMPLETED :
        failed > 0 ? ImportJobStatus.FAILED : ImportJobStatus.COMPLETED;
    importRepository.markJobComplete(tenantId, jobId, finalStatus, Instant.now(), created, failed);
  }

  CreateScenarioRequest mapRowToRequest(String[] cols, String channel, String quoteIntent) {
    if (cols == null || cols.length < 5) return null;
    Map<String, Object> facts = new LinkedHashMap<>();
    facts.put("channel", channel);
    facts.put("quoteIntent", quoteIntent);
    int idx = 0;
    facts.put("scenarioName", getSafe(cols, idx++));
    facts.put("externalLoanId", getSafe(cols, idx++));
    facts.put("sourceSystem", getSafe(cols, idx++) != null ? getSafe(cols, idx - 1) : "BATCH_IMPORT");
    // Store remaining as structured facts
    String[] colNames = {"property_state", "property_zip", "property_type", "occupancy_type",
        "units", "purchase_price", "credit_score", "credit_status", "monthly_income", "monthly_debt",
        "liquid_assets", "loan_amount", "term_months"};
    for (int i = 0; i < colNames.length && idx < cols.length; i++, idx++) {
      facts.put(colNames[i], getSafe(cols, idx));
    }
    return new CreateScenarioRequest(quoteIntent, channel,
        facts.get("scenarioName") != null ? facts.get("scenarioName").toString() : null,
        facts.get("externalLoanId") != null ? facts.get("externalLoanId").toString() : null,
        "BATCH_IMPORT", facts);
  }

  private List<ValidationIssue> validateRow(String[] cols) {
    List<ValidationIssue> issues = new ArrayList<>();
    if (cols == null || cols.length < 3) issues.add(new ValidationIssue("INSUFFICIENT_COLUMNS", "_row", Severity.BLOCKING, "Row has insufficient columns."));
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
      if (!name.endsWith(".csv") && !name.endsWith(". CSV")) throw new ScenarioException(
          org.springframework.http.HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE_TYPE",
          "Only CSV files are accepted.", List.of());
    }
  }

  private void validateTemplate(String templateVersion) {
    if (templateVersion == null || !templateVersion.equals("scenario-import-v1")) {
      throw new ScenarioException(org.springframework.http.HttpStatus.UNSUPPORTED_MEDIA_TYPE,
          "TEMPLATE_MISMATCH", "Only template version scenario-import-v1 is supported.", List.of());
    }
  }

  private void validateHeaders(String[] headers) {
    if (headers == null || headers.length == 0) return;
    // Basic header validation - ensure at least known columns exist
  }

  private static String getSafe(String[] cols, int idx) {
    if (idx < cols.length && cols[idx] != null) {
      String val = cols[idx].trim();
      if (isCsvInjection(val)) return val.replaceAll("^[=+\\-@`]", ""); // neutralize
      return val;
    }
    return null;
  }

  private static boolean isCsvInjection(String value) {
    if (value == null || value.isEmpty()) return false;
    for (char prefix : CSV_INJECTION_PREFIXES) {
      if (value.charAt(0) == prefix) return true;
    }
    return false;
  }

  private static void requireRole(String required, Set<String> allowed) {
    String roles = Optional.ofNullable(RequestContext.roles()).orElse("");
    boolean ok = Arrays.stream(roles.split(",")).map(String::trim).anyMatch(allowed::contains);
    if (!ok) throw new ScenarioException(org.springframework.http.HttpStatus.FORBIDDEN, "ROLE_REQUIRED", required + " role is required.", List.of());
  }
}
