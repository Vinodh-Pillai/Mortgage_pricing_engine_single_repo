package com.wcpe.adjustment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.wcpe.adjustment.AdjustmentRuleBook.AdjustmentCondition;
import com.wcpe.adjustment.AdjustmentRuleBook.AdjustmentOutput;
import com.wcpe.adjustment.AdjustmentRuleBook.AdjustmentOutputType;
import com.wcpe.adjustment.AdjustmentRuleBook.AdjustmentRule;
import com.wcpe.adjustment.AdjustmentRuleBook.ConditionOperator;
import com.wcpe.adjustment.AdjustmentRuleBook.EffectiveWindow;
import com.wcpe.adjustment.AdjustmentRuleBook.PricingPrecisionPolicy;
import com.wcpe.adjustment.AdjustmentRuleBook.RuleBookSelector;
import com.wcpe.adjustment.AdjustmentRuleBook.RuleBookStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

/** Resolves published rule books through a repository with a 5 minute TTL cache. */
public final class RuleBookResolver {
    public static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

    private final RuleBookRepository repository;
    private final Clock clock;
    private final Duration ttl;
    private final Cache<CacheKey, Optional<AdjustmentRuleBook>> ruleBookCache;

    public RuleBookResolver(RuleBookRepository repository) {
        this(repository, Clock.systemUTC(), DEFAULT_TTL);
    }

