package com.wcpe.catalog.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class TenantProductAuthorizationService {
    public static final Duration AUTH_CACHE_TTL = Duration.ofMinutes(5);

    private final JdbcTemplate jdbc;
    private final Cache<UUID, List<TenantProductAuthorization>> authCache;
    private final Function<UUID, List<TenantProductAuthorization>> loader;
    private final ApplicationEventPublisher events;

    public TenantProductAuthorizationService(JdbcTemplate jdbc, ApplicationEventPublisher events) {
        this(jdbc, Caffeine.newBuilder().expireAfterWrite(AUTH_CACHE_TTL).build(), null, events);
    }

    TenantProductAuthorizationService(
        JdbcTemplate jdbc,
        Cache<UUID, List<TenantProductAuthorization>> authCache,
        Function<UUID, List<TenantProductAuthorization>> loader,
        ApplicationEventPublisher events
    ) {
        this.jdbc = jdbc;
        this.authCache = authCache;
        this.loader = loader == null ? this::loadActiveAuthorizations : loader;
        this.events = events;
    }

    public Duration cacheTtl() {
        return AUTH_CACHE_TTL;
    }

    public boolean isAuthorized(UUID tenantId, String productCode, String investorCode, String channelCode) {
        return isAuthorized(tenantId, productCode, investorCode, channelCode, Instant.now());
    }

    public boolean isAuthorized(UUID tenantId, String productCode, String investorCode, String channelCode, Instant asOf) {
        return getAuthorizedRulesAsOf(tenantId, asOf).stream()
            .anyMatch(authorization -> authorization.matches(productCode, investorCode, channelCode));
    }

    public List<AuthorizedProductCandidate> filterAuthorized(
        UUID tenantId,
        List<AuthorizedProductCandidate> candidates,
        String channelCode
    ) {
        return filterAuthorized(
            tenantId,
            candidates,
            channelCode,
            Instant.now(),
            AuthorizedProductCandidate::productCode,
            AuthorizedProductCandidate::investorCode,
            AuthorizedProductCandidate::channelCode
        );
    }

    public <T> List<T> filterAuthorized(
        UUID tenantId,
        List<T> candidates,
        String channelCode,
        Instant asOf,
        Function<T, String> productCode,
        Function<T, String> investorCode,
        Function<T, String> candidateChannelCode
    ) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required");
        }
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        List<TenantProductAuthorization> rules = getAuthorizedRulesAsOf(tenantId, asOf);
        String requestedChannel = TenantProductAuthorization.normalizeOptional(channelCode);
        return candidates.stream()
            .filter(candidate -> requestedChannel == null || requestedChannel.equals(TenantProductAuthorization.normalizeOptional(candidateChannelCode.apply(candidate))))
            .filter(candidate -> rules.stream().anyMatch(authorization -> authorization.matches(productCode.apply(candidate), investorCode.apply(candidate), candidateChannelCode.apply(candidate))))
            .toList();
    }

    public List<TenantProductAuthorization> getAuthorizedRules(UUID tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required");
        }
        return authCache.get(tenantId, loader);
    }

    public List<TenantProductAuthorization> getAuthorizedRulesAsOf(UUID tenantId, Instant asOf) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required");
        }
        Instant effectiveAsOf = asOf == null ? Instant.now() : asOf;
        if (jdbc == null) {
            return getAuthorizedRules(tenantId).stream()
                .filter(authorization -> authorization.isActiveAt(effectiveAsOf))
                .toList();
        }
        return jdbc.query("""
            select tenant_id, product_code, investor_code, channel_code, status, authorized_at, authorized_by, expires_at, notes
              from catalog.tenant_product_authorization
             where tenant_id = ?
               and status = 'ACTIVE'
               and authorized_at <= ?
               and (expires_at is null or expires_at > ?)
             order by product_code, investor_code nulls first, channel_code nulls first, authorized_at desc
            """, (rs, rowNum) -> new TenantProductAuthorization(
                rs.getObject("tenant_id", UUID.class),
                rs.getString("product_code"),
                rs.getString("investor_code"),
                rs.getString("channel_code"),
                rs.getString("status"),
                rs.getTimestamp("authorized_at").toInstant(),
                rs.getString("authorized_by"),
                rs.getTimestamp("expires_at") == null ? null : rs.getTimestamp("expires_at").toInstant(),
                rs.getString("notes")
            ), tenantId, Timestamp.from(effectiveAsOf), Timestamp.from(effectiveAsOf));
    }

    public List<TenantProductAuthorization> list(UUID tenantId, String productCode, String status) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
            select tenant_id, product_code, investor_code, channel_code, status, authorized_at, authorized_by, expires_at, notes
            from catalog.tenant_product_authorization
            where 1 = 1
            """);
        if (tenantId != null) {
            sql.append(" and tenant_id = ?");
            args.add(tenantId);
        }
        if (productCode != null && !productCode.isBlank()) {
            sql.append(" and product_code = ?");
            args.add(TenantProductAuthorization.normalizeRequired(productCode, "productCode"));
        }
        if (status != null && !status.isBlank()) {
            sql.append(" and status = ?");
            args.add(TenantProductAuthorization.normalizeRequired(status, "status"));
        }
        sql.append(" order by tenant_id, product_code, investor_code nulls first, channel_code nulls first");
        return jdbc.query(sql.toString(), (rs, rowNum) -> new TenantProductAuthorization(
            rs.getObject("tenant_id", UUID.class),
            rs.getString("product_code"),
            rs.getString("investor_code"),
            rs.getString("channel_code"),
            rs.getString("status"),
            rs.getTimestamp("authorized_at").toInstant(),
            rs.getString("authorized_by"),
            rs.getTimestamp("expires_at") == null ? null : rs.getTimestamp("expires_at").toInstant(),
            rs.getString("notes")
        ), args.toArray());
    }

    public TenantProductAuthorization save(TenantProductAuthorizationCommand command, String actorId) {
        TenantProductAuthorization authorization = new TenantProductAuthorization(
            command.tenantId(),
            command.productCode(),
            command.investorCode(),
            command.channelCode(),
            command.status() == null ? "ACTIVE" : command.status(),
            Instant.now(),
            actorId,
            command.expiresAt(),
            command.notes()
        );
        jdbc.update("""
            insert into catalog.tenant_product_authorization
              (tenant_id, product_code, investor_code, channel_code, status, authorized_at, authorized_by, expires_at, notes)
            values (?, ?, ?, ?, ?, now(), ?, ?, ?)
            on conflict (tenant_id, product_code, coalesce(investor_code, '*'), coalesce(channel_code, '*'))
            do update set status = excluded.status,
                          authorized_at = now(),
                          authorized_by = excluded.authorized_by,
                          expires_at = excluded.expires_at,
                          notes = excluded.notes
            """,
            authorization.tenantId(),
            authorization.productCode(),
            authorization.investorCode(),
            authorization.channelCode(),
            authorization.status(),
            authorization.authorizedBy(),
            authorization.expiresAt() == null ? null : Timestamp.from(authorization.expiresAt()),
            authorization.notes()
        );
        publishChanged(authorization.tenantId());
        return authorization;
    }

    public TenantProductAuthorization update(UUID tenantId, String productCode, String investorCode, String channelCode, TenantProductAuthorizationPatch patch) {
        jdbc.update("""
            update catalog.tenant_product_authorization
               set status = coalesce(?, status),
                   expires_at = ?,
                   notes = coalesce(?, notes)
             where tenant_id = ?
               and product_code = ?
               and coalesce(investor_code, '*') = coalesce(?, '*')
               and coalesce(channel_code, '*') = coalesce(?, '*')
            """,
            patch.status() == null ? null : TenantProductAuthorization.normalizeRequired(patch.status(), "status"),
            patch.expiresAt() == null ? null : Timestamp.from(patch.expiresAt()),
            patch.notes(),
            tenantId,
            TenantProductAuthorization.normalizeRequired(productCode, "productCode"),
            TenantProductAuthorization.normalizeOptional(investorCode),
            TenantProductAuthorization.normalizeOptional(channelCode)
        );
        publishChanged(tenantId);
        return list(tenantId, productCode, null).stream()
            .filter(authorization -> authorization.matches(productCode, investorCode, channelCode))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("AUTHORIZATION_NOT_FOUND"));
    }

    @EventListener
    public void onAuthorizationChanged(TenantAuthorizationChangedEvent event) {
        if (event != null && event.tenantId() != null) {
            authCache.invalidate(event.tenantId());
        }
    }

    private void publishChanged(UUID tenantId) {
        authCache.invalidate(tenantId);
        if (events != null) {
            events.publishEvent(new TenantAuthorizationChangedEvent(tenantId));
        }
    }

    private List<TenantProductAuthorization> loadActiveAuthorizations(UUID tenantId) {
        return jdbc.query("""
            select tenant_id, product_code, investor_code, channel_code, status, authorized_at, authorized_by, expires_at, notes
              from catalog.tenant_product_authorization
             where tenant_id = ?
               and status = 'ACTIVE'
               and (expires_at is null or expires_at > now())
            """, (rs, rowNum) -> new TenantProductAuthorization(
                rs.getObject("tenant_id", UUID.class),
                rs.getString("product_code"),
                rs.getString("investor_code"),
                rs.getString("channel_code"),
                rs.getString("status"),
                rs.getTimestamp("authorized_at").toInstant(),
                rs.getString("authorized_by"),
                rs.getTimestamp("expires_at") == null ? null : rs.getTimestamp("expires_at").toInstant(),
                rs.getString("notes")
            ), tenantId);
    }
}
