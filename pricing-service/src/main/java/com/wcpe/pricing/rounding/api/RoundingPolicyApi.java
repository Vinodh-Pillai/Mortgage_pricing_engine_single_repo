package com.wcpe.pricing.rounding.api;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.sql.DataSource;

public final class RoundingPolicyApi {
    public static final String ROUNDING_READ_PERMISSION = "pricing.rounding.read";
    public static final String ROUNDING_WRITE_PERMISSION = "pricing.rounding.write";
    public static final String ROUNDING_APPROVE_PERMISSION = "pricing.rounding.approve";
    public static final String ROUNDING_PUBLISH_PERMISSION = "pricing.rounding.publish";

    private static final Map<RoundingUnit, Set<Integer>> SUPPORTED_SCALES = Map.of(
            RoundingUnit.NOTE_RATE, Set.of(5),
            RoundingUnit.PRICE, Set.of(5),
            RoundingUnit.POINTS, Set.of(5),
            RoundingUnit.BPS, Set.of(2),
            RoundingUnit.MONEY, Set.of(2));

    private final RoundingPolicyRepository repository;

    public RoundingPolicyApi(RoundingPolicyRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    public RoundingPolicyVersion createDraft(String tenantId, RoundingHeaders headers, CreateRoundingPolicyRequest request) {
        requireTenant(tenantId);
        requirePermission(headers, ROUNDING_WRITE_PERMISSION);
        requireText(headers.idempotencyKey(), "Idempotency-Key is required");
        if (request == null) {
            throw new RoundingPolicyValidationException("request is required");
        }
        if (!tenantId.equals(request.tenantId())) {
            throw new RoundingPolicyAccessDeniedException("request tenant does not match path tenant");
        }
        requireText(request.scope(), "scope is required");
        requireDate(request.effectiveFrom(), "effective_from is required");
        if (request.effectiveTo() != null && !request.effectiveTo().isAfter(request.effectiveFrom())) {
            throw new RoundingPolicyValidationException("effective_to must be after effective_from");
        }
        if (request.rules() == null || request.rules().isEmpty()) {
            throw new RoundingPolicyValidationException("at least one rounding rule is required");
        }

        List<RoundingRule> rules = request.rules().stream()
                .map(RoundingPolicyApi::validateRule)
                .sorted(Comparator.comparing(RoundingRule::precedence))
                .toList();
        List<RoundingSampleFixture> fixtures = request.fixtures() == null ? List.of() : List.copyOf(request.fixtures());
        RoundingPolicyVersion policy = new RoundingPolicyVersion(
                "rounding-policy-" + UUID.randomUUID(),
                tenantId,
                1,
                RoundingPolicyStatus.DRAFT,
                request.scope(),
                request.productCode(),
                request.investorCode(),
                request.channelCode(),
                request.effectiveFrom(),
                request.effectiveTo(),
                request.schemaVersion(),
                headers.actorId(),
                null,
                null,
                "audit-" + headers.correlationId(),
                headers.correlationId(),
                Instant.now(),
                rules,
                fixtures,
                false);
        repository.save(policy);
        return policy;
    }

    public RoundingPolicyValidationResult validatePolicy(String tenantId, String policyVersionId, RoundingHeaders headers) {
        requireTenant(tenantId);
        requireText(policyVersionId, "policy_version_id is required");
        requirePermission(headers, ROUNDING_WRITE_PERMISSION);
        RoundingPolicyVersion policy = requirePolicy(tenantId, policyVersionId);
        List<RoundingPolicyValidationMessage> messages = new ArrayList<>();
        for (RoundingSampleFixture fixture : policy.fixtures()) {
            RoundingRule rule = matchingRules(policy, fixture.outputContext()).stream()
                    .findFirst()
                    .orElse(null);
            if (rule == null) {
                messages.add(new RoundingPolicyValidationMessage(
                        "ROUNDING_POLICY_MISSING",
                        "No rule matches sample fixture output context " + fixture.outputContext()));
                continue;
            }
            RoundedValue rounded = rule.round(fixture.inputValue());
            if (rounded.outputValue().compareTo(fixture.expectedValue()) != 0) {
                messages.add(new RoundingPolicyValidationMessage(
                        "ROUNDING_SAMPLE_MISMATCH",
                        "Sample fixture " + fixture.fixtureName() + " did not match expected output"));
            }
        }
        boolean valid = messages.isEmpty();
        if (valid) {
            repository.save(policy.withValidationPassed(true));
        }
        return new RoundingPolicyValidationResult(policy.id(), valid, List.copyOf(messages));
    }

    public RoundingPolicyVersion publish(String tenantId, String policyVersionId, RoundingHeaders headers) {
        requireTenant(tenantId);
        requireText(policyVersionId, "policy_version_id is required");
        requirePermission(headers, ROUNDING_APPROVE_PERMISSION);
        requirePermission(headers, ROUNDING_PUBLISH_PERMISSION);
        RoundingPolicyVersion policy = requirePolicy(tenantId, policyVersionId);
        if (policy.status() != RoundingPolicyStatus.DRAFT) {
            throw new RoundingPolicyConflictException("published rounding policy versions are immutable");
        }
        if (Objects.equals(policy.createdBy(), headers.actorId())) {
            throw new RoundingPolicyValidationException("policy creator cannot approve their own policy");
        }
        if (!policy.validationPassed()) {
            throw new RoundingPolicyValidationException("policy must validate before publish");
        }
        if (repository.findPublishedForScope(tenantId, policy.scope(), policy.productCode(), policy.investorCode(), policy.channelCode())
                .stream()
                .anyMatch(existing -> windowsOverlap(existing.effectiveFrom(), existing.effectiveTo(), policy.effectiveFrom(), policy.effectiveTo()))) {
            throw new RoundingPolicyConflictException("ROUNDING_POLICY_AMBIGUOUS");
        }
        RoundingPolicyVersion published = policy.withPublished(headers.actorId(), Instant.now());
        repository.save(published);
        return published;
    }

    public RoundingPolicyResolution resolve(String tenantId, ResolveRoundingPolicyRequest request, RoundingHeaders headers) {
        requireTenant(tenantId);
        requirePermission(headers, ROUNDING_READ_PERMISSION);
        if (request == null) {
            throw new RoundingPolicyValidationException("request is required");
        }
        requireText(request.outputContext(), "output_context is required");
        requireDate(request.asOf(), "as_of is required");
        List<RoundingPolicyVersion> matchingPolicies = repository.findPublishedForScope(
                        tenantId, request.scope(), request.productCode(), request.investorCode(), request.channelCode())
                .stream()
                .filter(policy -> isEffective(policy, request.asOf()))
                .filter(policy -> matchingRules(policy, request.outputContext()).size() == 1)
                .toList();
        if (matchingPolicies.isEmpty()) {
            throw new RoundingPolicyNotSatisfiedException("ROUNDING_POLICY_MISSING");
        }
        if (matchingPolicies.size() > 1) {
            throw new RoundingPolicyConflictException("ROUNDING_POLICY_AMBIGUOUS");
        }
        RoundingPolicyVersion policy = matchingPolicies.get(0);
        RoundingRule rule = matchingRules(policy, request.outputContext()).get(0);
        RoundedValue rounded = request.inputValue() == null ? null : rule.round(request.inputValue());
        return new RoundingPolicyResolution(
                policy.id(),
                policy.versionNumber(),
                rule.ruleId(),
                rule.outputContext(),
                rule.unit(),
                rule.scale(),
                rule.roundingMode(),
                rule.increment(),
                rounded);
    }

    private RoundingPolicyVersion requirePolicy(String tenantId, String policyVersionId) {
        RoundingPolicyVersion policy = repository.findById(policyVersionId)
                .orElseThrow(() -> new RoundingPolicyNotFoundException("rounding policy not found"));
        if (!tenantId.equals(policy.tenantId())) {
            throw new RoundingPolicyAccessDeniedException("rounding policy tenant does not match request tenant");
        }
        return policy;
    }

    private static RoundingRule validateRule(RoundingRule rule) {
        if (rule == null) {
            throw new RoundingPolicyValidationException("rounding rule is required");
        }
        requireText(rule.outputContext(), "output_context is required");
        if (rule.unit() == null) {
            throw new RoundingPolicyValidationException("unit is required");
        }
        if (rule.roundingMode() == null) {
            throw new RoundingPolicyValidationException("rounding_mode is required");
        }
        Set<Integer> supportedScales = SUPPORTED_SCALES.get(rule.unit());
        if (supportedScales == null || !supportedScales.contains(rule.scale())) {
            throw new RoundingPolicyValidationException("ROUNDING_SCALE_UNSUPPORTED");
        }
        if (rule.increment() == null || rule.increment().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RoundingPolicyValidationException("ROUNDING_INCREMENT_INVALID");
        }
        if (rule.precedence() < 0) {
            throw new RoundingPolicyValidationException("precedence must be zero or greater");
        }
        return rule;
    }

    private static List<RoundingRule> matchingRules(RoundingPolicyVersion policy, String outputContext) {
        return policy.rules().stream()
                .filter(rule -> rule.outputContext().equals(outputContext))
                .sorted(Comparator.comparing(RoundingRule::precedence))
                .toList();
    }

    private static boolean isEffective(RoundingPolicyVersion policy, LocalDate asOf) {
        return !asOf.isBefore(policy.effectiveFrom()) && (policy.effectiveTo() == null || asOf.isBefore(policy.effectiveTo()));
    }

    private static boolean windowsOverlap(LocalDate leftStart, LocalDate leftEnd, LocalDate rightStart, LocalDate rightEnd) {
        LocalDate normalizedLeftEnd = leftEnd == null ? LocalDate.MAX : leftEnd;
        LocalDate normalizedRightEnd = rightEnd == null ? LocalDate.MAX : rightEnd;
        return leftStart.isBefore(normalizedRightEnd) && rightStart.isBefore(normalizedLeftEnd);
    }

    private static void requirePermission(RoundingHeaders headers, String permission) {
        if (headers == null) {
            throw new RoundingPolicyAccessDeniedException("headers are required");
        }
        requireText(headers.actorId(), "actor_id is required");
        requireText(headers.correlationId(), "correlation_id is required");
        if (!headers.permissions().contains(permission)) {
            throw new RoundingPolicyAccessDeniedException(permission + " permission is required");
        }
    }

    private static void requireTenant(String tenantId) {
        requireText(tenantId, "tenant_id is required");
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new RoundingPolicyValidationException(message);
        }
    }

