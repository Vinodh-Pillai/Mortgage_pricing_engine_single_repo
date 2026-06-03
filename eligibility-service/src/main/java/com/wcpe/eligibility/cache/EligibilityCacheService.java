package com.wcpe.eligibility.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.eligibility.domain.hashing.Hashing;
import com.wcpe.eligibility.domain.models.EligibilityRequest;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
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

    private void increment(String name, String namespace) {
        Counter.builder("eligibility_cache_" + name + "_total")
            .tag("namespace", namespace)
            .register(meterRegistry)
            .increment();
    }
}
