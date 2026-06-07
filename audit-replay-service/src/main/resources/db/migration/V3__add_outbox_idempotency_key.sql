ALTER TABLE audit_outbox_events
  ADD COLUMN idempotency_key varchar(160);

CREATE INDEX idx_audit_outbox_events_tenant_idempotency_key
  ON audit_outbox_events (tenant_id, idempotency_key)
  WHERE idempotency_key IS NOT NULL;