    private static void requireDate(LocalDate value, String message) {
        if (value == null) {
            throw new RoundingPolicyValidationException(message);
        }
    }

    public enum RoundingPolicyStatus {
        DRAFT,
        PUBLISHED,
        SUSPENDED
    }

    public enum RoundingUnit {
        NOTE_RATE,
        PRICE,
        POINTS,
        BPS,
        MONEY,
        PERCENT
    }

    public record RoundingHeaders(Set<String> permissions, String actorId, String correlationId, String idempotencyKey) {
        public RoundingHeaders {
            permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
        }
    }

    public record CreateRoundingPolicyRequest(
            String tenantId,
            String scope,
            String productCode,
            String investorCode,
            String channelCode,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            int schemaVersion,
            List<RoundingRule> rules,
            List<RoundingSampleFixture> fixtures) {
        public CreateRoundingPolicyRequest {
            rules = rules == null ? List.of() : List.copyOf(rules);
            fixtures = fixtures == null ? List.of() : List.copyOf(fixtures);
        }
    }

    public record ResolveRoundingPolicyRequest(
            String scope,
            String productCode,
            String investorCode,
            String channelCode,
            String outputContext,
            LocalDate asOf,
            BigDecimal inputValue) {
    }

    public record RoundingPolicyVersion(
            String id,
            String tenantId,
            int versionNumber,
            RoundingPolicyStatus status,
            String scope,
            String productCode,
            String investorCode,
            String channelCode,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            int schemaVersion,
            String createdBy,
            String approvedBy,
            Instant approvedAt,
            String auditReference,
            String correlationId,
            Instant updatedAt,
            List<RoundingRule> rules,
            List<RoundingSampleFixture> fixtures,
            boolean validationPassed) {
        public RoundingPolicyVersion {
            rules = rules == null ? List.of() : List.copyOf(rules);
            fixtures = fixtures == null ? List.of() : List.copyOf(fixtures);
        }

        public RoundingPolicyVersion withValidationPassed(boolean validationPassed) {
            return new RoundingPolicyVersion(id, tenantId, versionNumber, status, scope, productCode, investorCode,
                    channelCode, effectiveFrom, effectiveTo, schemaVersion, createdBy, approvedBy, approvedAt,
                    auditReference, correlationId, Instant.now(), rules, fixtures, validationPassed);
        }

        public RoundingPolicyVersion withPublished(String approvedBy, Instant approvedAt) {
            return new RoundingPolicyVersion(id, tenantId, versionNumber, RoundingPolicyStatus.PUBLISHED, scope,
                    productCode, investorCode, channelCode, effectiveFrom, effectiveTo, schemaVersion, createdBy,
                    approvedBy, approvedAt, auditReference, correlationId, Instant.now(), rules, fixtures, true);
        }
    }

