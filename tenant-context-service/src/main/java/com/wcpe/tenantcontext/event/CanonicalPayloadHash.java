package com.wcpe.tenantcontext.event;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class CanonicalPayloadHash {
    private CanonicalPayloadHash() {
    }

    public static String sha256(String payloadJson) {
        String canonical = canonicalize(payloadJson);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return "sha256:" + HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new EventEnvelopeValidationException("PAYLOAD_HASH_UNAVAILABLE", "SHA-256 payload hashing is unavailable");
        }
    }

    public static String canonicalize(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            throw new EventEnvelopeValidationException("EVENT_ENVELOPE_VALIDATION_FAILED", "payloadJson is required");
        }
        Parser parser = new Parser(payloadJson);
        Object value = parser.parse();
        return write(value);
    }

    public static String topLevelTenantId(String payloadJson) {
        Object value = new Parser(payloadJson).parse();
        if (value instanceof Map<?, ?> map && map.get("tenantId") instanceof String tenantId) {
            return tenantId;
        }
        return "";
    }

    private static String write(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String string) {
            return quote(string);
        }
        if (value instanceof NumberToken number) {
            return number.value();
        }
        if (value instanceof Boolean bool) {
            return bool.toString();
        }
        if (value instanceof List<?> list) {
            StringBuilder builder = new StringBuilder("[");
            for (int index = 0; index < list.size(); index++) {
                if (index > 0) {
                    builder.append(',');
                }
                builder.append(write(list.get(index)));
            }
            return builder.append(']').toString();
        }
        if (value instanceof Map<?, ?> map) {
            StringBuilder builder = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    builder.append(',');
                }
                first = false;
                builder.append(quote((String) entry.getKey())).append(':').append(write(entry.getValue()));
            }
            return builder.append('}').toString();
        }
        throw new EventEnvelopeValidationException("EVENT_ENVELOPE_VALIDATION_FAILED", "unsupported JSON payload value");
    }

    private static String quote(String value) {
        StringBuilder builder = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            char c = value.charAt(index);
            switch (c) {
                case '\\' -> builder.append("\\\\");
                case '"' -> builder.append("\\\"");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (c < 0x20) {
                        builder.append(String.format("\\u%04x", (int) c));
                    } else {
                        builder.append(c);
                    }
                }
            }
        }
        return builder.append('"').toString();
    }

    private record NumberToken(String value) {
    }

    private static final class Parser {
        private final String source;
        private int offset;

        private Parser(String source) {
            this.source = source;
        }

        private Object parse() {
            Object value = parseValue();
            skipWhitespace();
            if (offset != source.length()) {
                fail("unexpected trailing JSON content");
            }
            return value;
        }

        private Object parseValue() {
            skipWhitespace();
            if (offset >= source.length()) {
                fail("JSON value is required");
            }
            char c = source.charAt(offset);
            return switch (c) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> parseNumber();
            };
        }

        private Map<String, Object> parseObject() {
            offset++;
            TreeMap<String, Object> values = new TreeMap<>();
            skipWhitespace();
            if (take('}')) {
                return values;
            }
            while (true) {
                skipWhitespace();
                if (peek() != '"') {
                    fail("JSON object key must be a string");
                }
                String key = parseString();
                skipWhitespace();
                require(':');
                values.put(key, parseValue());
                skipWhitespace();
                if (take('}')) {
                    return values;
                }
                require(',');
            }
        }

        private List<Object> parseArray() {
            offset++;
            ArrayList<Object> values = new ArrayList<>();
            skipWhitespace();
            if (take(']')) {
                return values;
            }
            while (true) {
                values.add(parseValue());
                skipWhitespace();
                if (take(']')) {
                    return values;
                }
                require(',');
            }
        }

        private String parseString() {
            require('"');
            StringBuilder builder = new StringBuilder();
            while (offset < source.length()) {
                char c = source.charAt(offset++);
                if (c == '"') {
                    return builder.toString();
                }
                if (c == '\\') {
                    builder.append(parseEscape());
                } else {
                    builder.append(c);
                }
            }
            fail("unterminated JSON string");
            return "";
        }

        private char parseEscape() {
            if (offset >= source.length()) {
                fail("unterminated JSON escape");
            }
            char escaped = source.charAt(offset++);
            return switch (escaped) {
                case '"' -> '"';
                case '\\' -> '\\';
                case '/' -> '/';
                case 'b' -> '\b';
                case 'f' -> '\f';
                case 'n' -> '\n';
                case 'r' -> '\r';
                case 't' -> '\t';
                case 'u' -> parseUnicode();
                default -> throw new EventEnvelopeValidationException("EVENT_ENVELOPE_VALIDATION_FAILED", "unsupported JSON escape");
            };
        }

        private char parseUnicode() {
            if (offset + 4 > source.length()) {
                fail("incomplete unicode escape");
            }
            int value = Integer.parseInt(source.substring(offset, offset + 4), 16);
            offset += 4;
            return (char) value;
        }

        private Object literal(String expected, Object value) {
            if (!source.startsWith(expected, offset)) {
                fail("invalid JSON literal");
            }
            offset += expected.length();
            return value;
        }

        private NumberToken parseNumber() {
            int start = offset;
            if (peek() == '-') {
                offset++;
            }
            while (offset < source.length() && Character.isDigit(source.charAt(offset))) {
                offset++;
            }
            if (offset < source.length() && source.charAt(offset) == '.') {
                offset++;
                while (offset < source.length() && Character.isDigit(source.charAt(offset))) {
                    offset++;
                }
            }
            if (offset < source.length() && (source.charAt(offset) == 'e' || source.charAt(offset) == 'E')) {
                offset++;
                if (offset < source.length() && (source.charAt(offset) == '+' || source.charAt(offset) == '-')) {
                    offset++;
                }
                while (offset < source.length() && Character.isDigit(source.charAt(offset))) {
                    offset++;
                }
            }
            if (start == offset) {
                fail("invalid JSON value");
            }
            String raw = source.substring(start, offset);
            try {
                BigDecimal decimal = new BigDecimal(raw).stripTrailingZeros();
                return new NumberToken(decimal.scale() < 0 ? decimal.setScale(0).toPlainString() : decimal.toPlainString());
            } catch (NumberFormatException error) {
                fail("invalid JSON number");
                return new NumberToken(raw);
            }
        }

        private char peek() {
            return offset < source.length() ? source.charAt(offset) : '\0';
        }

        private boolean take(char expected) {
            if (peek() == expected) {
                offset++;
                return true;
            }
            return false;
        }

        private void require(char expected) {
            if (!take(expected)) {
                fail("expected '" + expected + "'");
            }
        }

        private void skipWhitespace() {
            while (offset < source.length() && Character.isWhitespace(source.charAt(offset))) {
                offset++;
            }
        }

        private void fail(String message) {
            throw new EventEnvelopeValidationException("EVENT_ENVELOPE_VALIDATION_FAILED", message);
        }
    }
}
