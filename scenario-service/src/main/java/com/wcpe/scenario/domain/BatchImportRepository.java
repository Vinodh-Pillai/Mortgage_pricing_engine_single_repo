package com.wcpe.scenario.domain;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class BatchImportRepository {
  private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  BatchImportRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper.findAndRegisterModules();
  }

  @Transactional
  UUID createJob(UUID tenantId, String fileName, String fileHash, String templateVersion, String channel,
      String quoteIntent, PartialSuccessPolicy policy, String submittedBy, int totalRows) {
    UUID jobId = UUID.randomUUID();
    Instant now = Instant.now();
    jdbc.update("""
        insert into scenario.scenario_import_job (tenant_id, import_job_id, status, file_name, file_hash,
          template_version, channel, quote_intent, partial_success_policy, submitted_by, submitted_at_utc,
          total_rows, created_rows, failed_rows)
        values (?, ?, 'QUEUED', ?, ?, ?, ?, ?, ?, ?, ?, 0, 0, 0)
        """, tenantId, jobId, fileName, fileHash, templateVersion, channel, quoteIntent,
        policy.name(), submittedBy, Timestamp.from(now), totalRows);
    return jobId;
  }

  ImportJob getJob(UUID tenantId, UUID jobId) {
    try {
      return jdbc.queryForObject("""
          select import_job_id, status, file_name, file_hash, template_version, channel, quote_intent,
            partial_success_policy, submitted_by, submitted_at_utc, started_at_utc, completed_at_utc,
            total_rows, created_rows, failed_rows
          from scenario.scenario_import_job where tenant_id = ? and import_job_id = ?
          """, (rs, row) -> new ImportJob(
        (UUID) rs.getObject("import_job_id"), tenantId,
        ImportJobStatus.valueOf(rs.getString("status")),
        rs.getString("file_name"), rs.getString("file_hash"),
        rs.getString("template_version"), rs.getString("channel"), rs.getString("quote_intent"),
        rs.getString("partial_success_policy") != null ? PartialSuccessPolicy.valueOf(rs.getString("partial_success_policy")) : PartialSuccessPolicy.ALLOW_VALID_ROWS,
        rs.getString("submitted_by"),
        rs.getTimestamp("submitted_at_utc").toInstant(),
        rs.getTimestamp("started_at_utc") != null ? rs.getTimestamp("started_at_utc").toInstant() : null,
        rs.getTimestamp("completed_at_utc") != null ? rs.getTimestamp("completed_at_utc").toInstant() : null,
        rs.getInt("total_rows"), rs.getInt("created_rows"), rs.getInt("failed_rows")),
        tenantId, jobId);
    } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
      throw new ScenarioException(org.springframework.http.HttpStatus.NOT_FOUND, "IMPORT_JOB_NOT_FOUND", "Import job was not found.", List.of());
    }
  }

  @Transactional
  void startJob(UUID tenantId, UUID jobId, Instant startedAt) {
    int updated = jdbc.update("""
        update scenario.scenario_import_job set status = 'RUNNING', started_at_utc = ?
        where tenant_id = ? and import_job_id = ? and status = 'QUEUED'
        """, Timestamp.from(startedAt), tenantId, jobId);
    if (updated == 0) throw new ScenarioException(org.springframework.http.HttpStatus.CONFLICT, "IMPORT_JOB_NOT_QUEUED", "Import job is not in QUEUED state.", List.of());
  }

  @Transactional
  void markJobComplete(UUID tenantId, UUID jobId, ImportJobStatus status, Instant completedAt, int createdRows, int failedRows) {
    jdbc.update("""
        update scenario.scenario_import_job set status = ?, completed_at_utc = ?,
          created_rows = ?, failed_rows = ?
        where tenant_id = ? and import_job_id = ?
        """, status.name(), Timestamp.from(completedAt), createdRows, failedRows, tenantId, jobId);
  }

  @Transactional
  UUID addRow(UUID tenantId, UUID jobId, int rowNumber, String rowHash, ImportRowStatus status,
      UUID scenarioId, String idempotencyKey) {
    UUID rowId = UUID.randomUUID();
    int inserted = jdbc.update("""
        insert into scenario.scenario_import_row (tenant_id, import_row_id, import_job_id, row_number,
          row_hash, status, scenario_id, idempotency_key, created_at_utc)
        values (?, ?, ?, ?, ?, ?, ?, ?, now())
        on conflict (tenant_id, import_job_id, row_number) do nothing
        """, tenantId, rowId, jobId, rowNumber, rowHash, status.name(),
        scenarioId != null ? scenarioId : null, idempotencyKey);
    if (inserted > 0) return rowId;
    return jdbc.queryForObject("""
        select import_row_id from scenario.scenario_import_row
        where tenant_id = ? and import_job_id = ? and row_number = ?
        """, UUID.class, tenantId, jobId, rowNumber);
  }

  @Transactional
  void addError(UUID tenantId, UUID rowId, String fieldName, String errorCode, String message, String rawValueRedacted) {
    UUID errorId = UUID.randomUUID();
    jdbc.update("""
        insert into scenario.scenario_import_error (tenant_id, import_error_id, import_row_id,
          field_name, error_code, message, raw_value_redacted)
        values (?, ?, ?, ?, ?, ?, ?)
        """, tenantId, errorId, rowId, fieldName, errorCode, message, rawValueRedacted);
  }

  @Transactional
  void updateRowStatus(UUID tenantId, UUID jobId, int rowNumber, ImportRowStatus status, UUID scenarioId) {
    jdbc.update("""
        update scenario.scenario_import_row set status = ?, scenario_id = ?
        where tenant_id = ? and import_job_id = ? and row_number = ?
        """, status.name(), scenarioId, tenantId, jobId, rowNumber);
  }

  List<ImportRow> getRows(UUID tenantId, UUID jobId) {
    return jdbc.query("""
        select import_row_id, row_number, row_hash, status, scenario_id, idempotency_key, created_at_utc
        from scenario.scenario_import_row where tenant_id = ? and import_job_id = ?
        order by row_number
        """, (rs, row) -> new ImportRow(
        (UUID) rs.getObject("import_row_id"), jobId, rs.getInt("row_number"),
        rs.getString("row_hash"), ImportRowStatus.valueOf(rs.getString("status")),
        rs.getObject("scenario_id") != null ? (UUID) rs.getObject("scenario_id") : null,
        null, rs.getString("idempotency_key"), rs.getTimestamp("created_at_utc").toInstant()),
        tenantId, jobId);
  }

  List<ImportError> getErrors(UUID tenantId, UUID jobId) {
    return jdbc.query("""
        select e.import_error_id, r.import_row_id, e.field_name, e.error_code, e.message, e.raw_value_redacted
        from scenario.scenario_import_error e
        join scenario.scenario_import_row r on r.import_row_id = e.import_row_id
        where r.tenant_id = ? and r.import_job_id = ?
        order by r.row_number
        """, (rs, row) -> new ImportError(
        (UUID) rs.getObject("import_error_id"), (UUID) rs.getObject("import_row_id"),
        rs.getString("field_name"), rs.getString("error_code"), rs.getString("message"),
        rs.getString("raw_value_redacted")), tenantId, jobId);
  }
}
