package com.wcpe.auditreplay.application;

import com.wcpe.auditreplay.domain.AuditRecord;
import com.wcpe.auditreplay.repository.AuditRecordRepository;
import jakarta.persistence.criteria.Predicate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;

@Service
public class AuditSearchService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;
    private static final Set<String> SUPPORTED_FILTERS = Set.of(
            "from",
            "to",
            "actorId",
            "subjectType",
            "subjectId",
            "action",
            "result",
            "correlationId",
            "legalHold",
            "integrityStatus",
            "cursor",
            "limit");

    private final AuditRecordRepository repository;

    public AuditSearchService(AuditRecordRepository repository) {
        this.repository = repository;
    }

    public AuditSearchPage search(UUID tenantId, MultiValueMap<String, String> filters) {
        validateFilterNames(filters);
        validateIntegrityStatus(first(filters, "integrityStatus"));
        Instant from = requiredInstant(filters, "from");
        Instant to = requiredInstant(filters, "to");
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from must be before or equal to to");
        }
        int limit = boundedLimit(first(filters, "limit"));
        Cursor cursor = Cursor.decode(first(filters, "cursor"));
        List<AuditRecord> records = repository.findAll(
                        specification(tenantId, filters, from, to, cursor),
                        PageRequest.of(0, limit + 1, Sort.by(
                                Sort.Order.desc("occurredAt"),
                                Sort.Order.desc("id"))))
                .getContent();

        boolean hasMore = records.size() > limit;
        List<AuditSearchResult> results = records.stream()
                .limit(limit)
                .map(AuditSearchResult::from)
                .toList();
        String nextCursor = null;
        if (hasMore && !results.isEmpty()) {
            AuditRecord last = records.get(results.size() - 1);
            nextCursor = Cursor.encode(last.getOccurredAt(), last.getId());
        }
        return new AuditSearchPage(results, nextCursor, results.size(), hasMore);
    }

    private Specification<AuditRecord> specification(
            UUID tenantId,
            MultiValueMap<String, String> filters,
            Instant from,
            Instant to,
            Cursor cursor) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
            predicates.add(cb.between(root.get("occurredAt"), from, to));
            addEqual(predicates, filters, "actorId", root.get("actorId"), cb);
            addEqual(predicates, filters, "subjectType", root.get("subjectType"), cb);
            addEqual(predicates, filters, "subjectId", root.get("subjectId"), cb);
            addEqual(predicates, filters, "action", root.get("action"), cb);
            addEqual(predicates, filters, "result", root.get("result"), cb);
            String correlationId = first(filters, "correlationId");
            if (correlationId != null) {
                predicates.add(cb.equal(root.get("correlationId"), uuid(correlationId, "correlationId")));
            }
            String legalHold = first(filters, "legalHold");
            if (legalHold != null) {
                predicates.add(cb.equal(root.get("legalHold"), bool(legalHold, "legalHold")));
            }
            String integrityStatus = first(filters, "integrityStatus");
            if (integrityStatus != null) {
                if ("VERIFIED".equals(integrityStatus)) {
                    predicates.add(cb.and(
                            cb.isNotNull(root.get("integrityHash")),
                            cb.notEqual(root.get("integrityHash"), "")));
                } else if ("UNKNOWN".equals(integrityStatus)) {
                    predicates.add(cb.or(
                            cb.isNull(root.get("integrityHash")),
                            cb.equal(root.get("integrityHash"), "")));
                } else {
                    throw new IllegalArgumentException("integrityStatus supports VERIFIED or UNKNOWN");
                }
            }
            if (cursor != null) {
                predicates.add(cb.or(
                        cb.lessThan(root.get("occurredAt"), cursor.occurredAt()),
                        cb.and(
                                cb.equal(root.get("occurredAt"), cursor.occurredAt()),
                                cb.lessThan(root.<UUID>get("id"), cursor.id()))));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static void addEqual(
            List<Predicate> predicates,
            MultiValueMap<String, String> filters,
            String key,
            jakarta.persistence.criteria.Expression<String> expression,
            jakarta.persistence.criteria.CriteriaBuilder cb) {
        String value = first(filters, key);
        if (value != null) {
            predicates.add(cb.equal(expression, value));
        }
    }

    private static void validateFilterNames(MultiValueMap<String, String> filters) {
        for (String key : filters.keySet()) {
            if (!SUPPORTED_FILTERS.contains(key)) {
                throw new IllegalArgumentException("Unsupported audit search filter: " + key);
            }
        }
    }

    private static void validateIntegrityStatus(String value) {
        if (value != null && !"VERIFIED".equals(value) && !"UNKNOWN".equals(value)) {
            throw new IllegalArgumentException("integrityStatus supports VERIFIED or UNKNOWN");
        }
    }

    private static Instant requiredInstant(MultiValueMap<String, String> filters, String key) {
        String value = first(filters, key);
        if (value == null) {
            throw new IllegalArgumentException("Bounded date range is required: from and to");
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException(key + " must be an ISO-8601 instant", ex);
        }
    }

    private static int boundedLimit(String value) {
        if (value == null) {
            return DEFAULT_LIMIT;
        }
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 1 || parsed > MAX_LIMIT) {
                throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("limit must be numeric", ex);
        }
    }

    private static UUID uuid(String value, String key) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(key + " must be a UUID", ex);
        }
    }

    private static boolean bool(String value, String key) {
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        throw new IllegalArgumentException(key + " must be true or false");
    }

    private static String first(MultiValueMap<String, String> filters, String key) {
        String value = filters.getFirst(key);
        return value == null || value.isBlank() ? null : value;
    }

    private record Cursor(Instant occurredAt, UUID id) {
        static Cursor decode(String cursor) {
            if (cursor == null) {
                return null;
            }
            try {
                String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
                String[] parts = decoded.split("\\|", 2);
                if (parts.length != 2) {
                    throw new IllegalArgumentException("cursor is invalid");
                }
                return new Cursor(Instant.parse(parts[0]), UUID.fromString(parts[1]));
            } catch (RuntimeException ex) {
                throw new IllegalArgumentException("cursor is invalid", ex);
            }
        }

        static String encode(Instant occurredAt, UUID id) {
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString((occurredAt + "|" + id).getBytes(StandardCharsets.UTF_8));
        }
    }

    public record AuditSearchPage(List<AuditSearchResult> results, String nextCursor, int count, boolean hasMore) {
    }

    public record AuditSearchResult(
            UUID auditRecordId,
            String occurredAt,
            String action,
            String subjectType,
            String subjectId,
            String actorIdHash,
            String result,
            UUID correlationId,
            String integrityStatus,
            boolean legalHold,
            java.time.LocalDate retentionUntil) {
        static AuditSearchResult from(AuditRecord record) {
            return new AuditSearchResult(
                    record.getId(),
                    record.getOccurredAt().toString(),
                    record.getAction(),
                    record.getSubjectType(),
                    record.getSubjectId(),
                    sha256Hex(record.getActorId()),
                    record.getResult(),
                    record.getCorrelationId(),
                    record.getIntegrityHash() == null || record.getIntegrityHash().isBlank() ? "UNKNOWN" : "VERIFIED",
                    record.isLegalHold(),
                    record.getRetentionUntil());
        }
    }

    private static String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest is unavailable", ex);
        }
    }
}
