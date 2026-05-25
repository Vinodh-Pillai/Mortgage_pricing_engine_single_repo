package com.wcpe.scenario.domain;

import java.time.*;
import java.util.*;

// S09: Batch Scenario Import domain records

enum ImportJobStatus { QUEUED, RUNNING, COMPLETED, FAILED, CANCELLED, REJECTED_TEMPLATE_MISMATCH }

enum ImportRowStatus { PENDING, CREATED, FAILED_VALIDATION, SKIPPED, SYSTEM_FAILED }

enum PartialSuccessPolicy { ALLOW_VALID_ROWS, REJECT_ALL_ON_ANY_ERROR }

record CsvImportRequest(String templateVersion, String channel, String quoteIntent, PartialSuccessPolicy partialSuccessPolicy) {}

record ImportJob(UUID importJobId, UUID tenantId, ImportJobStatus status, String fileName, String fileHash,
    String templateVersion, String channel, String quoteIntent, PartialSuccessPolicy partialSuccessPolicy,
    String submittedBy, Instant submittedAtUtc, Instant startedAtUtc, Instant completedAtUtc,
    int totalRows, int createdRows, int failedRows) {}

record ImportRow(UUID importRowId, UUID importJobId, int rowNumber, String rowHash, ImportRowStatus status,
    UUID scenarioId, UUID scenarioVersionId, String idempotencyKey, Instant createdAtUtc) {}

record ImportError(UUID importErrorId, UUID importRowId, String fieldName, String errorCode,
    String message, String rawValueRedacted) {}

record ImportJobResponse(UUID importJobId, ImportJobStatus status, String templateVersion, int submittedRowCount,
    Map<String, String> links) {}

record ImportJobStatusResponse(UUID importJobId, ImportJobStatus status, String templateVersion, int totalRows,
    int createdRows, int failedRows, Instant startedAtUtc, Instant completedAtUtc) {}

record CsvParsingError(int rowNumber, String fieldName, String errorCode, String message, String rawValueRedacted) {}
