ALTER TABLE audit_outbox_events
  ADD COLUMN lock_version bigint NOT NULL DEFAULT 0;
