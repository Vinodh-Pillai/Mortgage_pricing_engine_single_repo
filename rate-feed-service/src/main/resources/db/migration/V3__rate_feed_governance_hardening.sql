-- PII-04 governance hardening migration for rate-feed-service
-- Add investor/channel/feed-format validation constraints and governance checks

-- Governance validation function: validate required fields for outbox events
create or replace function rate_feed.validate_outbox_governance() returns trigger as $$
declare
  investor uuid;
  channel uuid;
  feed_format uuid;
begin
  -- Extract investor_id, channel_id, feed_format_id from headers_json
  investor := new.headers_json ->> 'investorId';
  channel := new.headers_json ->> 'channelId';
  feed_format := new.headers_json ->> 'feedFormatId';

  -- Governance check 1: investor_id must not be null
  if investor is null or investor = '' or investor = 'null' then
    raise exception 'GOVERNANCE_ERROR: investor_id is required in outbox headers';
  end if;

  -- Governance check 2: channel_id must not be null
  if channel is null or channel = '' or channel = 'null' then
    raise exception 'GOVERNANCE_ERROR: channel_id is required in outbox headers';
  end if;

  -- Governance check 3: feed_format_id must not be null
  if feed_format is null or feed_format = '' or feed_format = 'null' then
    raise exception 'GOVERNANCE_ERROR: feed_format_id is required in outbox headers';
  end if;

  -- Governance check 4: validate format of UUIDs (basic check)
  if investor !~ '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$' then
    raise exception 'GOVERNANCE_ERROR: investor_id must be a valid UUID format';
  end if;

  if channel !~ '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$' then
    raise exception 'GOVERNANCE_ERROR: channel_id must be a valid UUID format';
  end if;

  if feed_format !~ '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$' then
    raise exception 'GOVERNANCE_ERROR: feed_format_id must be a valid UUID format';
  end if;

  return new;
end;
$$ language plpgsql;

-- Governance validation function: validate investor/channel/feed-format in batch records
create or replace function rate_feed.validate_batch_governance() returns trigger as $$
begin
  -- Governance check: investor_id must not be null
  if new.investor_id is null then
    raise exception 'GOVERNANCE_ERROR: investor_id cannot be null in batch';
  end if;

  -- Governance check: channel_id must not be null
  if new.channel_id is null then
    raise exception 'GOVERNANCE_ERROR: channel_id cannot be null in batch';
  end if;

  -- Governance check: feed_format_id must not be null
  if new.feed_format_id is null then
    raise exception 'GOVERNANCE_ERROR: feed_format_id cannot be null in batch';
  end if;

  -- Governance check: all UUIDs must be valid (not zero UUID)
  if new.investor_id = '00000000-0000-0000-0000-000000000000'::uuid then
    raise exception 'GOVERNANCE_ERROR: investor_id cannot be zero UUID';
  end if;

  if new.channel_id = '00000000-0000-0000-0000-000000000000'::uuid then
    raise exception 'GOVERNANCE_ERROR: channel_id cannot be zero UUID';
  end if;

  if new.feed_format_id = '00000000-0000-0000-0000-000000000000'::uuid then
    raise exception 'GOVERNANCE_ERROR: feed_format_id cannot be zero UUID';
  end if;

  return new;
end;
$$ language plpgsql;

-- Create audit trigger for outbox events
create or replace function rate_feed.outbox_audit_trigger() returns trigger as $$
begin
  insert into rate_feed.audit_event (
    tenant_id,
    audit_event_id,
    event_type,
    aggregate_type,
    aggregate_id,
    actor_id,
    correlation_id,
    causation_id,
    before_hash,
    after_hash,
    payload_redacted,
    result_hash,
    occurred_at
  ) values (
    new.tenant_id,
    new.event_id,
    'OUTBOX_AUDIT',
    'rate_feed_batch',
    new.aggregate_id,
    new.headers_json ->> 'actorId',
    new.headers_json ->> 'correlationId',
    new.event_id::text,
    'AUDIT_TRIG',
    'AUDIT_TRIG',
    'null'::jsonb,
    Hash(new.headers_json ->> 'correlationId'),
    now()
  );
  return new;
