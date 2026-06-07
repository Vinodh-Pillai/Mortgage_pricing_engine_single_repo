package com.wcpe.auditreplay.application;

import com.wcpe.auditreplay.domain.AuditRecord;
import com.wcpe.auditreplay.domain.EvidenceExportJob;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class EvidenceExportManifest {

    private EvidenceExportManifest() {}

    public static Map<String, Object> build(EvidenceExportCommand command, List<AuditRecord> records) {
        Objects.requireNonNull(command, "command is required");
        List<AuditRecord> ordered = records.stream()
                .sorted(Comparator.comparing(AuditRecord::getOccurredAt).thenComparing(AuditRecord::getId))
                .toList();
        List<Map<String, Object>> files = new ArrayList<>();
        for (AuditRecord record : ordered) {
            files.add(fileEntry(record));
        }
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("schemaVersion", "evidence-export-manifest-v1");
        manifest.put("tenantId", command.tenantId().toString());
        manifest.put("format", command.format());
        manifest.put("purpose", command.purpose());
        manifest.put("redactionProfile", command.redactionProfile());
        manifest.put("includeSnapshots", command.includeSnapshots());
        manifest.put("includeDiffs", command.includeDiffs());
        manifest.put("includeHashes", command.includeHashes());
        manifest.put("producer", "audit-replay-service:0.1.0");
        manifest.put("sourceAuditRecordCount", ordered.size());
        manifest.put("sourceAuditRecordIds", ordered.stream().map(record -> record.getId().toString()).toList());
        manifest.put("sourceReplayRunIds", command.replayRunIds() == null ? List.of() : command.replayRunIds().stream().map(Object::toString).toList());
        manifest.put("legalHold", ordered.stream().anyMatch(AuditRecord::isLegalHold));
        manifest.put("files", files);
        return manifest;
    }

    public static Map<String, Object> resultSummary(EvidenceExportJob job) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("exportId", job.getExportId().toString());
        summary.put("status", job.getStatus().name());
        summary.put("artifactUri", job.getArtifactUri());
        summary.put("manifestHash", job.getManifestHash());
        summary.put("expiresAt", job.getExpiresAt() == null ? null : job.getExpiresAt().toString());
        summary.put("retentionUntil", job.getRetentionUntil().toString());
        summary.put("legalHold", job.isLegalHold());
        return summary;
    }

    private static Map<String, Object> fileEntry(AuditRecord record) {
        Map<String, Object> file = new LinkedHashMap<>();
        file.put("fileName", "audit-records/" + record.getId() + ".json");
        file.put("contentType", "application/json");
        file.put("sourceAuditId", record.getId().toString());
        file.put("subjectType", record.getSubjectType());
        file.put("subjectId", record.getSubjectId());
        file.put("occurredAt", record.getOccurredAt().toString());
        file.put("byteSize", record.getSnapshotJson() == null ? 0 : record.getSnapshotJson().length);
        file.put("sha256", record.getIntegrityHash());
        file.put("redactionProfile", record.getRedactionProfile());
        file.put("legalHold", record.isLegalHold());
        file.put("retentionUntil", record.getRetentionUntil().toString());
        return file;
    }
}
