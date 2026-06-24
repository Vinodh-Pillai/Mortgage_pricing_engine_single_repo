package com.wcpe.quote;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.quote.LoanPassQuoteModels.CatalogProduct;
import com.wcpe.quote.LoanPassQuoteModels.CatalogSnapshot;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

public class JdbcLoanPassQuoteCatalogRepository implements LoanPassQuoteCatalogRepository {
    private static final TypeReference<List<Integer>> INTEGER_LIST = new TypeReference<>() {
    };
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {
    };

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;

    public JdbcLoanPassQuoteCatalogRepository(DataSource dataSource, ObjectMapper objectMapper) {
        this.dataSource = dataSource;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<CatalogSnapshot> activeSnapshot(UUID tenantId) {
        String sql = "select tenant_id, snapshot_id, source_system, synthetic, generator_version, seed, "
            + "schema_version, loaded_at, payload_hash, metadata from loanpass_quote_catalog_snapshot "
            + "where tenant_id = ? order by loaded_at desc limit 1";
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, tenantId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                String snapshotId = resultSet.getString("snapshot_id");
                return Optional.of(new CatalogSnapshot(
                    resultSet.getObject("tenant_id", UUID.class),
                    snapshotId,
                    resultSet.getString("source_system"),
                    resultSet.getBoolean("synthetic"),
                    resultSet.getString("generator_version"),
                    resultSet.getString("seed"),
                    resultSet.getString("schema_version"),
                    resultSet.getTimestamp("loaded_at").toInstant(),
                    resultSet.getString("payload_hash"),
                    products(connection, tenantId, snapshotId),
                    JdbcJson.read(objectMapper, resultSet.getString("metadata"), STRING_MAP)
                ));
            }
        } catch (SQLException ex) {
            throw persistenceFailure("LOANPASS_CATALOG_FIND_FAILED", ex);
        }
    }

    @Override
    public CatalogSnapshot saveSnapshot(CatalogSnapshot snapshot) {
        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                upsertSnapshot(connection, snapshot);
                replaceProducts(connection, snapshot);
                upsertSourcePayload(connection, snapshot);
                connection.commit();
                return snapshot;
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException ex) {
            throw persistenceFailure("LOANPASS_CATALOG_SAVE_FAILED", ex);
        }
    }

    private void upsertSnapshot(Connection connection, CatalogSnapshot snapshot) throws SQLException {
        String sql = "insert into loanpass_quote_catalog_snapshot (tenant_id, snapshot_id, source_system, synthetic, "
            + "generator_version, seed, schema_version, loaded_at, payload_hash, metadata) "
            + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
            + "on conflict (tenant_id, snapshot_id) do update set source_system = excluded.source_system, "
            + "synthetic = excluded.synthetic, generator_version = excluded.generator_version, seed = excluded.seed, "
            + "schema_version = excluded.schema_version, loaded_at = excluded.loaded_at, payload_hash = excluded.payload_hash, "
            + "metadata = excluded.metadata";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, snapshot.tenantId());
            statement.setString(2, snapshot.snapshotId());
            statement.setString(3, snapshot.sourceSystem());
            statement.setBoolean(4, snapshot.synthetic());
            statement.setString(5, snapshot.generatorVersion());
            statement.setString(6, snapshot.seed());
            statement.setString(7, snapshot.schemaVersion());
            statement.setTimestamp(8, Timestamp.from(snapshot.loadedAt()));
            statement.setString(9, snapshot.payloadHash());
            statement.setObject(10, JdbcJson.jsonb(objectMapper, snapshot.metadata()));
            statement.executeUpdate();
        }
    }

    private void replaceProducts(Connection connection, CatalogSnapshot snapshot) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement("delete from loanpass_quote_catalog_product where tenant_id = ? and snapshot_id = ?")) {
            delete.setObject(1, snapshot.tenantId());
            delete.setString(2, snapshot.snapshotId());
            delete.executeUpdate();
        }
        String sql = "insert into loanpass_quote_catalog_product (tenant_id, snapshot_id, product_id, product_name, "
            + "investor_name, product_type, status, lock_periods, note_rate_pct, price_bps, rules, stipulations, "
            + "rejections, source_refs) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (CatalogProduct product : snapshot.products()) {
                statement.setObject(1, snapshot.tenantId());
                statement.setString(2, snapshot.snapshotId());
                statement.setString(3, product.productId());
                statement.setString(4, product.productName());
                statement.setString(5, product.investorName());
                statement.setString(6, product.productType());
                statement.setString(7, product.status());
                statement.setObject(8, JdbcJson.jsonb(objectMapper, product.lockPeriods()));
                statement.setBigDecimal(9, product.noteRatePercent());
                statement.setBigDecimal(10, product.priceBps());
                statement.setObject(11, JdbcJson.jsonb(objectMapper, product.rules()));
                statement.setObject(12, JdbcJson.jsonb(objectMapper, product.stipulations()));
                statement.setObject(13, JdbcJson.jsonb(objectMapper, product.rejections()));
                statement.setObject(14, JdbcJson.jsonb(objectMapper, product.sourceRefs()));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void upsertSourcePayload(Connection connection, CatalogSnapshot snapshot) throws SQLException {
        String sql = "insert into loanpass_quote_catalog_source_payload (tenant_id, snapshot_id, payload_hash, "
            + "source_system, synthetic, payload) values (?, ?, ?, ?, ?, ?) "
            + "on conflict (tenant_id, snapshot_id, payload_hash) do nothing";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, snapshot.tenantId());
            statement.setString(2, snapshot.snapshotId());
            statement.setString(3, snapshot.payloadHash());
            statement.setString(4, snapshot.sourceSystem());
            statement.setBoolean(5, snapshot.synthetic());
            statement.setObject(6, JdbcJson.jsonb(objectMapper, Map.of("metadata", snapshot.metadata(), "productCount", Integer.toString(snapshot.products().size()))));
            statement.executeUpdate();
        }
    }

    private List<CatalogProduct> products(Connection connection, UUID tenantId, String snapshotId) throws SQLException {
        String sql = "select product_id, product_name, investor_name, product_type, status, lock_periods, note_rate_pct, "
            + "price_bps, rules, stipulations, rejections, source_refs from loanpass_quote_catalog_product "
            + "where tenant_id = ? and snapshot_id = ? order by product_id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, tenantId);
            statement.setString(2, snapshotId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<CatalogProduct> products = new ArrayList<>();
                while (resultSet.next()) {
                    products.add(new CatalogProduct(
                        resultSet.getString("product_id"),
                        resultSet.getString("product_name"),
                        resultSet.getString("investor_name"),
                        resultSet.getString("product_type"),
                        resultSet.getString("status"),
                        JdbcJson.read(objectMapper, resultSet.getString("lock_periods"), INTEGER_LIST),
                        resultSet.getBigDecimal("note_rate_pct"),
                        resultSet.getBigDecimal("price_bps"),
                        JdbcJson.read(objectMapper, resultSet.getString("rules"), STRING_MAP),
                        JdbcJson.read(objectMapper, resultSet.getString("stipulations"), STRING_LIST),
                        JdbcJson.read(objectMapper, resultSet.getString("rejections"), STRING_LIST),
                        JdbcJson.read(objectMapper, resultSet.getString("source_refs"), STRING_MAP)
                    ));
                }
                return List.copyOf(products);
            }
        }
    }

    private static IllegalStateException persistenceFailure(String code, SQLException ex) {
        return new IllegalStateException(code + ": PostgreSQL LoanPass quote catalog persistence failed closed", ex);
    }
}
