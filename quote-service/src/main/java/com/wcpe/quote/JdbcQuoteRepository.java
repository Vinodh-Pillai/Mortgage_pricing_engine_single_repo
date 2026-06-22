package com.wcpe.quote;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

public class JdbcQuoteRepository implements QuoteRepository {
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {
    };

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;

    public JdbcQuoteRepository(DataSource dataSource, ObjectMapper objectMapper) {
        this.dataSource = dataSource;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<Quote> findByIdempotencyKey(UUID tenantId, String idempotencyKey) {
        return findQuote("tenant_id = ? and idempotency_key = ?", statement -> {
            statement.setObject(1, tenantId);
            statement.setString(2, idempotencyKey);
        });
    }

    @Override
    public Optional<Quote> findById(UUID tenantId, UUID quoteId) {
        return findQuote("tenant_id = ? and quote_id = ?", statement -> {
            statement.setObject(1, tenantId);
            statement.setObject(2, quoteId);
        });
    }

    @Override
    public Quote save(Quote quote) {
        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                upsertQuote(connection, quote);
                replaceOptions(connection, quote);
                connection.commit();
                return quote;
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException ex) {
            throw persistenceFailure("QUOTE_PERSISTENCE_SAVE_FAILED", ex);
        }
    }

    private Optional<Quote> findQuote(String predicate, SqlBinder binder) {
        String sql = "select tenant_id, quote_id, scenario_id, scenario_version, status, ranking_policy_id, "
            + "ranking_policy_version, input_version_set, expires_at, replay_hash, idempotency_key, "
            + "created_by, created_at, correlation_id, version from quote where " + predicate;
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapQuote(connection, resultSet));
            }
        } catch (SQLException ex) {
            throw persistenceFailure("QUOTE_PERSISTENCE_FIND_FAILED", ex);
        }
    }

    private void upsertQuote(Connection connection, Quote quote) throws SQLException {
        String sql = "insert into quote (tenant_id, quote_id, scenario_id, scenario_version, status, "
            + "ranking_policy_id, ranking_policy_version, input_version_set, requested_filters, expires_at, "
            + "replay_hash, idempotency_key, created_by, created_at, correlation_id, version) "
            + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
            + "on conflict (quote_id) do update set status = excluded.status, input_version_set = excluded.input_version_set, "
            + "requested_filters = excluded.requested_filters, expires_at = excluded.expires_at, replay_hash = excluded.replay_hash, "
            + "created_by = excluded.created_by, created_at = excluded.created_at, correlation_id = excluded.correlation_id, "
            + "version = excluded.version";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, quote.tenantId());
            statement.setObject(2, quote.quoteId());
            statement.setObject(3, quote.scenarioId());
            statement.setInt(4, quote.scenarioVersion());
            statement.setString(5, quote.status().name());
            statement.setString(6, quote.rankingPolicyId());
            statement.setString(7, quote.rankingPolicyVersion());
            statement.setObject(8, JdbcJson.jsonb(objectMapper, quote.inputVersionSet()));
            statement.setObject(9, JdbcJson.jsonb(objectMapper, Map.of()));
            statement.setTimestamp(10, Timestamp.from(quote.expiresAt()));
            statement.setString(11, quote.replayHash());
            statement.setString(12, quote.idempotencyKey());
            statement.setString(13, quote.createdBy());
            statement.setTimestamp(14, Timestamp.from(quote.createdAt()));
            statement.setString(15, quote.correlationId());
            statement.setInt(16, quote.version());
            statement.executeUpdate();
        }
    }

    private void replaceOptions(Connection connection, Quote quote) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement("delete from quote_option where tenant_id = ? and quote_id = ?")) {
            delete.setObject(1, quote.tenantId());
            delete.setObject(2, quote.quoteId());
            delete.executeUpdate();
        }
        String sql = "insert into quote_option (tenant_id, option_id, quote_id, product_id, investor_id, channel, "
            + "lock_period_days, note_rate_pct, final_price_bps, total_adjustment_bps, margin_bps, waterfall, "
            + "rank, rank_score, rank_reasons, upstream_refs) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (QuoteOption option : quote.options()) {
                statement.setObject(1, quote.tenantId());
                statement.setObject(2, option.optionId());
                statement.setObject(3, quote.quoteId());
                statement.setString(4, option.productId());
                statement.setString(5, option.investorId());
                statement.setString(6, option.channel());
                statement.setInt(7, option.lockPeriodDays());
                statement.setBigDecimal(8, option.noteRatePercent());
                statement.setBigDecimal(9, option.finalPriceBps());
                statement.setBigDecimal(10, option.totalAdjustmentBps());
                statement.setBigDecimal(11, option.marginBps());
                statement.setObject(12, JdbcJson.jsonb(objectMapper, option.waterfall()));
                statement.setInt(13, option.rank());
                statement.setBigDecimal(14, option.rankScore());
                statement.setObject(15, JdbcJson.jsonb(objectMapper, option.rankReasons()));
                statement.setObject(16, JdbcJson.jsonb(objectMapper, option.upstreamRefs()));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private Quote mapQuote(Connection connection, ResultSet resultSet) throws SQLException {
        UUID tenantId = resultSet.getObject("tenant_id", UUID.class);
        UUID quoteId = resultSet.getObject("quote_id", UUID.class);
        List<QuoteOption> options = findOptions(connection, tenantId, quoteId, resultSet.getTimestamp("expires_at").toInstant());
        return new Quote(
            tenantId,
            quoteId,
            resultSet.getObject("scenario_id", UUID.class),
            resultSet.getInt("scenario_version"),
            QuoteStatus.valueOf(resultSet.getString("status")),
            resultSet.getString("ranking_policy_id"),
            resultSet.getString("ranking_policy_version"),
            JdbcJson.read(objectMapper, resultSet.getString("input_version_set"), QuoteInputVersionSet.class),
            options,
            resultSet.getTimestamp("expires_at").toInstant(),
            "audit:" + resultSet.getString("correlation_id"),
            resultSet.getString("replay_hash"),
            resultSet.getString("idempotency_key"),
            resultSet.getString("created_by"),
            resultSet.getTimestamp("created_at").toInstant(),
            resultSet.getString("correlation_id"),
            resultSet.getInt("version")
        );
    }

    private List<QuoteOption> findOptions(Connection connection, UUID tenantId, UUID quoteId, java.time.Instant expiresAt) throws SQLException {
        String sql = "select option_id, product_id, investor_id, channel, lock_period_days, note_rate_pct, final_price_bps, "
            + "total_adjustment_bps, margin_bps, waterfall, rank, rank_score, rank_reasons, upstream_refs "
            + "from quote_option where tenant_id = ? and quote_id = ? order by rank asc";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, tenantId);
            statement.setObject(2, quoteId);
            try (ResultSet resultSet = statement.executeQuery()) {
                java.util.ArrayList<QuoteOption> options = new java.util.ArrayList<>();
                while (resultSet.next()) {
                    options.add(new QuoteOption(
                        resultSet.getObject("option_id", UUID.class),
                        resultSet.getString("product_id"),
                        resultSet.getString("investor_id"),
                        resultSet.getString("channel"),
                        resultSet.getInt("lock_period_days"),
                        resultSet.getBigDecimal("note_rate_pct"),
                        resultSet.getBigDecimal("final_price_bps"),
                        resultSet.getBigDecimal("total_adjustment_bps"),
                        resultSet.getBigDecimal("margin_bps"),
                        JdbcJson.read(objectMapper, resultSet.getString("waterfall"), PriceWaterfall.class),
                        resultSet.getInt("rank"),
                        resultSet.getBigDecimal("rank_score"),
                        JdbcJson.read(objectMapper, resultSet.getString("rank_reasons"), STRING_LIST),
                        List.of(),
                        "",
                        List.of(),
                        JdbcJson.read(objectMapper, resultSet.getString("upstream_refs"), STRING_MAP),
                        expiresAt
                    ));
                }
                return List.copyOf(options);
            }
        }
    }

    private static IllegalStateException persistenceFailure(String code, SQLException ex) {
        return new IllegalStateException(code + ": PostgreSQL quote persistence failed closed instead of using in-memory source-of-truth", ex);
    }

    @FunctionalInterface
    private interface SqlBinder {
        void bind(PreparedStatement statement) throws SQLException;
    }
}
