-- V5: Replay, resolution audit, idempotency hardening, tenant columns

-- Replay record table
CREATE TABLE IF NOT EXISTS rate_feed.replay_record (
    replay_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sheet_id        UUID NOT NULL REFERENCES rate_feed.rate_sheet(sheet_id) ON DELETE CASCADE,
    version         INTEGER NOT NULL,
    input_hash      VARCHAR(128) NOT NULL,
    output_hash     VARCHAR(128) NOT NULL,
    replayed_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    replayed_by     VARCHAR(128) NOT NULL,
    correlation_id  VARCHAR(128)
);
CREATE INDEX replay_sheet_idx ON rate_feed.replay_record (sheet_id, version, replayed_at DESC);

-- Resolution audit table
CREATE TABLE IF NOT EXISTS rate_feed.resolution_audit (
    audit_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sheet_id        UUID NOT NULL REFERENCES rate_feed.rate_sheet(sheet_id) ON DELETE CASCADE,
    version         INTEGER NOT NULL,
    investor_id     UUID NOT NULL,
    channel_id      UUID NOT NULL,
    product_code    VARCHAR(32) NOT NULL,
    lock_period     INTEGER NOT NULL,
    effective_at    TIMESTAMPTZ NOT NULL,
    result_hash     VARCHAR(128) NOT NULL,
    resolved_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_by     VARCHAR(128) NOT NULL,
    correlation_id  VARCHAR(128)
);
CREATE INDEX resolution_audit_sheet_idx ON rate_feed.resolution_audit (sheet_id, version, resolved_at DESC);

-- Idempotency record hardening (if columns missing)
ALTER TABLE rate_feed.idempotency_record ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

-- Tenant columns on existing tables
ALTER TABLE rate_feed.rate_feed_batch ADD COLUMN IF NOT EXISTS tenant_id UUID;
ALTER TABLE rate_feed.rate_sheet ADD COLUMN IF NOT EXISTS tenant_id UUID DEFAULT '00000000-0000-0000-0000-000000000000';
