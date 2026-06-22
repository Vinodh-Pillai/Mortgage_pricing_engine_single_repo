package com.wcpe.tenantcontext;

import static org.assertj.core.api.Assertions.*;

import com.wcpe.tenantcontext.TenantFieldConfigurationStoreService.FieldOrigin;
import com.wcpe.tenantcontext.TenantFieldConfigurationStoreService.TenantFieldConfigException;
import com.wcpe.tenantcontext.TenantFieldConfigurationStoreService.TenantFieldConfiguration;
import com.wcpe.tenantcontext.TenantPipelineEligibilityService.FieldReference;
import com.wcpe.tenantcontext.TenantPipelineEligibilityService.TenantInvestorOption;
import com.wcpe.tenantcontext.TenantPipelineEligibilityService.TenantPipelineAccessAuditRecord;
import com.wcpe.tenantcontext.TenantPipelineEligibilityService.TenantPipelineConfiguration;
import com.wcpe.tenantcontext.TenantPipelineEligibilityService.TenantProductOption;
import com.wcpe.tenantcontext.TenantPipelineEligibilityService.TenantUserSettings;
import com.wcpe.tenantcontext.audit.AuditRecord;
import com.wcpe.tenantcontext.audit.JdbcAuditLogStore;
import com.wcpe.tenantcontext.consumer.ConsumerInboxRecord;
import com.wcpe.tenantcontext.consumer.JdbcConsumerInboxStore;
import com.wcpe.tenantcontext.consumer.ProcessingStatus;
import com.wcpe.tenantcontext.event.DataClassification;
import com.wcpe.tenantcontext.outbox.JdbcOutboxStore;
import com.wcpe.tenantcontext.outbox.OutboxEvent;
import com.wcpe.tenantcontext.outbox.OutboxStatus;
import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;