end;
$$ language plpgsql;

-- Create audit trigger for batch events
create or replace function rate_feed.batch_audit_trigger() returns trigger as $$
begin
  insert into rate_feed.audit_event (
    tenant_id,
    audit_event_id,
    event_type,
    aggregate_type,
    aggregate_id,
    actor_id,
    correlation_id,
    causation_id,
    before_hash,
    after_hash,
    payload_redacted,
    result_hash,
    occurred_at
  ) values (
    new.tenant_id,
    uuid_generate_v4(),
    'RATE_FEED_BATCH_AUDIT',
    'rate_feed_batch',
    new.batch_id,
    coalesce(new.uploaded_by, 'system'),
    new.correlation_id,
    new.batch_id::text,
    Hash(new.result_hash),
    Hash(new.result_hash),
    'null'::jsonb,
    Hash(new.correlation_id),
    now()
  );
  return new;
end;
$$ language plpgsql;

-- Apply audit triggers to outbox_event table
drop trigger if exists outbox_audit_trig on rate_feed.outbox_event;
create trigger outbox_audit_trig
  before insert or update on rate_feed.outbox_event
  for each row
  execute function rate_feed.outbox_audit_trigger();

-- Apply audit triggers to rate_feed_batch table
drop trigger if exists batch_audit_trig on rate_feed.rate_feed_batch;
create trigger batch_audit_trig
  before insert or update on rate_feed.rate_feed_batch
  for each row
  execute function rate_feed.batch_audit_trigger();

-- Add governance check constraint to outbox_event table
alter table rate_feed.outbox_event
  add constraint outbox_governance_check
  check (
    headers_json ->> 'investorId' is not null and
    headers_json ->> 'investorId' != '' and
    headers_json ->> 'investorId' != 'null' and
    headers_json ->> 'channelId' is not null and
    headers_json ->> 'channelId' != '' and
    headers_json ->> 'channelId' != 'null' and
    headers_json ->> 'feedFormatId' is not null and
    headers_json ->> 'feedFormatId' != '' and
    headers_json ->> 'feedFormatId' != 'null'
  );

-- Add governance check constraint to rate_feed_batch table
alter table rate_feed.rate_feed_batch
  add constraint batch_governance_check
  check (
    investor_id is not null and
    channel_id is not null and
    feed_format_id is not null
  );

-- Create index for governance tracking
create index if not exists outbox_investor_channel_idx on rate_feed.outbox_event (tenant_id, headers_json ->> 'investorId', headers_json ->> 'channelId');
create index if not exists batch_governance_lookup_idx on rate_feed.rate_feed_batch (tenant_id, investor_id, channel_id, feed_format_id);

-- Create governance view for downstream consumers
create or replace view rate_feed.governance_rate_feed_batch as
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
  updated_at,
  -- Validate governance fields are present
  case
    when investor_id is null or channel_id is null or feed_format_id is null then false
    when investor_id = '00000000-0000-0000-0000-000000000000'::uuid then false
    when channel_id = '00000000-0000-0000-0000-000000000000'::uuid then false
    when feed_format_id = '00000000-0000-0000-0000-000000000000'::uuid then false
    else true
  end as governance_valid
from rate_feed.rate_feed_batch;

comment on view rate_feed.governance_rate_feed_batch is 'Governance-maintained view with governance_valid flag for downstream consumers';

comment on function rate_feed.validate_outbox_governance() is ' Governance validation function for outbox events';
comment on function rate_feed.validate_batch_governance() is 'Governance validation function for rate_feed_batch records';
comment on function rate_feed.outbox_audit_trigger() is 'Audit trigger for outbox_event records';
comment on function rate_feed.batch_audit_trigger() is 'Audit trigger for rate_feed_batch records';
