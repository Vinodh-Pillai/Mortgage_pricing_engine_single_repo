package com.wcpe.auditreplay.domain;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

class AuditRecordCreatedV1ContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void matchesSchema() throws Exception {
        JsonNode schema = resource("event-contracts/audit-record-created-v1.schema.json");
        JsonNode quoteFixture = resource("event-contracts/fixtures/audit-record-quote-created-v1.json");
        JsonNode snapshotFixture = resource("event-contracts/fixtures/audit-snapshot-redacted-before-after.json");

        assertTrue(hasRequired(schema, "auditRecordId"));
        assertTrue(hasRequired(schema, "integrityHash"));
        assertTrue(hasRequired(schema, "retentionUntil"));
        assertTrue(quoteFixture.hasNonNull("auditRecordId"));
        assertTrue(quoteFixture.path("actor").hasNonNull("id"));
        assertTrue(snapshotFixture.path("before").path("borrower").asText().equals("REDACTED"));
    }

    private boolean hasRequired(JsonNode schema, String requiredField) {
        for (JsonNode item : schema.path("required")) {
            if (requiredField.equals(item.asText())) {
                return true;
            }
        }
        return false;
    }

    private JsonNode resource(String path) throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
            return objectMapper.readTree(input);
        }
    }
}
