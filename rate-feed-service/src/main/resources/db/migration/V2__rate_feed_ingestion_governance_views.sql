-- Non-destructive governance/naming alignment for PII-04.
-- Existing canonical tables remain under rate_feed.*; compatibility views expose
-- ingestion-oriented names for downstream/read-only governance consumers.

-- Rate-feed ingestion compatibility views for governance consumers
create or replace view rate_feed.rate_feed_ingestion_upload_session as
select
  tenant_id,
  upload_session_id,
  investor_id,
  channel_id,
  feed_format_id,
  source_type,
  effective_at,
  timezone,
  file_name,
  content_type,
  content_length_bytes,
  supersedes_batch_id,
  notes,
  status,
  expires_at,
  created_by,
  correlation_id,
  request_hash,
  created_at,
  updated_at
from rate_feed.upload_session;

create or replace view rate_feed.rate_feed_ingestion_raw_file as
select
  tenant_id,
  raw_file_id,
  storage_object_id,
  file_sha256,
  scan_status,
  scan_result_id,
  retention_until,
  legal_hold,
  created_at
from rate_feed.raw_file;

create or replace view rate_feed.rate_feed_ingestion_batch as
select
  tenant_id,
  batch_id,
  upload_session_id,
  investor_id,
  channel_id,
  feed_format_id,
  source_type,
  status,
  effective_at,
  timezone,
  raw_file_id,
  file_sha256,
  file_name,
  content_type,
  content_length_bytes,
  supersedes_batch_id,
  uploaded_by,
  correlation_id,
  result_hash,
  created_at,
  updated_at
from rate_feed.rate_feed_batch;

comment on schema rate_feed is 'Rate-feed ingestion schema; canonical PII-04 tables retained with non-destructive compatibility views.';
comment on view rate_feed.rate_feed_ingestion_upload_session is 'Compatibility view over rate_feed.upload_session for ingestion governance naming.';
comment on view rate_feed.rate_feed_ingestion_raw_file is 'Compatibility view over rate_feed.raw_file for ingestion governance naming.';
comment on view rate_feed.rate_feed_ingestion_batch is 'Compatibility view over rate_feed.rate_feed_batch for ingestion governance naming.';
comment on table rate_feed.idempotency_record is 'Tenant-scoped idempotency store keyed by route response_type and request_hash; response_json is jsonb.';
comment on column rate_feed.idempotency_record.response_type is 'Command/response type discriminator used to prevent cross-route idempotency replay.';
comment on table rate_feed.outbox_event is 'Transactional outbox for synthetic/dev RateSheetUploaded.v1 events; headers_json and payload_json are jsonb.';
comment on table rate_feed.audit_event is 'Redacted audit trail for rate-feed upload actions; payload_redacted is jsonb and must not contain borrower PII.';