    public record RoundingRule(
            String ruleId,
            String outputContext,
            RoundingUnit unit,
            int scale,
            RoundingMode roundingMode,
            BigDecimal increment,
            int precedence,
            String reasonCode) {
        public RoundedValue round(BigDecimal inputValue) {
            if (inputValue == null) {
                throw new RoundingPolicyValidationException("input_value is required");
            }
            BigDecimal incrementCount = inputValue.divide(increment, 0, roundingMode);
            BigDecimal outputValue = incrementCount.multiply(increment).setScale(scale, roundingMode);
            return new RoundedValue(ruleId, inputValue, outputValue, roundingMode, scale, increment, reasonCode);
        }
    }

    public record RoundedValue(
            String ruleId,
            BigDecimal inputValue,
            BigDecimal outputValue,
            RoundingMode roundingMode,
            int scale,
            BigDecimal increment,
            String reasonCode) {
    }

    public record RoundingSampleFixture(
            String fixtureName,
            String outputContext,
            BigDecimal inputValue,
            BigDecimal expectedValue) {
    }

    public record RoundingPolicyValidationResult(
            String policyVersionId,
            boolean valid,
            List<RoundingPolicyValidationMessage> messages) {
        public RoundingPolicyValidationResult {
            messages = messages == null ? List.of() : List.copyOf(messages);
        }
    }

