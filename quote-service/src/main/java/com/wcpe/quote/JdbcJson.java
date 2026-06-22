package com.wcpe.quote;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.SQLException;
import org.postgresql.util.PGobject;

final class JdbcJson {
    private JdbcJson() {
    }

    static PGobject jsonb(ObjectMapper objectMapper, Object value) {
        try {
            PGobject json = new PGobject();
            json.setType("jsonb");
            json.setValue(objectMapper.writeValueAsString(value));
            return json;
        } catch (JsonProcessingException | SQLException ex) {
            throw new IllegalStateException("QUOTE_PERSISTENCE_JSON_SERIALIZATION_FAILED", ex);
        }
    }

    static <T> T read(ObjectMapper objectMapper, String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("QUOTE_PERSISTENCE_JSON_DESERIALIZATION_FAILED", ex);
        }
    }

    static <T> T read(ObjectMapper objectMapper, String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("QUOTE_PERSISTENCE_JSON_DESERIALIZATION_FAILED", ex);
        }
    }
}
