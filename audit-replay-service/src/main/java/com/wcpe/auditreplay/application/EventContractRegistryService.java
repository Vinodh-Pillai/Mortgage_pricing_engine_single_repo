package com.wcpe.auditreplay.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.auditreplay.domain.EventContractRegistry;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
public class EventContractRegistryService implements EventContractRegistry {

    private static final String ENVELOPE_SCHEMA_REF = "event-envelope-v1";
    private static final String AUDIT_RECORD_SCHEMA_REF = "audit-record-created-v1";

    private final ObjectMapper objectMapper;

    public EventContractRegistryService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String eventType, Integer eventVersion) {
        return Integer.valueOf(1).equals(eventVersion)
                && ("AuditRecordCreated.v1".equals(eventType)
                || "audit_record.created".equals(eventType)
                || "outbox_pattern.completed.v1".equals(eventType)
                || "QuoteReplayRequested.v1".equals(eventType)
                || "QuoteReplayCompleted.v1".equals(eventType)
                 || "EvidenceExportRequested.v1".equals(eventType)
                 || "EvidenceExportReady.v1".equals(eventType)
                 || "EvidenceExportDownloaded.v1".equals(eventType)
                 || "EvidenceExportFailed.v1".equals(eventType)
                 || "RetentionPolicyPublished.v1".equals(eventType)
                 || "LegalHoldApplied.v1".equals(eventType)
                 || "LegalHoldReleased.v1".equals(eventType)
                 || "RetentionPurgeCompleted.v1".equals(eventType));
    }

    public ContractMetadata envelopeV1() {
        return new ContractMetadata(
                "EventEnvelopeV1",
                1,
                ENVELOPE_SCHEMA_REF,
                "ACTIVE",
                "2026-06-06T00:00:00Z",
                null,
                "audit-replay-service",
                schema("event-contracts/event-envelope-v1.schema.json"),
                List.of(fixture("event-contracts/fixtures/event-envelope-v1-minimal.json"), fixture("event-contracts/fixtures/event-envelope-v1-audit-record-created.json")));
    }

    public ContractMetadata eventVersion(String eventType, int version) {
        if (!supports(eventType, version)) {
            throw new UnknownEventContractException(eventType, version);
        }
        return new ContractMetadata(
                eventType,
                version,
                AUDIT_RECORD_SCHEMA_REF,
                "ACTIVE",
                "2026-06-06T00:00:00Z",
                null,
                "audit-replay-service",
                schema("event-contracts/audit-record-created-v1.schema.json"),
                List.of(fixture("event-contracts/fixtures/event-envelope-v1-audit-record-created.json")));
    }

    private JsonNode schema(String path) {
        return resourceJson(path);
    }

    private JsonNode fixture(String path) {
        return resourceJson(path);
    }

    private JsonNode resourceJson(String path) {
        try (InputStream input = new ClassPathResource(path).getInputStream()) {
            return objectMapper.readTree(input);
        } catch (IOException ex) {
            throw new IllegalStateException("Missing event contract resource: " + path, ex);
        }
    }

    public record ContractMetadata(
            String eventType,
            int eventVersion,
            String schemaRef,
            String status,
            String effectiveFrom,
            String deprecatedAt,
            String owner,
            JsonNode jsonSchema,
            List<JsonNode> fixtures) {}

    public static class UnknownEventContractException extends RuntimeException {
        public UnknownEventContractException(String eventType, int version) {
            super("Unknown event contract: " + eventType + " v" + version);
        }
    }
}
