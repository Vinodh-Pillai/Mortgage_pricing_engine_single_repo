package com.wcpe.scenario.domain;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class SubmissionProfileRepository {
  private static final TypeReference<List<SubmissionProfileFieldRule>> RULES = new TypeReference<>() {};
  private static final TypeReference<List<SubmissionProfileVersion>> VERSIONS = new TypeReference<>() {};

  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  SubmissionProfileRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper.findAndRegisterModules();
  }

  @Transactional
  UUID createProfile(UUID tenantId, String channel, String quoteIntent, String profileName, Instant effectiveFromUtc, Instant effectiveToUtc, List<SubmissionProfileFieldRule> rules, String actorId) {
    UUID profileId = UUID.randomUUID();
    int nextVersion = nextVersion(tenantId, channel, quoteIntent);
    String checksum = computeChecksum(rules);
    UUID versionId = UUID.randomUUID();

    jdbc.update("""
        insert into scenario.submission_profile (tenant_id, submission_profile_id, channel, quote_intent, profile_name, created_by, created_at)
        values (?, ?, ?, ?, ?, ?, now())
        on conflict (tenant_id, submission_profile_id) do nothing
        """, tenantId, profileId, channel, quoteIntent, profileName, actorId);

    jdbc.update("""
        insert into scenario.submission_profile_version (tenant_id, profile_version_id, submission_profile_id, version_number, status, effective_from_utc, effective_to_utc, checksum)
        values (?, ?, ?, ?, 'DRAFT', ?, ?, ?)
        """, tenantId, versionId, profileId, nextVersion, Timestamp.from(effectiveFromUtc), effectiveToUtc != null ? Timestamp.from(effectiveToUtc) : null, checksum);

    for (int i = 0; i < rules.size(); i++) {
      SubmissionProfileFieldRule rule = rules.get(i);
      UUID ruleId = UUID.randomUUID();
      jdbc.update("""
          insert into scenario.submission_profile_field_rule (tenant_id, field_rule_id, profile_version_id, section, field_path, required_when_expression, severity, message, remediation_hint)
          values (?, ?, ?, ?, ?, ?, ?, ?, ?)
          """, tenantId, ruleId, versionId, rule.section(), rule.fieldPath(), rule.requiredWhenExpression(), rule.severity().name(), rule.message(), rule.remediationHint());
    }

    return profileId;
  }

  @Transactional
  UUID publishProfile(UUID tenantId, UUID profileId, Instant effectiveFromUtc, Instant effectiveToUtc, String approvalToken, String changeSetRef, String actorId) {
    SubmissionProfile profile = findProfile(tenantId, profileId);
    if (profile == null) throw new ScenarioException(org.springframework.http.HttpStatus.NOT_FOUND, "PROFILE_NOT_FOUND", "Submission profile was not found.", List.of());
    List<SubmissionProfileVersion> current = findVersions(tenantId, profileId);
    if (current.isEmpty()) throw new ScenarioException(org.springframework.http.HttpStatus.NOT_FOUND, "PROFILE_NOT_FOUND", "Submission profile was not found.", List.of());
    SubmissionProfileVersion active = current.stream().filter(v -> v.status() == ProfileStatus.DRAFT).findFirst().orElseGet(() -> current.get(current.size() - 1));

    checkOverlappingPublished(tenantId, profile.channel(), profile.quoteIntent(), effectiveFromUtc, effectiveToUtc);

    int newVersion = active.versionNumber() + 1;
    UUID newVersionId = UUID.randomUUID();
    // Clone rules from active version
    List<SubmissionProfileFieldRule> rules = findRules(tenantId, active.versionId());

    String checksum = computeChecksum(rules);

    // Update old to RETIRED if needed (actually we create new version since versions are immutable)
    jdbc.update("""
        insert into scenario.submission_profile_version (tenant_id, profile_version_id, submission_profile_id, version_number, status, effective_from_utc, effective_to_utc, checksum)
        values (?, ?, ?, ?, 'PUBLISHED', ?, ?, ?)
        """, tenantId, newVersionId, profileId, newVersion, Timestamp.from(effectiveFromUtc), effectiveToUtc != null ? Timestamp.from(effectiveToUtc) : null, checksum);

    // Clone rules for new version
    for (SubmissionProfileFieldRule rule : rules) {
      UUID ruleId = UUID.randomUUID();
      jdbc.update("""
          insert into scenario.submission_profile_field_rule (tenant_id, field_rule_id, profile_version_id, section, field_path, required_when_expression, severity, message, remediation_hint)
          values (?, ?, ?, ?, ?, ?, ?, ?, ?)
          """, tenantId, ruleId, newVersionId, rule.section(), rule.fieldPath(), rule.requiredWhenExpression(), rule.severity().name(), rule.message(), rule.remediationHint());
    }

    // Publish event
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("tenantId", tenantId.toString());
    payload.put("profileId", profileId.toString());
    payload.put("versionId", newVersionId.toString());
    payload.put("versionNumber", newVersion);
    payload.put("channel", profile.channel());
    payload.put("quoteIntent", profile.quoteIntent());
    payload.put("effectiveFromUtc", effectiveFromUtc.toString());
    payload.put("effectiveToUtc", effectiveToUtc != null ? effectiveToUtc.toString() : null);
    payload.put("checksum", checksum);
    payload.put("approvedBy", actorId);
    payload.put("changeSetRef", changeSetRef);
    EventRecord event = new EventRecord(UUID.randomUUID(), tenantId, null, "SubmissionProfilePublished.v1", 1, UUID.randomUUID().toString(), Instant.now(), payload);
    jdbc.update("""
        insert into scenario.scenario_outbox_event (tenant_id, event_id, scenario_id, event_type, event_version, correlation_id, payload_json, occurred_at)
        values (?, ?, NULL, ?, ?, ?, ?::jsonb, ?)
        """, event.tenantId(), event.eventId(), event.eventType(), event.eventVersion(), event.correlationId(), write(event.payload()), Timestamp.from(event.occurredAt()));

    return newVersionId;
  }

  SubmissionProfileResponse getProfile(UUID tenantId, UUID profileId) {
    SubmissionProfile profile = findProfile(tenantId, profileId);
    if (profile == null) throw new ScenarioException(org.springframework.http.HttpStatus.NOT_FOUND, "PROFILE_NOT_FOUND", "Submission profile was not found.", List.of());
    SubmissionProfileVersion latest = profile.versions().stream().max(Comparator.comparingInt(SubmissionProfileVersion::versionNumber)).orElseThrow();
    List<SubmissionProfileFieldRule> rules = findRules(tenantId, latest.versionId());
    return new SubmissionProfileResponse(profileId, latest.versionId(), latest.status(), profile.channel(), profile.quoteIntent(), profile.profileName(),
        latest.versionNumber(), latest.effectiveFromUtc(), latest.effectiveToUtc(), latest.checksum(), rules, Collections.emptyList(), latest.createdAtUtc());
  }

  ActiveChannelProfile getActiveChannelProfile(UUID tenantId, String channel, String quoteIntent, Instant asOf) {
    String sql = """
        select sv.profile_version_id, sv.version_number, sv.checksum,
          jsonb_agg(jsonb_build_object('section', fr.section, 'fieldPath', fr.field_path,
            'requiredWhenExpression', fr.required_when_expression, 'severity', fr.severity,
            'message', fr.message, 'remediationHint', fr.remediation_hint)) as rules_json,
          sv.effective_from_utc
        from scenario.submission_profile p
        join scenario.submission_profile_version sv on sv.submission_profile_id = p.submission_profile_id
        join scenario.submission_profile_field_rule fr on fr.profile_version_id = sv.profile_version_id
        where p.tenant_id = ? and p.channel = ? and p.quote_intent = ?
          and sv.status = 'PUBLISHED'
          and sv.effective_from_utc <= ?
          and (sv.effective_to_utc is null or sv.effective_to_utc > ?)
        group by sv.profile_version_id, sv.version_number, sv.checksum, sv.effective_from_utc
        order by sv.version_number desc
        limit 1
        """;
    List<Map<String, Object>> rows = jdbc.queryForList(sql, tenantId, channel, quoteIntent, Timestamp.from(asOf), Timestamp.from(asOf));
    if (rows.isEmpty()) return null;
    Map<String, Object> row = rows.get(0);
    return new ActiveChannelProfile(channel, quoteIntent,
        (UUID) row.get("profile_version_id"),
        ((Number) row.get("version_number")).intValue(),
        (String) row.get("checksum"),
        read(String.valueOf(row.get("rules_json")), RULES),
        ((Timestamp) row.get("effective_from_utc")).toInstant());
  }

  List<SubmissionProfile> findByChannel(UUID tenantId, String channel) {
    List<UUID> profileIds = jdbc.queryForList("""
        select submission_profile_id from scenario.submission_profile where tenant_id = ? and channel = ?
        """, UUID.class, tenantId, channel);
    List<SubmissionProfile> profiles = new ArrayList<>();
    for (UUID profileId : profileIds) {
      SubmissionProfile p = findProfile(tenantId, profileId);
      if (p != null) profiles.add(p);
    }
    return profiles;
  }

  private SubmissionProfile findProfile(UUID tenantId, UUID profileId) {
    try {
      return jdbc.queryForObject("""
          select p.submission_profile_id, p.channel, p.quote_intent, p.profile_name,
            coalesce(jsonb_agg(jsonb_build_object('versionId', sv.profile_version_id, 'submissionProfileId', sv.submission_profile_id,
              'versionNumber', sv.version_number, 'status', sv.status, 'effectiveFromUtc', sv.effective_from_utc,
              'effectiveToUtc', sv.effective_to_utc, 'checksum', sv.checksum, 'createdAtUtc', sv.created_at))
            filter (where sv.profile_version_id is not null), '[]'::jsonb)::text as versions_json
          from scenario.submission_profile p
          left join scenario.submission_profile_version sv on sv.submission_profile_id = p.submission_profile_id
          where p.tenant_id = ? and p.submission_profile_id = ?
          group by p.submission_profile_id, p.channel, p.quote_intent, p.profile_name
          """, (rs, row) -> {
        UUID id = (UUID) rs.getObject("submission_profile_id");
        return new SubmissionProfile(id, tenantId, rs.getString("channel"), rs.getString("quote_intent"), rs.getString("profile_name"),
            read(rs.getString("versions_json"), new TypeReference<List<SubmissionProfileVersion>>() {}));
      }, tenantId, profileId);
    } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
      return null;
    }
  }

  private List<SubmissionProfileVersion> findVersions(UUID tenantId, UUID profileId) {
    return jdbc.query("""
        select profile_version_id, submission_profile_id, version_number, status, effective_from_utc, effective_to_utc, checksum, created_at
        from scenario.submission_profile_version where submission_profile_id = ?
        order by version_number
        """, (rs, row) -> new SubmissionProfileVersion(
        (UUID) rs.getObject("profile_version_id"), (UUID) rs.getObject("submission_profile_id"),
        rs.getInt("version_number"), ProfileStatus.valueOf(rs.getString("status")),
        rs.getTimestamp("effective_from_utc").toInstant(),
        rs.getTimestamp("effective_to_utc") != null ? rs.getTimestamp("effective_to_utc").toInstant() : null,
        rs.getString("checksum"), new ArrayList<>(),
        rs.getTimestamp("created_at").toInstant()), tenantId);
  }

  private List<SubmissionProfileFieldRule> findRules(UUID tenantId, UUID versionId) {
    return jdbc.query("""
        select section, field_path, required_when_expression, severity, message, remediation_hint
        from scenario.submission_profile_field_rule where profile_version_id = ?
        order by section, field_path
        """, (rs, row) -> new SubmissionProfileFieldRule(
        rs.getString("section"), rs.getString("field_path"), rs.getString("required_when_expression"),
        FieldSeverity.valueOf(rs.getString("severity")), rs.getString("message"), rs.getString("remediation_hint")), versionId);
  }

  private void checkOverlappingPublished(UUID tenantId, String channel, String quoteIntent, Instant effectiveFromUtc, Instant effectiveToUtc) {
    Long count = jdbc.queryForObject("""
        select count(*) from scenario.submission_profile p
        join scenario.submission_profile_version sv on sv.submission_profile_id = p.submission_profile_id
        where p.tenant_id = ? and p.channel = ? and p.quote_intent = ?
          and sv.status = 'PUBLISHED'
          and sv.effective_from_utc < ?
          and (? is null or sv.effective_to_utc is null or ? > sv.effective_to_utc or (sv.effective_to_utc is not null and sv.effective_to_utc > ?))
        """, Long.class, tenantId, channel, quoteIntent, Timestamp.from(effectiveFromUtc),
        Timestamp.from(effectiveToUtc != null ? effectiveToUtc : Instant.MAX),
        effectiveToUtc != null ? Timestamp.from(effectiveToUtc) : null,
        effectiveToUtc != null ? Timestamp.from(effectiveToUtc) : null);
    if (count > 0) throw new ScenarioException(org.springframework.http.HttpStatus.CONFLICT, "OVERLAPPING_PUBLISHED_PROFILE",
        "Overlapping published profile versions exist for this channel/intent combination.", List.of());
  }

  private int nextVersion(UUID tenantId, String channel, String quoteIntent) {
    Integer max = jdbc.queryForObject("""
        select max(sv.version_number) as max_ver from scenario.submission_profile p
        join scenario.submission_profile_version sv on sv.submission_profile_id = p.submission_profile_id
        where p.tenant_id = ? and p.channel = ? and p.quote_intent = ?
        """, Integer.class, tenantId, channel, quoteIntent);
    return max != null ? max + 1 : 1;
  }

  private static String computeChecksum(List<SubmissionProfileFieldRule> rules) {
    return Hashing.sha256(writeRules(rules));
  }

  private static String writeRules(List<SubmissionProfileFieldRule> rules) {
    return rules.stream().map(r -> r.section() + ":" + r.fieldPath() + ":" + r.severity().name()).sorted().toList().toString();
  }

  private String write(Object value) {
    try { return mapper.writeValueAsString(value); }
    catch (Exception ex) { throw new IllegalStateException("JSON_WRITE_FAILED", ex); }
  }

  private <T> T read(String json, TypeReference<T> type) {
    try { return mapper.readValue(json, type); }
    catch (Exception ex) { throw new IllegalStateException("JSON_READ_FAILED", ex); }
  }
}
