package com.wcpe.eligibility;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.eligibility.domain.models.PropertyTypeRuleSetPublished;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PropertyTypeRuleSetPublishedEventContractTest {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void schemaV1() throws Exception {
        PropertyTypeRuleSetPublished event = new PropertyTypeRuleSetPublished(
            UUID.fromString("99999999-9999-9999-9999-999999999999"),
            "PropertyTypeRuleSetPublished.v1",
            "1",
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
            3,
            "CONF30",
            "FNMA",
            "RETAIL",
            "catalog-admin",
            "corr-pii03-s05",
            Instant.parse("2026-05-13T10:15:30Z")
        );

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(event));

        assertEquals("PropertyTypeRuleSetPublished.v1", json.get("eventType").asText());
        assertEquals("1", json.get("eventVersion").asText());
        assertEquals("11111111-1111-1111-1111-111111111111", json.get("tenantId").asText());
        assertEquals("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", json.get("ruleSetId").asText());
        assertEquals(3, json.get("version").asInt());
        assertEquals("CONF30", json.get("productCode").asText());
        assertEquals("FNMA", json.get("investorCode").asText());
        assertEquals("RETAIL", json.get("channel").asText());
        assertEquals("catalog-admin", json.get("actorId").asText());
        assertEquals("corr-pii03-s05", json.get("correlationId").asText());
        assertTrue(json.hasNonNull("occurredAt"));
    }
}
