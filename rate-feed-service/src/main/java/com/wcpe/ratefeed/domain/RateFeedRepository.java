package com.wcpe.ratefeed.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.ratefeed.domain.RateFeedModels.RateFeedException;
import com.wcpe.ratefeed.domain.RateFeedModels.UploadSessionRequest;
import com.wcpe.ratefeed.domain.RateFeedModels.UploadSessionResponse;
import com.wcpe.ratefeed.domain.RateFeedModels.CompleteUploadRequest;
import com.wcpe.ratefeed.domain.RateFeedModels.CompleteUploadResponse;
import com.wcpe.ratefeed.domain.RateFeedModels.BatchResponse;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.dao.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.postgresql.util.PGobject;

@Repository
class RateFeedRepository {
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  RateFeedRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper.findAndRegisterModules();
  }

  <T> T idempotent(UUID tenantId, String key, Object request, Class<T> responseType, Supplier<T> command) {
    if (key == null || key.isBlank()) throw new RateFeedException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key is required.");
    String requestHash = Hashing.sha256(json(request));
    try {
      return jdbc.queryForObject("select request_hash,response_type,response_json::text from rate_feed.idempotency_record where tenant_id=? and idempotency_key=?", (rs, row) -> {
        if (!responseType.getSimpleName().equals(rs.getString("response_type"))) throw new RateFeedException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", "Idempotency key reused for a different command route.");
        if (!requestHash.equals(rs.getString("request_hash"))) throw new RateFeedException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", "Idempotency key reused with a different request.");
        return read(rs.getString("response_json"), responseType);
      }, tenantId, key);
    } catch (EmptyResultDataAccessException ex) {
      T response = command.get();
      jdbc.update("insert into rate_feed.idempotency_record(tenant_id,idempotency_key,request_hash,response_type,response_json) values (?,?,?,?,?)", tenantId, key, requestHash, responseType.getSimpleName(), jsonb(response));
      return response;
    }
  }

  void saveSession(UUID tenantId, UUID sessionId, UploadSessionRequest request, String actor, String correlationId, String requestHash, UploadSessionResponse response) {
    jdbc.update("insert into rate_feed.upload_session(tenant_id,upload_session_id,investor_id,channel_id,feed_format_id,source_type,effective_at,timezone,file_name,content_type,content_length_bytes,supersedes_batch_id,notes,status,expires_at,created_by,correlation_id,request_hash) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        tenantId, sessionId, request.investorId(), request.channelId(), request.feedFormatId(), request.sourceType(), Timestamp.from(request.effectiveAt()), request.timezone(), request.fileName(), RateFeedService.normalizeMediaType(request.contentType()), request.contentLengthBytes(), request.supersedesBatchId(), request.notes(), "OPEN", Timestamp.from(response.expiresAt()), actor, correlationId, requestHash);
    // V-004 fix: separate request audit from response audit
    // Request audit (before state)
    audit(tenantId, sessionId, "REQUEST_AUDIT", "UploadSession", actor, correlationId, requestHash, null, Map.of("phase", "request"));
    // Response audit (after state)
    audit(tenantId, sessionId, "RESPONSE_AUDIT", "UploadSession", actor, correlationId, null, response.resultHash(), Map.of("phase", "response"));
  }

  UploadSessionRow session(UUID tenantId, UUID sessionId) {
    try {
      return jdbc.queryForObject("select * from rate_feed.upload_session where tenant_id=? and upload_session_id=?", (rs, row) -> new UploadSessionRow(
          rs.getObject("upload_session_id", UUID.class), rs.getObject("investor_id", UUID.class), rs.getObject("channel_id", UUID.class), rs.getObject("feed_format_id", UUID.class), rs.getString("source_type"), rs.getTimestamp("effective_at").toInstant(), rs.getString("timezone"), rs.getString("file_name"), rs.getString("content_type"), rs.getLong("content_length_bytes"), rs.getObject("supersedes_batch_id", UUID.class), rs.getString("status"), rs.getTimestamp("expires_at").toInstant(), rs.getString("created_by"), rs.getString("correlation_id")), tenantId, sessionId);
    } catch (EmptyResultDataAccessException ex) {
      throw new RateFeedException(HttpStatus.NOT_FOUND, "UPLOAD_SESSION_NOT_FOUND", "Upload session was not found.");
    }
  }

  CompleteUploadResponse complete(UUID tenantId, UploadSessionRow session, CompleteUploadRequest request, String actor, String correlationId) {
    UUID rawFileId = UUID.randomUUID();
    UUID batchId = UUID.randomUUID();
    String resultHash = Hashing.sha256(json(Map.of("batchId", batchId, "fileSha256", request.fileSha256(), "sessionId", session.uploadSessionId())));
    try {
      jdbc.update("insert into rate_feed.raw_file(tenant_id,raw_file_id,storage_object_id,file_sha256,scan_status,scan_result_id,retention_until) values (?,?,?,?,?,?,?)",
          tenantId, rawFileId, request.storageObjectId(), request.fileSha256().toLowerCase(Locale.ROOT), "CLEAN", request.scanResultId(), Timestamp.from(Instant.now().plus(Duration.ofDays(2555))));
      jdbc.update("insert into rate_feed.rate_feed_batch(tenant_id,batch_id,upload_session_id,investor_id,channel_id,feed_format_id,source_type,status,effective_at,timezone,raw_file_id,file_sha256,file_name,content_type,content_length_bytes,supersedes_batch_id,uploaded_by,correlation_id,result_hash) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
          tenantId, batchId, session.uploadSessionId(), session.investorId(), session.channelId(), session.feedFormatId(), session.sourceType(), "UPLOADED", Timestamp.from(session.effectiveAt()), session.timezone(), rawFileId, request.fileSha256().toLowerCase(Locale.ROOT), session.fileName(), session.contentType(), session.contentLengthBytes(), session.supersedesBatchId(), actor, correlationId, resultHash);
      jdbc.update("update rate_feed.upload_session set status='COMPLETED', updated_at=now() where tenant_id=? and upload_session_id=?", tenantId, session.uploadSessionId());
    } catch (DuplicateKeyException ex) {
      throw new RateFeedException(HttpStatus.CONFLICT, "DUPLICATE_FILE_HASH", "A batch already exists for this file hash, feed format, and effective timestamp.");
    }
    CompleteUploadResponse response = new CompleteUploadResponse(batchId, "UPLOADED", rawFileId, UUID.randomUUID(), Map.of("batch", "/api/v1/tenants/" + tenantId + "/rate-feed-batches/" + batchId), resultHash);
    // V-003 fix: pass actual session values to outbox
    outbox(tenantId, batchId, "RateSheetUploaded.v1", 1, actor, correlationId,
        Map.of("investorId", session.investorId().toString(),
               "channelId", session.channelId().toString(),
               "feedFormatId", session.feedFormatId().toString()),
        Map.of("batchId", batchId, "uploadSessionId", session.uploadSessionId(), "effectiveAt", session.effectiveAt().toString(), "fileSha256", request.fileSha256().toLowerCase(Locale.ROOT), "status", "UPLOADED"));
    audit(tenantId, batchId, "RATE_SHEET_UPLOADED", "RateFeedBatch", actor, correlationId, null, resultHash, response);
    return response;
  }

  BatchResponse batch(UUID tenantId, UUID batchId) {
    try {
      return jdbc.queryForObject("select * from rate_feed.rate_feed_batch where tenant_id=? and batch_id=?", (rs, row) -> new BatchResponse(rs.getObject("batch_id", UUID.class), rs.getObject("upload_session_id", UUID.class), rs.getObject("investor_id", UUID.class), rs.getObject("channel_id", UUID.class), rs.getObject("feed_format_id", UUID.class), rs.getString("source_type"), RateFeedModels.BatchStatus.from(rs.getString("status")), rs.getTimestamp("effective_at").toInstant(), rs.getString("timezone"), rs.getObject("raw_file_id", UUID.class), rs.getString("file_sha256"), rs.getString("file_name"), rs.getString("content_type"), rs.getLong("content_length_bytes"), rs.getObject("supersedes_batch_id", UUID.class), rs.getString("uploaded_by"), rs.getString("correlation_id"), rs.getString("result_hash")), tenantId, batchId);
    } catch (EmptyResultDataAccessException ex) {
      throw new RateFeedException(HttpStatus.NOT_FOUND, "BATCH_NOT_FOUND", "Rate feed batch was not found.");
    }
  }

  // V-003 fix: outbox accepts sessionHeaders with actual session values
  void outbox(UUID tenantId, UUID aggregateId, String eventType, int version, String actor, String correlationId, Map<String, String> sessionHeaders, Object payload) {
    UUID eventId = UUID.randomUUID();
    Map<String, Object> headers = new HashMap<>();
    headers.put("tenantId", tenantId);
    headers.put("eventId", eventId);
    headers.put("eventType", eventType);
    headers.put("eventVersion", version);
    headers.put("sourceService", "rate-feed-service");
    headers.put("actorId", actor);
    headers.put("correlationId", correlationId);
    headers.put("occurredAt", Instant.now().toString());
    // V-003: use actual session values (investorId, channelId, feedFormatId)
    if (sessionHeaders != null) {
      headers.put("investorId", sessionHeaders.getOrDefault("investorId", ""));
      headers.put("channelId", sessionHeaders.getOrDefault("channelId", ""));
      headers.put("feedFormatId", sessionHeaders.getOrDefault("feedFormatId", ""));
    }
    jdbc.update("insert into rate_feed.outbox_event(tenant_id,event_id,aggregate_type,aggregate_id,event_type,event_version,event_key,headers,payload) values (?,?,?,?,?,?,?,?,?)", tenantId, eventId, "RateFeedBatch", aggregateId, eventType, version, tenantId + ":" + aggregateId, jsonb(headers), jsonb(payload));
  }

  // Legacy overload for backward compat
  void outbox(UUID tenantId, UUID aggregateId, String eventType, int version, String actor, String correlationId, Object payload) {
    outbox(tenantId, aggregateId, eventType, version, actor, correlationId, null, payload);
  }

  // V-004 fix: audit separates request (before_hash) from response (after_hash)
  void audit(UUID tenantId, UUID aggregateId, String eventType, String aggregateType, String actor, String correlationId, String beforeHash, String afterHash, Object payload) {
    jdbc.update("insert into rate_feed.audit_event(tenant_id,audit_event_id,event_type,aggregate_type,aggregate_id,actor_id,correlation_id,before_hash,after_hash,payload_redacted,result_hash) values (?,?,?,?,?,?,?,?,?,?,?)",
        tenantId, UUID.randomUUID(), eventType, aggregateType, aggregateId, actor, correlationId, beforeHash, afterHash, jsonb(payload), afterHash);
  }

  String json(Object value) { try { return mapper.writeValueAsString(value); } catch (Exception ex) { throw new IllegalStateException(ex); } }
  <T> T read(String json, Class<T> type) { try { return mapper.readValue(json, type); } catch (Exception ex) { throw new IllegalStateException(ex); } }
  PGobject jsonb(Object value) {
    try {
      PGobject object = new PGobject();
      object.setType("jsonb");
      object.setValue(json(value));
      return object;
    } catch (Exception ex) { throw new IllegalStateException(ex); }
  }

  record UploadSessionRow(UUID uploadSessionId, UUID investorId, UUID channelId, UUID feedFormatId, String sourceType, Instant effectiveAt, String timezone, String fileName, String contentType, long contentLengthBytes, UUID supersedesBatchId, String status, Instant expiresAt, String createdBy, String correlationId) {}
}