class JdbcPersistenceStoreTest {
    private static final Instant NOW = Instant.parse("2026-06-18T16:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final UUID AUDIT_ID = UUID.fromString("018f7c7e-9f3b-7cc2-a6db-2e3df7d1c105");
    private static final UUID EVENT_ID = UUID.fromString("018f7c7e-9f3b-7cc2-a6db-2e3df7d1c106");
    private static final UUID INBOX_ID = UUID.fromString("018f7c7e-9f3b-7cc2-a6db-2e3df7d1c107");

    @Test
    void jdbcAuditLogStoreAppendsAndMapsRowsThroughJdbcTemplate() {
        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate();
        JdbcAuditLogStore store = new JdbcAuditLogStore(jdbc);
        AuditRecord record = auditRecord();
        jdbc.whenQueryContains("FROM tenant.audit_log_record", auditRow(record));

        assertThat(store.append(record)).isEqualTo(record);
        Optional<AuditRecord> latest = store.latestForTenant("tenant-alpha");

        assertThat(jdbc.updates()).anySatisfy(call -> assertThat(call.sql()).contains("INSERT INTO tenant.audit_log_record"));
        assertThat(latest).hasValueSatisfying(mapped -> {
            assertThat(mapped.auditId()).isEqualTo(AUDIT_ID);
            assertThat(mapped.tenantId()).isEqualTo("tenant-alpha");
            assertThat(mapped.dataClassification()).isEqualTo(DataClassification.CONFIDENTIAL);
        });
    }

    @Test
    void jdbcOutboxStoreSavesAndReadsDueRowsThroughJdbcTemplate() {
        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate();
        JdbcOutboxStore store = new JdbcOutboxStore(jdbc);
        OutboxEvent event = outboxEvent();
        jdbc.whenQueryContains("FROM tenant.outbox_event", outboxRow(event));

        assertThat(store.save(event)).isEqualTo(event);
        assertThat(store.dueForPublish("tenant-alpha"))
            .singleElement()
            .satisfies(mapped -> {
                assertThat(mapped.eventId()).isEqualTo(EVENT_ID);
                assertThat(mapped.status()).isEqualTo(OutboxStatus.PENDING);
                assertThat(mapped.payloadHash()).isEqualTo("sha256:payload");
            });

        assertThat(jdbc.updates()).anySatisfy(call -> assertThat(call.sql()).contains("INSERT INTO tenant.outbox_event"));
    }

    @Test
    void jdbcConsumerInboxStoreSavesAndFindsRowsThroughJdbcTemplate() {
        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate();
        JdbcConsumerInboxStore store = new JdbcConsumerInboxStore(jdbc);
        ConsumerInboxRecord record = inboxRecord();
        jdbc.whenQueryContains("FROM tenant.consumer_inbox_record", inboxRow(record));

        assertThat(store.save(record)).isEqualTo(record);
        assertThat(store.find("tenant-alpha", "pricing-cache-consumer", EVENT_ID))
            .hasValueSatisfying(mapped -> {
                assertThat(mapped.inboxId()).isEqualTo(INBOX_ID);
                assertThat(mapped.status()).isEqualTo(ProcessingStatus.PROCESSED);
                assertThat(mapped.resultHash()).isEqualTo("sha256:result");
            });

        assertThat(jdbc.updates()).anySatisfy(call -> assertThat(call.sql()).contains("INSERT INTO tenant.consumer_inbox_record"));
    }

    @Test
    void tenantFieldConfigurationJdbcPathPersistsReadsAndKeepsDraftsFailClosed() {
        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate();
        TenantFieldConfigurationStoreService service = new TenantFieldConfigurationStoreService(jdbc, CLOCK);
        TenantFieldConfiguration field = nativeField("tenant-alpha", "CLIENT_SETTINGS", "tenant-custom-1", "Alpha custom field");
        jdbc.whenQueryContains("FROM tenant.tenant_field_configuration", fieldRow(field));

        assertThat(service.save(field).fieldId()).isEqualTo("tenant-custom-1");
        assertThat(service.activeField("tenant-alpha", "CLIENT_SETTINGS", "tenant-custom-1"))
            .hasValueSatisfying(mapped -> {
                assertThat(mapped.tenantId()).isEqualTo("tenant-alpha");
                assertThat(mapped.surface()).isEqualTo("CLIENT_SETTINGS");
                assertThat(mapped.nameAlias()).isEqualTo("Alpha custom field");
            });
        assertThatThrownBy(() -> service.saveDraft("tenant-alpha", "CLIENT_SETTINGS", List.of(field), "admin-alpha"))
            .isInstanceOf(TenantFieldConfigException.class)
            .extracting(Throwable::getMessage)
            .isEqualTo("TENANT_FIELD_PERSISTENCE_CONTRACT_MISSING");

        assertThat(jdbc.updates()).anySatisfy(call -> assertThat(call.sql()).contains("INSERT INTO tenant.tenant_field_configuration"));
    }

    @Test
    void tenantPipelineEligibilityJdbcPathWritesConfigurationAndMapsAuditRows() {
        TenantFieldConfigurationStoreService fieldStore = new TenantFieldConfigurationStoreService(CLOCK, new TestOnlyTenantFieldConfigurationStore());
        fieldStore.save(systemField("tenant-alpha", "PRODUCT_SPEC", "loan-type"));
        fieldStore.save(systemField("tenant-alpha", "PRODUCT_SPEC", "investor-code"));
        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate();
        jdbc.whenQueryContains("FROM tenant.tenant_pipeline_user_assignment", List.of());
        jdbc.whenQueryContains("FROM tenant.tenant_pipeline_access_audit", pipelineAuditRow());
        TenantPipelineEligibilityService service = new TenantPipelineEligibilityService(fieldStore, jdbc);

        service.configureTenant(new TenantPipelineConfiguration(
            "tenant-alpha",
            List.of(new TenantProductOption("alpha-product", "Alpha product", List.of(new FieldReference("PRODUCT_SPEC", "loan-type")), true)),
            List.of(new TenantInvestorOption("alpha-investor", "Alpha investor", List.of(new FieldReference("PRODUCT_SPEC", "investor-code")), true)),
            Map.of("pipeline.defaultView", "summary"),
            List.of(new TenantUserSettings("user-alpha", "tenant-alpha", Map.of("pipeline.columns", "compact")))
        ));
        List<TenantPipelineAccessAuditRecord> auditRows = service.accessAuditRecordsForTenant("tenant-alpha");

        assertThat(jdbc.updates()).anySatisfy(call -> assertThat(call.sql()).contains("DELETE FROM tenant.tenant_pipeline_product"));
        assertThat(jdbc.updates()).anySatisfy(call -> assertThat(call.sql()).contains("INSERT INTO tenant.tenant_pipeline_product"));
        assertThat(jdbc.updates()).anySatisfy(call -> assertThat(call.sql()).contains("INSERT INTO tenant.tenant_pipeline_investor"));
        assertThat(jdbc.updates()).anySatisfy(call -> assertThat(call.sql()).contains("INSERT INTO tenant.tenant_pipeline_user_assignment"));
        assertThat(jdbc.updates()).anySatisfy(call -> assertThat(call.sql()).contains("INSERT INTO tenant.tenant_pipeline_user_setting"));
        assertThat(auditRows)
            .singleElement()
            .satisfies(row -> {
                assertThat(row.tenantId()).isEqualTo("tenant-alpha");
                assertThat(row.code()).isEqualTo("TENANT_PIPELINE_METADATA_EVALUATED");
            });
    }

    private static AuditRecord auditRecord() {
        return new AuditRecord(AUDIT_ID, "tenant-alpha", NOW, "actor-1", "USER", "PRICE_CONFIG_CHANGED", "Scenario",
            "scenario-1", "v1", "SUCCESS", "correlation-1", "causation-1", "event-1", "idem-audit-1",
            "before-ref-1", "after-ref-1", "{\"status\":\"READY\"}", DataClassification.CONFIDENTIAL,
            "sha256:audit", "");
    }

    private static OutboxEvent outboxEvent() {
        return new OutboxEvent("tenant-alpha", EVENT_ID, "Scenario", "scenario-1", "tenant-context.outbox.v1",
            "tenant-alpha:" + EVENT_ID, "tenant-context.outbox.v1", "tenant_context.outbox_recorded.v1", 1,
            "{\"event\":1}", "sha256:payload", OutboxStatus.PENDING, 0, null, NOW, NOW, null,
            "actor-1", "correlation-1", "causation-1", "idem-outbox-1", List.of(), "", "", "");
    }

    private static ConsumerInboxRecord inboxRecord() {
        return new ConsumerInboxRecord(INBOX_ID, "tenant-alpha", "pricing-cache-consumer", EVENT_ID, "rate.changed",
            "rate.changed.v1", 1, "sha256:payload", "correlation-1", "causation-1", ProcessingStatus.PROCESSED,
            1, NOW, NOW, NOW, "sha256:result", "", "");
    }

    private static TenantFieldConfiguration nativeField(String tenantId, String surface, String fieldId, String alias) {
        return new TenantFieldConfiguration(null, tenantId, surface, fieldId, FieldOrigin.NATIVE, "", alias, "", true, false, NOW, null);
    }

    private static TenantFieldConfiguration systemField(String tenantId, String surface, String fieldId) {
        return new TenantFieldConfiguration(null, tenantId, surface, fieldId, FieldOrigin.INHERITED_SYSTEM,
            "system-field:" + fieldId, fieldId, "", true, false, NOW, null);
    }

    private static List<Map<String, Object>> auditRow(AuditRecord record) {
        return List.of(row(
            "audit_id", record.auditId(),
            "tenant_id", record.tenantId(),
            "occurred_at", ts(record.occurredAt()),
            "actor_id", record.actorId(),
            "actor_type", record.actorType(),
            "action", record.action(),
            "entity_type", record.entityType(),
            "entity_id", record.entityId(),
            "entity_version", record.entityVersion(),
            "outcome", record.outcome(),
            "correlation_id", record.correlationId(),
            "causation_id", record.causationId(),
            "event_id", record.eventId(),
            "idempotency_key", record.idempotencyKey(),
            "before_ref", record.beforeRef(),
            "after_ref", record.afterRef(),
            "change_summary_json", record.changeSummaryJson(),
            "data_classification", record.dataClassification().name(),
            "record_hash", record.recordHash(),
            "previous_hash", record.previousHash()
        ));
    }

    private static List<Map<String, Object>> outboxRow(OutboxEvent event) {
        return List.of(row(
            "tenant_id", event.tenantId(),
            "event_id", event.eventId(),
            "aggregate_type", event.aggregateType(),
            "aggregate_id", event.aggregateId(),
            "topic", event.topic(),
            "partition_key", event.partitionKey(),
            "schema_ref", event.schemaRef(),
            "event_name", event.eventName(),
            "event_version", event.eventVersion(),
            "envelope_json", event.envelopeJson(),
            "payload_hash", event.payloadHash(),
            "status", event.status().name(),
            "attempt_count", event.attemptCount(),
            "next_attempt_at", ts(event.nextAttemptAt()),
            "created_at", ts(event.createdAt()),
            "updated_at", ts(event.updatedAt()),
            "published_at", ts(event.publishedAt()),
            "actor_id", event.actorId(),
            "correlation_id", event.correlationId(),
            "causation_id", event.causationId(),
            "idempotency_key", event.idempotencyKey(),
            "error_code", event.lastErrorCode(),
            "error_message", event.lastErrorMessage(),
            "publisher_ref", event.quarantineReason()
        ));
    }

    private static List<Map<String, Object>> inboxRow(ConsumerInboxRecord record) {
        return List.of(row(
            "inbox_id", record.inboxId(),
            "tenant_id", record.tenantId(),
            "consumer_name", record.consumerName(),
            "event_id", record.eventId(),
            "event_name", record.eventName(),
            "schema_ref", record.schemaRef(),
            "schema_version", record.schemaVersion(),
            "payload_hash", record.payloadHash(),
            "correlation_id", record.correlationId(),
            "causation_id", record.causationId(),
            "status", record.status().name(),
            "attempt_count", record.attemptCount(),
            "first_seen_at", ts(record.firstSeenAt()),
            "last_attempt_at", ts(record.lastAttemptAt()),
            "processed_at", ts(record.processedAt()),
            "result_payload_hash", record.resultHash(),
            "last_error_code", record.lastErrorCode(),
            "last_error_message", record.lastErrorMessage()
        ));
    }

    private static List<Map<String, Object>> fieldRow(TenantFieldConfiguration field) {
        return List.of(row(
            "configuration_id", "tenant-alpha:CLIENT_SETTINGS:tenant-custom-1",
            "tenant_id", field.tenantId(),
            "surface", "CLIENT_SETTINGS",
            "field_id", field.fieldId(),
            "origin", field.origin().name(),
            "system_field_ref", field.systemFieldRef(),
            "name_alias", field.nameAlias(),
            "description_alias", field.descriptionAlias(),
            "enabled", field.enabled(),
            "omitted", field.omitted(),
            "updated_at", ts(field.updatedAt()),
            "audit_ref", "tenant-field-config:tenant-alpha:CLIENT_SETTINGS:tenant-custom-1"
        ));
    }

    private static List<Map<String, Object>> pipelineAuditRow() {
        return List.of(row(
            "tenant_id", "tenant-alpha",
            "user_id", "user-alpha",
            "actor_id", "actor-tenant-alpha",
            "actor_type", "USER",
            "code", "TENANT_PIPELINE_METADATA_EVALUATED",
            "entity_type", "tenant",
            "entity_id", "tenant-alpha"
        ));
    }

    private static Map<String, Object> row(Object... entries) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            row.put((String) entries[index], entries[index + 1]);
        }
        return row;
    }

    private static Timestamp ts(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private record JdbcCall(String sql, List<Object> args) { }

    private static final class RecordingJdbcTemplate extends JdbcTemplate {
        private final List<JdbcCall> updates = new ArrayList<>();
        private final List<JdbcCall> queries = new ArrayList<>();
        private final List<QueryRule> queryRules = new ArrayList<>();

        void whenQueryContains(String token, List<Map<String, Object>> rows) {
            queryRules.add(new QueryRule(token, rows));
        }

        List<JdbcCall> updates() {
            return updates;
        }

        @Override
        public int update(String sql, Object... args) throws DataAccessException {
            updates.add(new JdbcCall(sql, Arrays.asList(args)));
            return 1;
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) throws DataAccessException {
            queries.add(new JdbcCall(sql, Arrays.asList(args)));
            List<T> mapped = new ArrayList<>();
            List<Map<String, Object>> rows = rowsFor(sql);
            for (int index = 0; index < rows.size(); index++) {
                try {
                    mapped.add(rowMapper.mapRow(resultSet(rows.get(index)), index));
                } catch (Exception error) {
                    throw new AssertionError("row mapper failed for SQL: " + sql, error);
                }
            }
            return mapped;
        }

        @Override
        public <T> T query(String sql, ResultSetExtractor<T> resultSetExtractor, Object... args) throws DataAccessException {
            queries.add(new JdbcCall(sql, Arrays.asList(args)));
            try {
                return resultSetExtractor.extractData(resultSet(rowsFor(sql)));
            } catch (Exception error) {
                throw new AssertionError("result set extractor failed for SQL: " + sql, error);
            }
        }

        private List<Map<String, Object>> rowsFor(String sql) {
            for (int index = 0; index < queryRules.size(); index++) {
                QueryRule rule = queryRules.get(index);
                if (sql.contains(rule.token())) {
                    queryRules.remove(index);
                    return rule.rows();
                }
            }
            return List.of();
        }
    }

    private record QueryRule(String token, List<Map<String, Object>> rows) { }

    private static ResultSet resultSet(Map<String, Object> row) {
        return (ResultSet) Proxy.newProxyInstance(ResultSet.class.getClassLoader(), new Class<?>[] { ResultSet.class },
            (proxy, method, args) -> valueFor(method.getName(), method.getReturnType(), row, args));
    }

    private static ResultSet resultSet(List<Map<String, Object>> rows) {
        final int[] index = { -1 };
        return (ResultSet) Proxy.newProxyInstance(ResultSet.class.getClassLoader(), new Class<?>[] { ResultSet.class },
            (proxy, method, args) -> {
                if (method.getName().equals("next")) {
                    index[0]++;
                    return index[0] < rows.size();
                }
                Map<String, Object> row = index[0] >= 0 && index[0] < rows.size() ? rows.get(index[0]) : Map.of();
                return valueFor(method.getName(), method.getReturnType(), row, args);
            });
    }

    private static Object valueFor(String methodName, Class<?> returnType, Map<String, Object> row, Object[] args) {
        if (methodName.equals("toString")) return "RecordingResultSet";
        if (methodName.equals("wasNull")) return false;
        Object value = args != null && args.length > 0 && args[0] instanceof String column ? row.get(column) : null;
        return switch (methodName) {
            case "getString" -> value == null ? null : value.toString();
            case "getObject" -> value;
            case "getTimestamp" -> value;
            case "getInt" -> value instanceof Number number ? number.intValue() : 0;
            case "getBoolean" -> value instanceof Boolean bool && bool;
            default -> defaultValue(returnType);
        };
    }

    private static Object defaultValue(Class<?> returnType) {
        if (returnType == boolean.class) return false;
        if (returnType == int.class) return 0;
        if (returnType == long.class) return 0L;
        if (returnType == double.class) return 0d;
        if (returnType == float.class) return 0f;
        return null;
    }
}
