package com.wcpe.auditreplay.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class AuditRedactionPolicy {

    static final String MASK = "***REDACTED***";

    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "borrower",
            "borrowername",
            "ssn",
            "socialsecuritynumber",
            "taxpayerid",
            "dob",
            "dateofbirth",
            "email",
            "phone",
            "income",
            "annualincome",
            "monthlyincome",
            "assets",
            "liabilities",
            "credit",
            "creditscore",
            "fico",
            "financial",
            "loanamount",
            "propertyvalue",
            "accountnumber");

    private final ObjectMapper objectMapper;

    public AuditRedactionPolicy(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public byte[] redact(byte[] snapshotJson) {
        try {
            JsonNode root = objectMapper.readTree(snapshotJson);
            return objectMapper.writeValueAsBytes(redactNode(root));
        } catch (IOException ex) {
            throw new IllegalArgumentException("snapshotJson cannot be parsed for redaction", ex);
        }
    }

    private JsonNode redactNode(JsonNode node) throws JsonProcessingException {
        if (node == null || node.isNull() || node.isValueNode()) {
            return node;
        }
        if (node.isArray()) {
            ArrayNode redacted = objectMapper.createArrayNode();
            for (JsonNode child : node) {
                redacted.add(redactNode(child));
            }
            return redacted;
        }
        ObjectNode redacted = objectMapper.createObjectNode();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            if (isSensitiveField(field.getKey())) {
                redacted.set(field.getKey(), TextNode.valueOf(MASK));
            } else {
                redacted.set(field.getKey(), redactNode(field.getValue()));
            }
        }
        return redacted;
    }

    static boolean isSensitiveField(String fieldName) {
        String normalized = fieldName == null ? "" : fieldName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return SENSITIVE_KEYS.stream().anyMatch(normalized::contains);
    }
}
