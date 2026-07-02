package com.wcpe.eligibility.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.eligibility.domain.hashing.Hashing;
import com.wcpe.eligibility.domain.models.EligibilityRequest;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class EligibilityCacheService {
    private static final int DECISION_TTL_SECONDS = 900;

    private final ObjectMapper mapper;
    private final StringRedisTemplate redis;
    private final JdbcTemplate jdbc;
    private final MeterRegistry meterRegistry;
    private final DecisionCachePolicy decisionCachePolicy = new DecisionCachePolicy();

    public EligibilityCacheService(ObjectMapper mapper, ObjectProvider<StringRedisTemplate> redisProvider,
                                   JdbcTemplate jdbc, MeterRegistry meterRegistry) {
        this.mapper = mapper.findAndRegisterModules();
        this.redis = redisProvider.getIfAvailable();
        this.jdbc = jdbc;
        this.meterRegistry = meterRegistry;
    }

    public Optional<CachedEligibilityDecision> getDecision(UUID tenantId, EligibilityRequest request,
                                                          String currentRuleVersionGraphHash) {
        if (redis == null) {
            increment("fallback", "decision");
            return Optional.empty();
        }
        EligibilityCacheKey key = decisionKey(tenantId, request, currentRuleVersionGraphHash);
        try {
            String json = redis.opsForValue().get(key.value());
            if (json == null) {
                increment("miss", "decision");
                return Optional.empty();
            }
            CachedEligibilityDecision cached = mapper.readValue(json, CachedEligibilityDecision.class);
            EligibilityCacheStatus status = decisionCachePolicy.statusFor(cached.ruleVersionGraphHash(), currentRuleVersionGraphHash);
            if (status == EligibilityCacheStatus.HIT) {
                increment("hit", "decision");
                return Optional.of(cached);
            }
            redis.delete(key.value());
            increment("stale_rejected", "decision");
            return Optional.empty();
        } catch (Exception ex) {
            increment("redis_error", "decision");
            return Optional.empty();
        }
    }

    public void putDecision(UUID tenantId, EligibilityRequest request, String ruleVersionGraphHash,
                            com.wcpe.eligibility.domain.models.EligibilityResult result) {
        if (redis == null) {
            return;
        }
        EligibilityCacheKey key = decisionKey(tenantId, request, ruleVersionGraphHash);
        Instant now = Instant.now();
        CachedEligibilityDecision cached = new CachedEligibilityDecision(
            1,
            tenantId.toString(),
            ruleVersionGraphHash,
            result.resultHash(),
            now,
            now.plusSeconds(DECISION_TTL_SECONDS),
            result
        );
        try {
            redis.opsForValue().set(key.value(), mapper.writeValueAsString(cached), Duration.ofSeconds(DECISION_TTL_SECONDS));
        } catch (Exception ex) {
            increment("redis_error", "decision");
        }
    }

    public EligibilityCacheHealth health(UUID tenantId, String productFamily, String quoteType) {
        boolean redisReachable = false;
        if (redis != null) {
            try {
                redisReachable = Boolean.TRUE.equals(redis.getConnectionFactory().getConnection().ping() != null);
            } catch (Exception ex) {
                increment("redis_error", "health");
            }
        }
        Instant lastInvalidation = lastInvalidationAt(tenantId);
        String status = redisReachable ? "UP" : "DEGRADED";
        return new EligibilityCacheHealth(
            tenantId,
            status,
            redisReachable,
            true,
            lastInvalidation,
            List.of(
                new EligibilityCacheHealth.TrackedNamespace("loan-limit", 86_400, null, null),
                new EligibilityCacheHealth.TrackedNamespace("fico-ltv", 21_600, null, null),
                new EligibilityCacheHealth.TrackedNamespace("occupancy-purpose", 43_200, null, null),
                new EligibilityCacheHealth.TrackedNamespace("property-type", 43_200, null, null),
                new EligibilityCacheHealth.TrackedNamespace("investor-overlay", 14_400, null, null)
            )
        );
    }

    public UUID recordInvalidation(UUID tenantId, String namespace, String versionToken, String reason, UUID requestedBy) {
        if (tenantId == null || requestedBy == null) {
            throw new IllegalArgumentException("tenantId and requestedBy are required");
        }
        String normalizedNamespace = required(namespace, "namespace");
        String normalizedVersionToken = required(versionToken, "versionToken");
        String normalizedReason = required(reason, "reason");
        UUID invalidationId = UUID.randomUUID();
        jdbc.update("""
            insert into eligibility.eligibility_cache_invalidation
              (tenant_id, invalidation_id, namespace, version_token, reason, requested_by, requested_at_utc, status)
            values (?, ?, ?, ?, ?, ?, ?, 'PENDING')
            """, tenantId, invalidationId, normalizedNamespace, normalizedVersionToken, normalizedReason, requestedBy, Instant.now());
        if (redis != null) {
            applyRedisInvalidation(tenantId, invalidationId, normalizedNamespace);
        }
        increment("invalidation_requested", normalizedNamespace);
        return invalidationId;
    }

    public EligibilityCacheKey decisionKey(UUID tenantId, EligibilityRequest request, String ruleVersionGraphHash) {
        return EligibilityCacheKey.decision(
            tenantId,
            request.resolvedQuoteType().name(),
            hash(request),
            hash(request.productCandidate()),
            ruleVersionGraphHash
        );
    }

    private Instant lastInvalidationAt(UUID tenantId) {
        try {
            return jdbc.queryForObject(
                "select max(requested_at_utc) from eligibility.eligibility_cache_invalidation where tenant_id = ?",
                Instant.class,
                tenantId
            );
        } catch (Exception ex) {
            return null;
        }
    }

    private String hash(Object value) {
        try {
            return Hashing.sha256(mapper.writeValueAsString(value));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to hash cache key material", ex);
        }
    }

    private static String required(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }

    private void applyRedisInvalidation(UUID tenantId, UUID invalidationId, String namespace) {
        try {
            long deleted = deleteRedisKeys("eligibility:decision:" + tenantId + ":*")
                + deleteRedisKeys("eligibility:cfg:" + namespace + ":" + tenantId + ":*");
            jdbc.update("""
                update eligibility.eligibility_cache_invalidation
                set status = 'PROCESSED', processed_at_utc = ?
                where invalidation_id = ?
                """, Instant.now(), invalidationId);
            increment(deleted > 0 ? "invalidation_processed" : "invalidation_no_keys", namespace);
        } catch (Exception ex) {
            jdbc.update("""
                update eligibility.eligibility_cache_invalidation
                set status = 'FAILED', error_message = ?
                where invalidation_id = ?
                """, left(ex.getMessage(), 1000), invalidationId);
            increment("redis_error", namespace);
        }
    }

    private long deleteRedisKeys(String pattern) {
        long deleted = 0;
        List<String> batch = new ArrayList<>(500);
        try (Cursor<String> cursor = redis.scan(ScanOptions.scanOptions().match(pattern).count(500).build())) {
            while (cursor.hasNext()) {
                batch.add(cursor.next());
                if (batch.size() == 500) {
                    deleted += deleteRedisBatch(batch);
                    batch.clear();
                }
            }
            deleted += deleteRedisBatch(batch);
        } catch (Exception ex) {
            throw new IllegalStateException("Redis cache invalidation scan failed", ex);
        }
        return deleted;
    }

    private long deleteRedisBatch(List<String> keys) {
        if (keys.isEmpty()) {
            return 0;
        }
        Long deleted = redis.delete(List.copyOf(keys));
        return deleted == null ? 0 : deleted;
    }

    private static String left(String value, int maxLength) {
        String text = value == null || value.isBlank() ? "Redis cache invalidation failed" : value;
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private void increment(String name, String namespace) {
        Counter.builder("eligibility_cache_" + name + "_total")
            .tag("namespace", namespace)
            .register(meterRegistry)
            .increment();
    }
}
