CREATE TABLE IF NOT EXISTS fair_lending.event_outbox (
  event_id UUID PRIMARY KEY,
  event_type VARCHAR(120) NOT NULL,
  tenant_id UUID NOT NULL,
  aggregate_id VARCHAR(160) NOT NULL,
  occurred_at TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_fair_lending_event_outbox_tenant_occurred
  ON fair_lending.event_outbox (tenant_id, occurred_at);