    public RuleBookResolver(RuleBookRepository repository, Clock clock, Duration ttl) {
        this.repository = Objects.requireNonNull(repository, "repository is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
        this.ttl = Objects.requireNonNull(ttl, "ttl is required");
        this.ruleBookCache = Caffeine.newBuilder().expireAfterWrite(ttl).build();
    }

    public Optional<AdjustmentRuleBook> resolve(UUID tenantId, RuleBookSelector selector, Instant quoteDate) {
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(selector, "selector is required");
        Objects.requireNonNull(quoteDate, "quoteDate is required");
        CacheKey key = new CacheKey(tenantId, selector, quoteDate);
        return ruleBookCache.get(key, ignored -> repository.findPublished(tenantId, selector, quoteDate).stream()
            .filter(book -> book.status() == RuleBookStatus.PUBLISHED)
            .filter(book -> book.selector().matches(selector))
            .filter(book -> book.effectiveWindow().contains(quoteDate))
            .max(Comparator.comparing(AdjustmentRuleBook::publishedAt)));
    }

    public void invalidate(UUID tenantId, RuleBookSelector selector) {
        ruleBookCache.asMap().keySet().removeIf(key -> key.tenantId().equals(tenantId) && key.selector().equals(selector));
    }

    public void invalidateAll() {
        ruleBookCache.invalidateAll();
    }

    private record CacheKey(UUID tenantId, RuleBookSelector selector, Instant quoteDate) {}

    public interface RuleBookRepository {
        List<AdjustmentRuleBook> findPublished(UUID tenantId, RuleBookSelector selector, Instant quoteDate);
    }

    public static final class InMemoryRuleBookRepository implements RuleBookRepository {
        private final List<AdjustmentRuleBook> ruleBooks;

        public InMemoryRuleBookRepository(List<AdjustmentRuleBook> ruleBooks) {
            this.ruleBooks = List.copyOf(ruleBooks == null ? List.of() : ruleBooks);
        }

        @Override
        public List<AdjustmentRuleBook> findPublished(UUID tenantId, RuleBookSelector selector, Instant quoteDate) {
            return ruleBooks.stream()
                .filter(book -> book.tenantId().equals(tenantId))
                .filter(book -> book.status() == RuleBookStatus.PUBLISHED)
                .filter(book -> book.selector().matches(selector))
                .filter(book -> book.effectiveWindow().contains(quoteDate))
                .toList();
        }
    }

    public static final class JdbcRuleBookRepository implements RuleBookRepository {
        private final DataSource dataSource;
        private final ObjectMapper mapper = new ObjectMapper();

        public JdbcRuleBookRepository(DataSource dataSource) {
            this.dataSource = Objects.requireNonNull(dataSource, "dataSource is required");
        }

        @Override
        public List<AdjustmentRuleBook> findPublished(UUID tenantId, RuleBookSelector selector, Instant quoteDate) {
            String sql = """
                select tenant_id, rule_book_id, business_key, version, status, product_family,
                       investor_selector, channel_selector, effective_start, effective_end,
                       precision_policy, content_hash, created_by, approved_by, published_at,
                       max_total_points_delta, min_total_points_delta
                  from adjustment_rule_books
                 where tenant_id = ? and status = 'PUBLISHED' and product_family = ?
                   and effective_start <= ? and (effective_end is null or effective_end > ?)
                 order by published_at desc
                """;
            try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setObject(1, tenantId);
                statement.setString(2, selector.productFamily());
                statement.setTimestamp(3, Timestamp.from(quoteDate));
                statement.setTimestamp(4, Timestamp.from(quoteDate));
                List<AdjustmentRuleBook> books = new ArrayList<>();
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        RuleBookSelector rowSelector = new RuleBookSelector(
                            rs.getString("product_family"),
                            selectorValue(rs.getString("investor_selector"), selector.investor()),
                            selectorValue(rs.getString("channel_selector"), selector.channel())
                        );
                        if (!rowSelector.matches(selector)) {
                            continue;
                        }
                        UUID ruleBookId = (UUID) rs.getObject("rule_book_id");
                        Timestamp effectiveEnd = rs.getTimestamp("effective_end");
                        books.add(new AdjustmentRuleBook(
                            (UUID) rs.getObject("tenant_id"), ruleBookId, rs.getString("business_key"), rs.getString("version"),
                            RuleBookStatus.valueOf(rs.getString("status")), rowSelector,
                            new EffectiveWindow(rs.getTimestamp("effective_start").toInstant(), effectiveEnd == null ? null : effectiveEnd.toInstant()),
                            precisionPolicy(rs.getString("precision_policy")), findRules(connection, tenantId, ruleBookId),
                            rs.getString("created_by"), rs.getString("approved_by"), rs.getTimestamp("published_at").toInstant(),
                            rs.getString("content_hash"), rs.getBigDecimal("max_total_points_delta"), rs.getBigDecimal("min_total_points_delta")
                        ));
                    }
                }
                return books;
            } catch (SQLException ex) {
                throw new IllegalStateException("failed to query published adjustment rule books", ex);
            }
        }

        private List<AdjustmentRule> findRules(Connection connection, UUID tenantId, UUID ruleBookId) throws SQLException {
            String sql = """
                select rule_id, priority, conditions, output, reason_code, exclusivity_group,
                       enabled, source_ref, min_output, max_output
                  from adjustment_rules
                 where tenant_id = ? and rule_book_id = ? and enabled = true
                 order by priority asc, rule_id asc
                """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setObject(1, tenantId);
                statement.setObject(2, ruleBookId);
                List<AdjustmentRule> rules = new ArrayList<>();
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        rules.add(new AdjustmentRule(
                            (UUID) rs.getObject("rule_id"), rs.getInt("priority"), conditions(rs.getString("conditions")),
                            output(rs.getString("output")), rs.getString("reason_code"), rs.getString("exclusivity_group"),
                            rs.getBoolean("enabled"), rs.getString("source_ref"), rs.getBigDecimal("min_output"), rs.getBigDecimal("max_output")
                        ));
                    }
                }
                return rules;
            }
        }

        private List<AdjustmentCondition> conditions(String json) {
            try {
                JsonNode root = mapper.readTree(json == null ? "[]" : json);
                JsonNode array = root.isArray() ? root : root.path("conditions");
                List<AdjustmentCondition> conditions = new ArrayList<>();
                for (JsonNode node : array) {
                    List<String> values = new ArrayList<>();
                    JsonNode configured = node.path("values");
                    if (!configured.isArray()) {
                        configured = node.path("configuredValues");
                    }
                    for (JsonNode value : configured) {
                        values.add(value.asText());
                    }
                    conditions.add(new AdjustmentCondition(node.path("dimension").asText(), ConditionOperator.valueOf(node.path("operator").asText()), values));
                }
                return conditions;
            } catch (Exception ex) {
                throw new IllegalArgumentException("invalid adjustment rule condition json", ex);
            }
        }

        private AdjustmentOutput output(String json) {
            try {
                JsonNode root = mapper.readTree(json == null ? "{}" : json);
                AdjustmentOutputType type = AdjustmentOutputType.valueOf(root.path("type").asText());
                BigDecimal amount = root.hasNonNull("amount") ? new BigDecimal(root.path("amount").asText()) : null;
                if (amount == null && root.hasNonNull("configuredAmount")) {
                    amount = new BigDecimal(root.path("configuredAmount").asText());
                }
                String label = root.hasNonNull("label") ? root.path("label").asText() : root.path("configuredLabel").asText(null);
                return new AdjustmentOutput(type, amount, label);
            } catch (Exception ex) {
                throw new IllegalArgumentException("invalid adjustment rule output json", ex);
            }
        }

        private PricingPrecisionPolicy precisionPolicy(String json) {
            try {
                JsonNode root = mapper.readTree(json == null || json.isBlank() ? "{}" : json);
                return new PricingPrecisionPolicy(root.path("pointsScale").asInt(6), root.path("bpsScale").asInt(4),
                    root.path("moneyScale").asInt(2), RoundingMode.valueOf(root.path("roundingMode").asText("HALF_UP")));
            } catch (Exception ex) {
                throw new IllegalArgumentException("invalid precision policy json", ex);
            }
        }

        private String selectorValue(String json, String fallback) {
            try {
                JsonNode root = mapper.readTree(json == null || json.isBlank() ? "{}" : json);
                if (root.isTextual()) {
                    return root.asText();
                }
                if (root.hasNonNull("value")) {
                    return root.path("value").asText();
                }
                return root.hasNonNull("code") ? root.path("code").asText() : fallback;
            } catch (Exception ex) {
                return fallback;
            }
        }
    }
}
