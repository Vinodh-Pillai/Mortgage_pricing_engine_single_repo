package com.wcpe.eligibility;

import com.wcpe.eligibility.cache.EligibilityCacheKey;
import com.wcpe.eligibility.cache.EligibilityCacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EligibilityCacheKeyTest {
    @Test
    void includesTenantFamilyQuoteTypeAndVersion() {
        UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        EligibilityCacheKey key = EligibilityCacheKey.config(
            tenantId,
            "CONVENTIONAL",
            "CONVENTIONAL_PURCHASE",
            "loan-limit",
            "version-7",
            "FNMA",
            "2026",
            "TX"
        );

        assertTrue(key.value().contains(tenantId.toString()));
        assertTrue(key.value().contains("CONVENTIONAL:CONVENTIONAL_PURCHASE"));
        assertTrue(key.value().contains("version-7"));
    }

    @Test
    void cacheInvalidationIsPersistedAsPendingDurableRecord() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ObjectProvider<StringRedisTemplate> redisProvider = mock(ObjectProvider.class);
        when(redisProvider.getIfAvailable()).thenReturn(null);
        EligibilityCacheService service = new EligibilityCacheService(new ObjectMapper(), redisProvider, jdbc, new SimpleMeterRegistry());
        UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID actorId = UUID.fromString("22222222-2222-2222-2222-222222222222");

        UUID invalidationId = service.recordInvalidation(tenantId, "product-catalog", "catalog-version-9", "catalog.authorization.changed", actorId);

        assertTrue(invalidationId.toString().length() > 0);
        verify(jdbc).update(contains("insert into eligibility.eligibility_cache_invalidation"),
            eq(tenantId), eq(invalidationId), eq("product-catalog"), eq("catalog-version-9"), eq("catalog.authorization.changed"), eq(actorId), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void cacheInvalidationDeletesRedisTenantKeysAndMarksRecordProcessed() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ObjectProvider<StringRedisTemplate> redisProvider = mock(ObjectProvider.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redisProvider.getIfAvailable()).thenReturn(redis);
        EligibilityCacheService service = new EligibilityCacheService(new ObjectMapper(), redisProvider, jdbc, new SimpleMeterRegistry());
        UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID actorId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        List<String> decisionKeys = List.of("eligibility:decision:" + tenantId + ":hash:product:rules");
        List<String> configKeys = List.of("eligibility:cfg:product-catalog:" + tenantId + ":CONVENTIONAL:PURCHASE:v9");
        Cursor<String> decisionCursor = cursor(decisionKeys);
        Cursor<String> configCursor = cursor(configKeys);
        when(redis.scan(any())).thenReturn(decisionCursor, configCursor);
        when(redis.delete(decisionKeys)).thenReturn(1L);
        when(redis.delete(configKeys)).thenReturn(1L);

        UUID invalidationId = service.recordInvalidation(tenantId, "product-catalog", "catalog-version-9", "catalog.authorization.changed", actorId);

        verify(redis).delete(decisionKeys);
        verify(redis).delete(configKeys);
        verify(redis, never()).keys(anyString());
        verify(jdbc).update(contains("set status = 'PROCESSED'"), org.mockito.ArgumentMatchers.any(), eq(invalidationId));
    }

    private static Cursor<String> cursor(List<String> values) {
        @SuppressWarnings("unchecked")
        Cursor<String> cursor = mock(Cursor.class);
        java.util.Iterator<String> iterator = values.iterator();
        when(cursor.hasNext()).thenAnswer(ignored -> iterator.hasNext());
        when(cursor.next()).thenAnswer(ignored -> iterator.next());
        return cursor;
    }
}
