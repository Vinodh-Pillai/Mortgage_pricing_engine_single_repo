package com.wcpe.ratefeed.audit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.ratefeed.domain.Hashing;
import com.wcpe.ratefeed.domain.RateFeedModels;
import com.wcpe.ratefeed.domain.RateFeedModels.RateFeedException;
import com.wcpe.ratefeed.domain.RequestContext;
import com.wcpe.ratefeed.role.RateFeedRoles;
import org.postgresql.util.PGobject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AuditReportService {
  private static final int DEFAULT_PAGE = 0;
  private static final int DEFAULT_SIZE = 50;
  private static final int MAX_SIZE = 500;

  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;
  private final long retentionDays;

  public AuditReportService(JdbcTemplate jdbc, ObjectMapper mapper,
      @Value("${wcpe.audit.retention.days:2555}") long retentionDays) {
    this.jdbc = jdbc;
    this.mapper = mapper.findAndRegisterModules();
    this.retentionDays = retentionDays;
  }

  public RateFeedModels.AuditTimelinePage queryTimeline(UUID tenantId,
      RateFeedModels.AuditTimelineRequest request, Set<String> roles) {
    requireAuditView(roles);
    int page = request == null || request.page() < 0 ? DEFAULT_PAGE : request.page();
    int size = request == null || request.size() <= 0 ? DEFAULT_SIZE : Math.min(request.size(), MAX_SIZE);

    QueryParts query = timelineQuery(tenantId, request);
    Long total = jdbc.queryForObject("select count(*) " + query.fromWhere(), Long.class, query.params().toArray());

    List<Object> params = new ArrayList<>(query.params());
    params.add(size);
    params.add(page * size);
    List<RateFeedModels.AuditTimelineEvent> events = jdbc.query(
        "select e.audit_event_id,e.event_type,e.event_version,e.aggregate_type,e.aggregate_id,e.actor_id,e.actor_type," +
            "e.correlation_id,e.causation_id,e.occurred_at,e.source_service,e.before_hash,e.after_hash," +
            "e.evidence_refs::text,e.payload_redacted::text,e.result_hash,e.retention_until,e.legal_hold " +
            query.fromWhere() + " order by e.occurred_at asc, e.sequence_number asc limit ? offset ?",
        (rs, row) -> timelineEvent(rs, roles), params.toArray());
    return new RateFeedModels.AuditTimelinePage(events, page, size, total == null ? 0 : total);
  }

  @Transactional
  public RateFeedModels.AuditReportResponse getBatchReport(UUID tenantId, UUID batchId, String actorId) {
    requireAuditViewFromContext();
    List<AuditEventRow> events = batchEvents(tenantId, batchId);
    if (events.isEmpty()) throw new RateFeedException(HttpStatus.NOT_FOUND, "BATCH_NOT_FOUND", "Batch has no audit events.");
    ensureRetentionReadable(events);

    UUID snapshotId = UUID.randomUUID();
    Instant generatedAt = Instant.now();
    Instant retentionUntil = generatedAt.plus(Duration.ofDays(retentionDays));
    UUID versionId = latestVersionId(tenantId, batchId).orElse(null);
    String snapshotHash = snapshotHash(events);
    Map<String, Object> filters = new LinkedHashMap<>();
    filters.put("batchId", batchId.toString());

    jdbc.update("insert into rate_feed.rate_feed_audit_report_snapshot(tenant_id,snapshot_id,batch_id,version_id,generated_by,generated_at,filters,format,storage_object_id,snapshot_hash,retention_until,watermark_json) values (?,?,?,?,?,?,?,?,?,?,?,?)",
        tenantId, snapshotId, batchId, versionId, actor(actorId), Timestamp.from(generatedAt), jsonb(filters),
        RateFeedModels.AuditReportFormat.JSON.name(), null, snapshotHash, Timestamp.from(retentionUntil),
        jsonb(watermark(tenantId, actor(actorId), generatedAt, snapshotHash)));

    return new RateFeedModels.AuditReportResponse(snapshotId, batchId, versionId, "READY", events.size(),
        snapshotHash, generatedAt, actor(actorId), links(tenantId, batchId, snapshotId));
  }

  @Transactional
  public RateFeedModels.AuditExportResponse createExport(UUID tenantId, UUID batchId,
      RateFeedModels.AuditExportRequest request, String actorId, String correlationId) {
    requireAuditViewFromContext();
    RateFeedModels.AuditReportFormat format = request == null || request.format() == null
        ? RateFeedModels.AuditReportFormat.JSON : request.format();
    String reasonCode = request == null ? null : request.reasonCode();
    if (!present(reasonCode)) {
      throw new RateFeedException(HttpStatus.UNPROCESSABLE_ENTITY, "POLICY_NOT_SATISFIED",
          "Audit export reasonCode is required because governance reason-code configuration is external.");
    }
    boolean includeRawValues = request != null && request.includeRawValues();
    if (includeRawValues && !RequestContext.hasRole(RateFeedRoles.RATE_FEED_AUDIT_EXPORT)) {
      throw new RateFeedException(HttpStatus.FORBIDDEN, "ELEVATED_EXPORT_PERMISSION_REQUIRED",
          "RATE_FEED_AUDIT_EXPORT role is required for raw-value audit exports.");
    }

    List<AuditEventRow> events = batchEvents(tenantId, batchId);
    if (events.isEmpty()) throw new RateFeedException(HttpStatus.NOT_FOUND, "BATCH_NOT_FOUND", "Batch has no audit events.");
    ensureRetentionReadable(events);

    UUID snapshotId = UUID.randomUUID();
    Instant generatedAt = Instant.now();
    Instant retentionUntil = generatedAt.plus(Duration.ofDays(retentionDays));
    String snapshotHash = Hashing.sha256(snapshotHash(events) + "|" + format + "|raw=" + includeRawValues + "|" + safe(reasonCode));
    String storageObjectId = includeRawValues ? null : "audit-exports/" + tenantId + "/" + batchId + "/" + snapshotId + "." + format.name().toLowerCase(Locale.ROOT);

    Map<String, Object> filters = new LinkedHashMap<>();
    filters.put("batchId", batchId.toString());
    filters.put("includeRawValues", includeRawValues);
    filters.put("reasonCode", safe(reasonCode));
    filters.put("correlationId", correlation(correlationId));
    Map<String, Object> watermark = watermark(tenantId, actor(actorId), generatedAt, snapshotHash);
    watermark.put("format", format.name());

    jdbc.update("insert into rate_feed.rate_feed_audit_report_snapshot(tenant_id,snapshot_id,batch_id,version_id,generated_by,generated_at,filters,format,storage_object_id,snapshot_hash,retention_until,watermark_json) values (?,?,?,?,?,?,?,?,?,?,?,?)",
        tenantId, snapshotId, batchId, latestVersionId(tenantId, batchId).orElse(null), actor(actorId),
        Timestamp.from(generatedAt), jsonb(filters), format.name(), storageObjectId, snapshotHash,
        Timestamp.from(retentionUntil), jsonb(watermark));

    return new RateFeedModels.AuditExportResponse(snapshotId, format.name(), "READY", storageObjectId,
        snapshotHash, retentionUntil, links(tenantId, batchId, snapshotId));
  }

  @Transactional(noRollbackFor = RateFeedException.class)
  public RateFeedModels.VerifyReplayResponse verifyReplay(UUID tenantId, UUID batchId,
      RateFeedModels.VerifyReplayRequest request, String actorId) {
    requireAuditViewFromContext();
    List<AuditEventRow> events = batchEvents(tenantId, batchId);
    if (events.isEmpty()) throw new RateFeedException(HttpStatus.NOT_FOUND, "BATCH_NOT_FOUND", "Batch has no audit events.");
    ensureRetentionReadable(events);

    UUID verificationId = UUID.randomUUID();
    String expectedHash = request == null ? null : request.expectedHash();
    String actualHash = snapshotHash(events);
    String correlationId = correlation(request == null ? null : request.correlationId());
    ReplayHashVerifier.ReplayVerificationResult result = ReplayHashVerifier.verify(verificationId, expectedHash,
        actualHash, batchId, actor(actorId), correlationUuid(correlationId));

    jdbc.update("insert into rate_feed.rate_feed_replay_verification(tenant_id,verification_id,batch_id,expected_hash,actual_hash,status,mismatch_classification,created_by,correlation_id,created_at) values (?,?,?,?,?,?,?,?,?,?)",
        tenantId, verificationId, batchId, safe(expectedHash), actualHash, result.status(),
        result.mismatchClassification(), actor(actorId), correlationId, Timestamp.from(result.createdAt()));

    RateFeedModels.VerifyReplayResponse response = new RateFeedModels.VerifyReplayResponse(verificationId, batchId,
        safe(expectedHash), actualHash, RateFeedModels.ReplayVerificationStatus.valueOf(result.status()),
        result.mismatchClassification(), result.createdAt());
    if (!"PASSED".equals(result.status())) {
      throw new RateFeedException(HttpStatus.CONFLICT, "REPLAY_HASH_MISMATCH", "Replay verification hash did not match.");
    }
    return response;
  }

  private QueryParts timelineQuery(UUID tenantId, RateFeedModels.AuditTimelineRequest request) {
    StringBuilder sql = new StringBuilder("from rate_feed.rate_feed_audit_event e where e.tenant_id=?");
    List<Object> params = new ArrayList<>();
    params.add(tenantId);
    if (request == null) return new QueryParts(sql.toString(), params);
    if (request.from() != null) { sql.append(" and e.occurred_at >= ?"); params.add(Timestamp.from(request.from())); }
    if (request.to() != null) { sql.append(" and e.occurred_at <= ?"); params.add(Timestamp.from(request.to())); }
    if (request.investorId() != null) {
      sql.append(" and (exists (select 1 from rate_feed.rate_feed_batch b where b.tenant_id=e.tenant_id and b.batch_id=e.aggregate_id and b.investor_id=?) or exists (select 1 from rate_feed.rate_sheet s where s.tenant_id=e.tenant_id and (s.sheet_id=e.aggregate_id or s.source_batch_id=e.aggregate_id) and s.investor_id=?))");
      params.add(request.investorId()); params.add(request.investorId());
    }
    if (request.channelId() != null) {
      sql.append(" and (exists (select 1 from rate_feed.rate_feed_batch b where b.tenant_id=e.tenant_id and b.batch_id=e.aggregate_id and b.channel_id=?) or exists (select 1 from rate_feed.rate_sheet s where s.tenant_id=e.tenant_id and (s.sheet_id=e.aggregate_id or s.source_batch_id=e.aggregate_id) and s.channel_id=?))");
      params.add(request.channelId()); params.add(request.channelId());
    }
    if (request.batchId() != null) { sql.append(" and (e.aggregate_id=? or exists (select 1 from rate_feed.rate_sheet s where s.tenant_id=e.tenant_id and s.source_batch_id=? and s.sheet_id=e.aggregate_id))"); params.add(request.batchId()); params.add(request.batchId()); }
    if (request.versionId() != null) { sql.append(" and (e.aggregate_id=? or exists (select 1 from rate_feed.rate_sheet s where s.tenant_id=e.tenant_id and s.sheet_id=? and e.aggregate_id=s.source_batch_id))"); params.add(request.versionId()); params.add(request.versionId()); }
    if (present(request.actorId())) { sql.append(" and e.actor_id=?"); params.add(request.actorId()); }
    if (present(request.eventType())) { sql.append(" and e.event_type=?"); params.add(request.eventType()); }
    if (present(request.status())) {
      sql.append(" and (exists (select 1 from rate_feed.rate_feed_batch b where b.tenant_id=e.tenant_id and b.batch_id=e.aggregate_id and b.status=?) or exists (select 1 from rate_feed.rate_sheet s where s.tenant_id=e.tenant_id and (s.sheet_id=e.aggregate_id or s.source_batch_id=e.aggregate_id) and s.status=?))");
      params.add(request.status()); params.add(request.status());
    }
    if (present(request.correlationId())) { sql.append(" and e.correlation_id=?"); params.add(request.correlationId()); }
    return new QueryParts(sql.toString(), params);
  }

  private RateFeedModels.AuditTimelineEvent timelineEvent(ResultSet rs, Set<String> roles) throws java.sql.SQLException {
    Map<String, Object> envelope = new LinkedHashMap<>();
    envelope.put("evidenceRefs", readObject(rs.getString("evidence_refs")));
    envelope.putAll(AuditEnvelopeRedaction.redact(readObject(rs.getString("payload_redacted")), roles));
    return new RateFeedModels.AuditTimelineEvent(
        rs.getObject("audit_event_id", UUID.class), rs.getString("event_type"), rs.getInt("event_version"),
        rs.getString("aggregate_type"), rs.getObject("aggregate_id", UUID.class), rs.getString("actor_id"),
        rs.getString("actor_type"), rs.getString("correlation_id"), rs.getString("causation_id"),
        rs.getTimestamp("occurred_at").toInstant(), rs.getString("source_service"), rs.getString("before_hash"),
        rs.getString("after_hash"), envelope, rs.getString("result_hash"),
        RateFeedModels.AuditRedactionLevel.valueOf(AuditEnvelopeRedaction.getRedactionLevel(roles)));
  }

  private List<AuditEventRow> batchEvents(UUID tenantId, UUID batchId) {
    return jdbc.query("select e.audit_event_id,e.occurred_at,e.sequence_number,e.before_hash,e.after_hash,e.result_hash,e.retention_until,e.legal_hold " +
            "from rate_feed.rate_feed_audit_event e where e.tenant_id=? and (e.aggregate_id=? or exists (" +
            "select 1 from rate_feed.rate_sheet s where s.tenant_id=e.tenant_id and s.source_batch_id=? and s.sheet_id=e.aggregate_id)) " +
            "order by e.occurred_at asc, e.sequence_number asc",
        (rs, row) -> new AuditEventRow(rs.getObject("audit_event_id", UUID.class),
            rs.getTimestamp("occurred_at").toInstant(), rs.getObject("sequence_number", Long.class),
            rs.getString("before_hash"), rs.getString("after_hash"), rs.getString("result_hash"),
            rs.getTimestamp("retention_until").toInstant(), rs.getBoolean("legal_hold")),
        tenantId, batchId, batchId);
  }

  private Optional<UUID> latestVersionId(UUID tenantId, UUID batchId) {
    List<UUID> ids = jdbc.query("select sheet_id from rate_feed.rate_sheet where tenant_id=? and source_batch_id=? order by created_at desc limit 1",
        (rs, row) -> rs.getObject("sheet_id", UUID.class), tenantId, batchId);
    return ids.stream().findFirst();
  }

  private String snapshotHash(List<AuditEventRow> events) {
    String material = events.stream()
        .map(e -> e.auditEventId() + "|" + e.occurredAt() + "|" + safe(e.sequenceNumber()) + "|" + stableHash(e))
        .collect(Collectors.joining("\n"));
    return Hashing.sha256(material);
  }

  private String stableHash(AuditEventRow event) {
    if (present(event.resultHash())) return event.resultHash();
    if (present(event.afterHash())) return event.afterHash();
    if (present(event.beforeHash())) return event.beforeHash();
    return event.auditEventId().toString();
  }

  private void ensureRetentionReadable(List<AuditEventRow> events) {
    Instant now = Instant.now();
    boolean expired = events.stream().anyMatch(e -> !e.legalHold() && e.retentionUntil() != null && now.isAfter(e.retentionUntil()));
    if (expired) throw new RateFeedException(HttpStatus.UNPROCESSABLE_ENTITY, "RETENTION_WINDOW_EXPIRED", "Audit retention window has expired.");
  }

  private void requireAuditView(Set<String> roles) {
    if (roles == null || !(roles.contains(RateFeedRoles.RATE_FEED_AUDIT_VIEW) || roles.contains(RateFeedRoles.RATE_FEED_AUDIT_EXPORT) || roles.contains(RateFeedRoles.RATE_FEED_ADMIN))) {
      throw new RateFeedException(HttpStatus.FORBIDDEN, "AUDIT_ACCESS_DENIED", "RATE_FEED_AUDIT_VIEW role is required.");
    }
  }

  private void requireAuditViewFromContext() {
    if (!(RequestContext.hasRole(RateFeedRoles.RATE_FEED_AUDIT_VIEW) || RequestContext.hasRole(RateFeedRoles.RATE_FEED_AUDIT_EXPORT) || RequestContext.hasRole(RateFeedRoles.RATE_FEED_ADMIN))) {
      throw new RateFeedException(HttpStatus.FORBIDDEN, "AUDIT_ACCESS_DENIED", "RATE_FEED_AUDIT_VIEW role is required.");
    }
  }

  private Map<String, String> links(UUID tenantId, UUID batchId, UUID snapshotId) {
    return Map.of(
        "batch", "/api/v1/tenants/" + tenantId + "/rate-feed-batches/" + batchId,
        "report", "/api/v1/tenants/" + tenantId + "/rate-feed-audit-reports/" + batchId,
        "snapshot", "/api/v1/tenants/" + tenantId + "/rate-feed-audit-reports/" + batchId + "/snapshots/" + snapshotId,
        "export", "/api/v1/tenants/" + tenantId + "/rate-feed-audit-reports/" + batchId + "/exports/" + snapshotId);
  }

  private Map<String, Object> watermark(UUID tenantId, String actorId, Instant generatedAt, String hash) {
    Map<String, Object> watermark = new LinkedHashMap<>();
    watermark.put("tenantId", tenantId.toString());
    watermark.put("actorId", actorId);
    watermark.put("generatedAt", generatedAt.toString());
    watermark.put("hash", hash);
    return watermark;
  }

  private Map<String, Object> readObject(String json) {
    try {
      if (json == null || json.isBlank()) return Map.of();
      Object value = mapper.readValue(json, Object.class);
      if (value instanceof Map<?, ?> map) {
        Map<String, Object> out = new LinkedHashMap<>();
        map.forEach((k, v) -> out.put(String.valueOf(k), v));
        return out;
      }
      return Map.of("items", mapper.convertValue(value, new TypeReference<List<Object>>() {}));
    } catch (Exception ex) { throw new IllegalStateException(ex); }
  }

  private PGobject jsonb(Object value) {
    try {
      PGobject object = new PGobject();
      object.setType("jsonb");
      object.setValue(mapper.writeValueAsString(value));
      return object;
    } catch (Exception ex) { throw new IllegalStateException(ex); }
  }

  private UUID correlationUuid(String correlationId) {
    try { return UUID.fromString(correlationId); }
    catch (Exception ex) { return UUID.nameUUIDFromBytes(correlationId.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
  }

  private static boolean present(String value) { return value != null && !value.isBlank(); }
  private static String actor(String actor) { return actor == null || actor.isBlank() ? "system" : actor; }
  private static String correlation(String correlationId) { return correlationId == null || correlationId.isBlank() ? UUID.randomUUID().toString() : correlationId; }
  private static String safe(Object value) { return value == null ? "" : value.toString(); }

  private record QueryParts(String fromWhere, List<Object> params) {}
  private record AuditEventRow(UUID auditEventId, Instant occurredAt, Long sequenceNumber, String beforeHash,
      String afterHash, String resultHash, Instant retentionUntil, boolean legalHold) {}
}
