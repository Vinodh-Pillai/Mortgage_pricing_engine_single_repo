package com.wcpe.scenario.domain;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class ScenarioRepository {
  private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};
  private static final TypeReference<List<ValidationIssue>> ISSUES = new TypeReference<>() {};
  private static final TypeReference<List<VersionManifest>> VERSIONS = new TypeReference<>() {};
  private static final TypeReference<List<EventRecord>> EVENTS = new TypeReference<>() {};

  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  ScenarioRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper.findAndRegisterModules();
  }

  Optional<Object> idempotent(String scope, String key, Object request) {
    if (key == null || key.isBlank()) return Optional.empty();
    UUID tenantId = tenant(scope);
    String scopedKey = scopedKey(scope, key);
    String requestHash = requestHash(scope, key, request);
    try {
      return Optional.of(jdbc.queryForObject("""
          select request_hash, response_type, response_json::text from scenario.scenario_idempotency_record
          where tenant_id = ? and idempotency_key = ?
          """, (rs, row) -> {
          if (!requestHash.equals(rs.getString("request_hash"))) throw new ScenarioException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", "Idempotency key was reused with a different request.", List.of());
          return readResponse(rs.getString("response_type"), rs.getString("response_json"));
        }, tenantId, scopedKey));
    } catch (EmptyResultDataAccessException ex) {
      return Optional.empty();
    }
  }

  void remember(String scope, String key, Object request, Object response) {
    if (key == null || key.isBlank()) return;
    UUID tenantId = tenant(scope);
    String scopedKey = scopedKey(scope, key);
    String json = write(response);
    String hash = requestHash(scope, key, request);
    int inserted = jdbc.update("""
        insert into scenario.scenario_idempotency_record (tenant_id, idempotency_key, request_hash, response_type, response_json)
        values (?, ?, ?, ?, ?::jsonb)
        on conflict (tenant_id, idempotency_key) do nothing
        """, tenantId, scopedKey, hash, response.getClass().getSimpleName(), json);
    if (inserted == 0) {
      String existing = jdbc.queryForObject("""
          select request_hash from scenario.scenario_idempotency_record where tenant_id = ? and idempotency_key = ?
          """, String.class, tenantId, scopedKey);
      if (!hash.equals(existing)) throw new ScenarioException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", "Idempotency key was reused with a different response.", List.of());
    }
  }

  @Transactional
  void save(Scenario scenario) {
    jdbc.update("""
        insert into scenario.scenario (tenant_id, scenario_id, lineage_id, version, status, quote_intent, channel, scenario_name,
          external_loan_id, source_system, raw_facts_json, normalized_facts_json, derived_fields_json, replay_hash, updated_at)
        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, now())
        on conflict (tenant_id, scenario_id) do update set
          version = excluded.version,
          status = excluded.status,
          raw_facts_json = excluded.raw_facts_json,
          normalized_facts_json = excluded.normalized_facts_json,
          derived_fields_json = excluded.derived_fields_json,
          replay_hash = excluded.replay_hash,
          updated_at = now()
        """, scenario.tenantId(), scenario.scenarioId(), scenario.lineageId(), scenario.version(), scenario.status().name(), scenario.quoteIntent(), scenario.channel(),
        scenario.scenarioName(), scenario.externalLoanId(), scenario.sourceSystem(), write(scenario.rawFacts()), write(scenario.normalizedFacts()), write(scenario.derivedFields()), scenario.replayHash());
    persistVersions(scenario);
    persistIssues(scenario);
  }

  Scenario get(UUID tenantId, UUID scenarioId) {
    try {
      return jdbc.queryForObject("""
          select s.*, coalesce(jsonb_agg(distinct jsonb_build_object('version', v.version, 'reason', v.reason, 'hash', v.replay_hash, 'createdAtUtc', v.created_at))
            filter (where v.version is not null), '[]'::jsonb)::text as versions_json,
            coalesce(jsonb_agg(distinct jsonb_build_object('code', i.code, 'fieldPath', i.field_path, 'severity', i.severity, 'message', i.message))
            filter (where i.code is not null), '[]'::jsonb)::text as issues_json
          from scenario.scenario s
          left join scenario.scenario_version v on v.tenant_id = s.tenant_id and v.scenario_id = s.scenario_id
          left join scenario.scenario_validation_issue i on i.tenant_id = s.tenant_id and i.scenario_id = s.scenario_id and i.version = s.version
          where s.tenant_id = ? and s.scenario_id = ?
          group by s.tenant_id, s.scenario_id
          """, (rs, row) -> Scenario.rehydrate(
          (UUID) rs.getObject("tenant_id"),
          (UUID) rs.getObject("scenario_id"),
          (UUID) rs.getObject("lineage_id"),
          rs.getInt("version"),
          ScenarioStatus.valueOf(rs.getString("status")),
          rs.getString("quote_intent"),
          rs.getString("channel"),
          rs.getString("scenario_name"),
          rs.getString("external_loan_id"),
          rs.getString("source_system"),
          read(rs.getString("raw_facts_json"), MAP),
          read(rs.getString("normalized_facts_json"), MAP),
          read(rs.getString("derived_fields_json"), MAP),
          read(rs.getString("issues_json"), ISSUES),
          read(rs.getString("versions_json"), VERSIONS),
          rs.getString("replay_hash")), tenantId, scenarioId);
    } catch (EmptyResultDataAccessException ex) {
      throw new ScenarioException(HttpStatus.NOT_FOUND, "SCENARIO_NOT_FOUND", "Scenario was not found for this tenant.", List.of());
    }
  }

  void event(EventRecord event) {
    jdbc.update("""
        insert into scenario.scenario_outbox_event (tenant_id, event_id, scenario_id, event_type, event_version, correlation_id, payload_json, occurred_at)
        values (?, ?, ?, ?, ?, ?, ?::jsonb, ?)
        on conflict (event_id) do nothing
        """, event.tenantId(), event.eventId(), event.scenarioId(), event.eventType(), event.eventVersion(), event.correlationId(), write(event.payload()), Timestamp.from(event.occurredAt()));
  }

  void audit(AuditRecord audit) {
    jdbc.update("""
        insert into scenario.scenario_audit_record (tenant_id, audit_package_id, scenario_id, action, correlation_id, replay_hash, occurred_at)
        values (?, ?, ?, ?, ?, ?, ?)
        on conflict (audit_package_id) do nothing
        """, audit.tenantId(), audit.auditPackageId(), audit.scenarioId(), audit.action(), audit.correlationId(), audit.replayHash(), Timestamp.from(audit.occurredAt()));
  }

  void persistBorrowers(UUID tenantId, UUID scenarioId, int version, List<BorrowerCredit> borrowers) {
    UUID vId = UUID.nameUUIDFromBytes((tenantId + ":" + scenarioId + ":" + version).getBytes());
    jdbc.update("delete from scenario.scenario_credit_attribute where tenant_id = ? and scenario_borrower_id in (select scenario_borrower_id from scenario.scenario_borrower where tenant_id = ? and scenario_version_id = ?)", tenantId, tenantId, vId);
    jdbc.update("delete from scenario.scenario_borrower where tenant_id = ? and scenario_version_id = ?", tenantId, vId);
    jdbc.update("delete from scenario.scenario_representative_credit where tenant_id = ? and scenario_version_id = ?", tenantId, vId);
    for (BorrowerCredit b : borrowers) {
      UUID borrowerId = UUID.randomUUID();
      jdbc.update("""
          insert into scenario.scenario_borrower (tenant_id, scenario_borrower_id, scenario_id, scenario_version_id, borrower_external_id, borrower_role, occupies_property, created_at_utc)
          values (?, ?, ?, ?, ?, ?, ?, now())
          """, tenantId, borrowerId, scenarioId, vId, b.borrowerExternalId(), b.borrowerRole(), b.occupiesProperty());
      String creditStatus = b.creditStatus() != null ? b.creditStatus() : "MISSING";
      String qualityStatus = computeQualityStatus(b);
      UUID attrId = UUID.randomUUID();
      jdbc.update("""
          insert into scenario.scenario_credit_attribute (tenant_id, credit_attribute_id, scenario_borrower_id, credit_status, credit_score, credit_score_source, credit_score_date, quality_status)
          values (?, ?, ?, ?, ?, ?, ?, ?)
          """, tenantId, attrId, borrowerId, creditStatus, b.creditScore(), b.creditScoreSource(), b.creditScoreDate(), qualityStatus);
    }
  }

  void persistRepresentativeCredit(UUID tenantId, UUID scenarioId, int version, RepresentativeCreditScorePolicy.RepresentativeCreditResult result) {
    UUID vId = UUID.nameUUIDFromBytes((tenantId + ":" + scenarioId + ":" + version).getBytes());
    String traceJson = "{}";
    try { traceJson = mapper.writeValueAsString(result.trace()); } catch (Exception ex) { /* ignore */ }
    jdbc.update("""
        insert into scenario.scenario_representative_credit (tenant_id, scenario_version_id, scenario_id, version_number, representative_score, derivation_rule_code, derivation_trace_json, quality_status)
        values (?, ?, ?, ?, ?, ?, ?::jsonb, ?)
        on conflict (tenant_id, scenario_version_id) do update set
          scenario_id = excluded.scenario_id,
          version_number = excluded.version_number,
          representative_score = excluded.representative_score,
          derivation_rule_code = excluded.derivation_rule_code,
          derivation_trace_json = excluded.derivation_trace_json,
          quality_status = excluded.quality_status
        """, tenantId, vId, scenarioId, version, result.score(), result.rule(), traceJson, result.qualityStatus());
  }

  private static String computeQualityStatus(BorrowerCredit b) {
    if (!"AVAILABLE".equals(b.creditStatus())) return "MISSING";
    if (b.creditScore() == null) return "MISSING";
    if (b.creditScore() < 300 || b.creditScore() > 850) return "INVALID";
    if (b.creditScoreDate() != null && b.creditScoreDate().isBefore(LocalDate.now().minusDays(120))) return "STALE";
    return "COMPLETE";
  }

  List<EventRecord> events(UUID tenantId, UUID scenarioId) {
    return jdbc.query("""
        select event_id, tenant_id, scenario_id, event_type, event_version, correlation_id, occurred_at, payload_json::text
        from scenario.scenario_outbox_event where tenant_id = ? and scenario_id = ? order by occurred_at, event_type
        """, (rs, row) -> new EventRecord((UUID) rs.getObject("event_id"), (UUID) rs.getObject("tenant_id"), (UUID) rs.getObject("scenario_id"),
        rs.getString("event_type"), rs.getInt("event_version"), rs.getString("correlation_id"), rs.getTimestamp("occurred_at").toInstant(), read(rs.getString("payload_json"), MAP)), tenantId, scenarioId);
  }

  Optional<Map<String, Object>> versionSnapshot(UUID tenantId, UUID scenarioId, int version) {
    try {
      return Optional.of(jdbc.queryForObject("""
          select snapshot_json::text from scenario.scenario_version where tenant_id = ? and scenario_id = ? and version = ?
          """, (rs, row) -> read(rs.getString(1), MAP), tenantId, scenarioId, version));
    } catch (EmptyResultDataAccessException ex) {
      return Optional.empty();
    }
  }

  private void persistVersions(Scenario scenario) {
    for (VersionManifest version : scenario.versions()) {
      jdbc.update("""
          insert into scenario.scenario_version (tenant_id, scenario_id, version, reason, replay_hash, snapshot_json, created_at)
          values (?, ?, ?, ?, ?, ?::jsonb, ?)
          on conflict (tenant_id, scenario_id, version) do nothing
          """, scenario.tenantId(), scenario.scenarioId(), version.version(), version.reason(), version.hash(), write(snapshot(scenario, version)), Timestamp.from(version.createdAtUtc()));
    }
  }

  private void persistIssues(Scenario scenario) {
    jdbc.update("delete from scenario.scenario_validation_issue where tenant_id = ? and scenario_id = ? and version = ?", scenario.tenantId(), scenario.scenarioId(), scenario.version());
    for (ValidationIssue issue : scenario.validationIssues()) {
      jdbc.update("""
          insert into scenario.scenario_validation_issue (tenant_id, scenario_id, version, code, field_path, severity, message)
          values (?, ?, ?, ?, ?, ?, ?)
          """, scenario.tenantId(), scenario.scenarioId(), scenario.version(), issue.code(), issue.fieldPath(), issue.severity().name(), issue.message());
    }
  }

  private Map<String, Object> snapshot(Scenario scenario, VersionManifest version) {
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("version", version.version());
    snapshot.put("status", scenario.status().name());
    snapshot.put("rawFacts", scenario.rawFacts());
    snapshot.put("normalizedFacts", scenario.normalizedFacts());
    snapshot.put("derivedFields", scenario.derivedFields());
    snapshot.put("validationIssues", scenario.validationIssues());
    return snapshot;
  }

  private Object readResponse(String type, String json) {
    if ("BatchImportResponse".equals(type)) return read(json, BatchImportResponse.class);
    if ("BorrowerCreditResponse".equals(type)) return read(json, BorrowerCreditResponse.class);
    return read(json, ScenarioResponse.class);
  }

  private static UUID tenant(String scope) {
    return UUID.fromString(scope.split(":", 2)[0]);
  }

  private static String scopedKey(String scope, String key) {
    String suffix = scope.contains(":") ? scope.substring(scope.indexOf(':') + 1) + ":" : "";
    return suffix + key;
  }

  private String requestHash(String scope, String key, Object request) {
    return Hashing.sha256(scope + ":" + key + ":" + write(request == null ? Map.of() : request));
  }

  private String write(Object value) {
    try { return mapper.writeValueAsString(value); }
    catch (Exception ex) { throw new IllegalStateException("JSON_WRITE_FAILED", ex); }
  }

  private <T> T read(String json, Class<T> type) {
    try { return mapper.readValue(json, type); }
    catch (Exception ex) { throw new IllegalStateException("JSON_READ_FAILED", ex); }
  }

  private <T> T read(String json, TypeReference<T> type) {
    try { return mapper.readValue(json, type); }
    catch (Exception ex) { throw new IllegalStateException("JSON_READ_FAILED", ex); }
  }
}