    public record RoundingPolicyValidationMessage(String code, String message) {
    }

    public record RoundingPolicyResolution(
            String policyVersionId,
            int versionNumber,
            String ruleId,
            String outputContext,
            RoundingUnit unit,
            int scale,
            RoundingMode roundingMode,
            BigDecimal increment,
            RoundedValue roundedValue) {
    }

    public interface RoundingPolicyRepository {
        void save(RoundingPolicyVersion policy);

        Optional<RoundingPolicyVersion> findById(String policyVersionId);

        List<RoundingPolicyVersion> findPublishedForScope(
                String tenantId,
                String scope,
                String productCode,
                String investorCode,
                String channelCode);
    }

    public static final class JdbcRoundingPolicyRepository implements RoundingPolicyRepository {
        private final DataSource dataSource;

        public JdbcRoundingPolicyRepository(DataSource dataSource) {
            this.dataSource = Objects.requireNonNull(dataSource, "dataSource is required");
        }

        @Override
        public void save(RoundingPolicyVersion policy) {
            Objects.requireNonNull(policy, "policy is required");
            try (Connection connection = dataSource.getConnection()) {
                boolean previousAutoCommit = connection.getAutoCommit();
                connection.setAutoCommit(false);
                try {
                    upsertPolicy(connection, policy);
                    replaceRules(connection, policy);
                    replaceFixtures(connection, policy);
                    connection.commit();
                } catch (SQLException | RuntimeException ex) {
                    connection.rollback();
                    throw ex;
                } finally {
                    connection.setAutoCommit(previousAutoCommit);
                }
            } catch (SQLException ex) {
                throw persistenceFailure("save rounding policy", ex);
            }
        }

