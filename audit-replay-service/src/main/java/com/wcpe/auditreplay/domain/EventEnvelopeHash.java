package com.wcpe.auditreplay.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class EventEnvelopeHash {

    private EventEnvelopeHash() {}

    public static String payloadHash(ObjectMapper objectMapper, Map<String, Object> payload) {
        return sha256Hex(canonicalJsonBytes(objectMapper, payload));
    }

    public static String integrityHash(ObjectMapper objectMapper, EventEnvelopeV1 envelopeWithoutIntegrityHash) {
        Map<String, Object> canonicalEnvelope = new LinkedHashMap<>();
        canonicalEnvelope.put("eventId", envelopeWithoutIntegrityHash.eventId());
        canonicalEnvelope.put("tenantId", envelopeWithoutIntegrityHash.tenantId());
        canonicalEnvelope.put("eventType", envelopeWithoutIntegrityHash.eventType());
        canonicalEnvelope.put("eventVersion", envelopeWithoutIntegrityHash.eventVersion());
        canonicalEnvelope.put("occurredAt", envelopeWithoutIntegrityHash.occurredAt());
        canonicalEnvelope.put("producer", envelopeWithoutIntegrityHash.producer());
        canonicalEnvelope.put("aggregate", envelopeWithoutIntegrityHash.aggregate());
        canonicalEnvelope.put("actor", envelopeWithoutIntegrityHash.actor());
        canonicalEnvelope.put("correlationId", envelopeWithoutIntegrityHash.correlationId());
        canonicalEnvelope.put("causationId", envelopeWithoutIntegrityHash.causationId());
        canonicalEnvelope.put("idempotencyKey", envelopeWithoutIntegrityHash.idempotencyKey());
        canonicalEnvelope.put("schemaRef", envelopeWithoutIntegrityHash.schemaRef());
        canonicalEnvelope.put("payload", envelopeWithoutIntegrityHash.payload());
        canonicalEnvelope.put("payloadHash", envelopeWithoutIntegrityHash.payloadHash());
        canonicalEnvelope.put("previousHash", envelopeWithoutIntegrityHash.previousHash());
        canonicalEnvelope.put("legalHoldTags", envelopeWithoutIntegrityHash.legalHoldTags());
        return sha256Hex(canonicalJsonBytes(objectMapper, canonicalEnvelope));
    }

    public static byte[] canonicalJsonBytes(ObjectMapper objectMapper, Object value) {
        try {
            return objectMapper.writeValueAsBytes(canonicalize(value));
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Event envelope value is not JSON serializable", ex);
        }
    }

    @SuppressWarnings("unchecked")
    private static Object canonicalize(Object value) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean || value instanceof Instant) {
            return value;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new TreeMap<>();
            map.forEach((key, child) -> sorted.put(String.valueOf(key), canonicalize(child)));
            return sorted;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> values = new ArrayList<>();
            iterable.forEach(child -> values.add(canonicalize(child)));
            return values;
        }
        if (value.getClass().isArray()) {
            List<Object> values = new ArrayList<>();
            Object[] array = (Object[]) value;
            for (Object child : array) {
                values.add(canonicalize(child));
            }
            return values;
        }
        if (value instanceof EventEnvelopeV1.Producer producer) {
            return Map.of("service", producer.service(), "version", producer.version());
        }
        if (value instanceof EventEnvelopeV1.Aggregate aggregate) {
            return Map.of("type", aggregate.type(), "id", aggregate.id(), "version", aggregate.version());
        }
        if (value instanceof EventEnvelopeV1.Actor actor) {
            return Map.of("type", actor.type(), "id", actor.id());
        }
        return value;
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest is unavailable", ex);
        }
    }
}