        @Override
        public Optional<RoundingPolicyVersion> findById(String policyVersionId) {
            requireText(policyVersionId, "policy_version_id is required");
            try (Connection connection = dataSource.getConnection()) {
                return findPolicy(connection, "select * from rounding_policy_version where policy_version_id = ?",
                        statement -> statement.setString(1, policyVersionId)).stream().findFirst();
            } catch (SQLException ex) {
                throw persistenceFailure("find rounding policy", ex);
            }
        }

        @Override
        public List<RoundingPolicyVersion> findPublishedForScope(
                String tenantId,
                String scope,
                String productCode,
                String investorCode,
                String channelCode) {
            requireTenant(tenantId);
            requireText(scope, "scope is required");
            try (Connection connection = dataSource.getConnection()) {
                return findPolicy(connection,
                        "select * from rounding_policy_version where tenant_id = ? and status = ? and scope = ?",
                        statement -> {
                            statement.setString(1, tenantId);
                            statement.setString(2, RoundingPolicyStatus.PUBLISHED.name());
                            statement.setString(3, scope);
                        }).stream()
                        .filter(policy -> Objects.equals(productCode, policy.productCode()))
                        .filter(policy -> Objects.equals(investorCode, policy.investorCode()))
                        .filter(policy -> Objects.equals(channelCode, policy.channelCode()))
                        .toList();
            } catch (SQLException ex) {
                throw persistenceFailure("find published rounding policies", ex);
            }
        }

        private static void upsertPolicy(Connection connection, RoundingPolicyVersion policy) throws SQLException {
            try (PreparedStatement statement = connection.prepareStatement("""
                    insert into rounding_policy_version (
                        tenant_id, policy_version_id, version_number, status, scope, product_code, investor_code,
                        channel_code, effective_from, effective_to, schema_version, created_by, approved_by,
                        approved_at, audit_reference, correlation_id, validation_passed, updated_at
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    on conflict (policy_version_id) do update set
                        tenant_id = excluded.tenant_id,
                        version_number = excluded.version_number,
                        status = excluded.status,
                        scope = excluded.scope,
                        product_code = excluded.product_code,
                        investor_code = excluded.investor_code,
                        channel_code = excluded.channel_code,
                        effective_from = excluded.effective_from,
                        effective_to = excluded.effective_to,
                        schema_version = excluded.schema_version,
                        created_by = excluded.created_by,
                        approved_by = excluded.approved_by,
                        approved_at = excluded.approved_at,
                        audit_reference = excluded.audit_reference,
                        correlation_id = excluded.correlation_id,
                        validation_passed = excluded.validation_passed,
                        updated_at = excluded.updated_at
                    """)) {
                statement.setString(1, policy.tenantId());
                statement.setString(2, policy.id());
                statement.setInt(3, policy.versionNumber());
                statement.setString(4, policy.status().name());
                statement.setString(5, policy.scope());
                statement.setString(6, policy.productCode());
                statement.setString(7, policy.investorCode());
                statement.setString(8, policy.channelCode());
                statement.setDate(9, Date.valueOf(policy.effectiveFrom()));
                statement.setDate(10, policy.effectiveTo() == null ? null : Date.valueOf(policy.effectiveTo()));
                statement.setInt(11, policy.schemaVersion());
                statement.setString(12, policy.createdBy());
                statement.setString(13, policy.approvedBy());
                statement.setTimestamp(14, policy.approvedAt() == null ? null : Timestamp.from(policy.approvedAt()));
                statement.setString(15, policy.auditReference());
                statement.setString(16, policy.correlationId());
                statement.setBoolean(17, policy.validationPassed());
                statement.setTimestamp(18, Timestamp.from(policy.updatedAt()));
                statement.executeUpdate();
            }
        }

        private static void replaceRules(Connection connection, RoundingPolicyVersion policy) throws SQLException {
            try (PreparedStatement delete = connection.prepareStatement("delete from rounding_rule where policy_version_id = ?")) {
                delete.setString(1, policy.id());
                delete.executeUpdate();
            }
            try (PreparedStatement insert = connection.prepareStatement("""
                    insert into rounding_rule (
                        tenant_id, rule_id, policy_version_id, output_context, unit, scale, rounding_mode,
                        increment, precedence, reason_code
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                for (RoundingRule rule : policy.rules()) {
                    requireText(rule.ruleId(), "rule_id is required for durable rounding policy persistence");
                    insert.setString(1, policy.tenantId());
                    insert.setString(2, rule.ruleId());
                    insert.setString(3, policy.id());
                    insert.setString(4, rule.outputContext());
                    insert.setString(5, rule.unit().name());
                    insert.setInt(6, rule.scale());
                    insert.setString(7, rule.roundingMode().name());
                    insert.setBigDecimal(8, rule.increment());
                    insert.setInt(9, rule.precedence());
                    insert.setString(10, rule.reasonCode());
                    insert.addBatch();
                }
                insert.executeBatch();
            }
        }

        private static void replaceFixtures(Connection connection, RoundingPolicyVersion policy) throws SQLException {
            try (PreparedStatement delete = connection.prepareStatement(
                    "delete from rounding_sample_fixture where policy_version_id = ?")) {
                delete.setString(1, policy.id());
                delete.executeUpdate();
            }
            try (PreparedStatement insert = connection.prepareStatement("""
                    insert into rounding_sample_fixture (
                        tenant_id, fixture_id, policy_version_id, fixture_name, output_context, input_value, expected_value
                    ) values (?, ?, ?, ?, ?, ?, ?)
                    """)) {
                for (RoundingSampleFixture fixture : policy.fixtures()) {
                    insert.setString(1, policy.tenantId());
                    insert.setString(2, fixtureId(policy.id(), fixture));
                    insert.setString(3, policy.id());
                    insert.setString(4, fixture.fixtureName());
                    insert.setString(5, fixture.outputContext());
                    insert.setBigDecimal(6, fixture.inputValue());
                    insert.setBigDecimal(7, fixture.expectedValue());
                    insert.addBatch();
                }
                insert.executeBatch();
            }
        }

        private List<RoundingPolicyVersion> findPolicy(Connection connection, String sql, StatementBinder binder)
                throws SQLException {
            List<RoundingPolicyVersion> policies = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                binder.bind(statement);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        String id = resultSet.getString("policy_version_id");
                        policies.add(new RoundingPolicyVersion(
                                id,
                                resultSet.getString("tenant_id"),
                                resultSet.getInt("version_number"),
                                RoundingPolicyStatus.valueOf(resultSet.getString("status")),
                                resultSet.getString("scope"),
                                resultSet.getString("product_code"),
                                resultSet.getString("investor_code"),
                                resultSet.getString("channel_code"),
                                resultSet.getDate("effective_from").toLocalDate(),
                                resultSet.getDate("effective_to") == null ? null
                                        : resultSet.getDate("effective_to").toLocalDate(),
                                resultSet.getInt("schema_version"),
                                resultSet.getString("created_by"),
                                resultSet.getString("approved_by"),
                                resultSet.getTimestamp("approved_at") == null ? null
                                        : resultSet.getTimestamp("approved_at").toInstant(),
                                resultSet.getString("audit_reference"),
                                resultSet.getString("correlation_id"),
                                resultSet.getTimestamp("updated_at").toInstant(),
                                findRules(connection, id),
                                findFixtures(connection, id),
                                resultSet.getBoolean("validation_passed")));
                    }
                }
            }
            return List.copyOf(policies);
        }

        private static List<RoundingRule> findRules(Connection connection, String policyVersionId) throws SQLException {
            try (PreparedStatement statement = connection.prepareStatement("""
                    select rule_id, output_context, unit, scale, rounding_mode, increment, precedence, reason_code
                    from rounding_rule
                    where policy_version_id = ?
                    order by precedence, rule_id
                    """)) {
                statement.setString(1, policyVersionId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    List<RoundingRule> rules = new ArrayList<>();
                    while (resultSet.next()) {
                        rules.add(new RoundingRule(
                                resultSet.getString("rule_id"),
                                resultSet.getString("output_context"),
                                RoundingUnit.valueOf(resultSet.getString("unit")),
                                resultSet.getInt("scale"),
                                RoundingMode.valueOf(resultSet.getString("rounding_mode")),
                                resultSet.getBigDecimal("increment"),
                                resultSet.getInt("precedence"),
                                resultSet.getString("reason_code")));
                    }
                    return List.copyOf(rules);
                }
            }
        }

        private static List<RoundingSampleFixture> findFixtures(Connection connection, String policyVersionId)
                throws SQLException {
            try (PreparedStatement statement = connection.prepareStatement("""
                    select fixture_name, output_context, input_value, expected_value
                    from rounding_sample_fixture
                    where policy_version_id = ?
                    order by fixture_name
                    """)) {
                statement.setString(1, policyVersionId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    List<RoundingSampleFixture> fixtures = new ArrayList<>();
                    while (resultSet.next()) {
                        fixtures.add(new RoundingSampleFixture(
                                resultSet.getString("fixture_name"),
                                resultSet.getString("output_context"),
                                resultSet.getBigDecimal("input_value"),
                                resultSet.getBigDecimal("expected_value")));
                    }
                    return List.copyOf(fixtures);
                }
            }
        }

        private static String fixtureId(String policyId, RoundingSampleFixture fixture) {
            String source = policyId + ":" + fixture.fixtureName() + ":" + fixture.outputContext();
            return "rounding-fixture-" + UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8));
        }

        private static RoundingPolicyValidationException persistenceFailure(String operation, SQLException ex) {
            return new RoundingPolicyValidationException("rounding policy JDBC persistence failed during " + operation
                    + "; PostgreSQL datasource/schema must be configured and migrated");
        }

        @FunctionalInterface
        private interface StatementBinder {
            void bind(PreparedStatement statement) throws SQLException;
        }
    }

    public static class RoundingPolicyValidationException extends RuntimeException {
        public RoundingPolicyValidationException(String message) {
            super(message);
        }
    }

    public static class RoundingPolicyAccessDeniedException extends RuntimeException {
        public RoundingPolicyAccessDeniedException(String message) {
            super(message);
        }
    }

    public static class RoundingPolicyConflictException extends RuntimeException {
        public RoundingPolicyConflictException(String message) {
            super(message);
        }
    }

    public static class RoundingPolicyNotFoundException extends RuntimeException {
        public RoundingPolicyNotFoundException(String message) {
            super(message);
        }
    }

    public static class RoundingPolicyNotSatisfiedException extends RuntimeException {
        public RoundingPolicyNotSatisfiedException(String message) {
            super(message);
        }
    }
}
