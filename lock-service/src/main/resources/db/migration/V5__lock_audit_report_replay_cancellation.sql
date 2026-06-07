create table lock_audit_reports (
  tenant_id uuid not null,
  report_id varchar(64) primary key,
  status varchar(40) not null,
  requested_by varchar(128) not null,
  generated_at timestamptz not null,
  criteria_hash varchar(128) not null,
  manifest_hash varchar(128) not null,
  idempotency_key varchar(160) not null,
  correlation_id varchar(128) not null,
  constraint lock_audit_reports_tenant_report unique (tenant_id, report_id),
  constraint lock_audit_reports_idempotency unique (tenant_id, idempotency_key)
);

create table lock_replay_results (
  tenant_id uuid not null,
  replay_id varchar(64) primary key,
  lock_id varchar(64) not null,
  input_hash varchar(128) not null,
  config_graph_hash varchar(128) not null,
  event_sequence_hash varchar(128) not null,
  expected_result_hash varchar(128) not null,
  actual_result_hash varchar(128) not null,
  mismatch_class varchar(80) not null,
  evidence_hash varchar(128) not null,
  idempotency_key varchar(160) not null,
  correlation_id varchar(128) not null,
  replayed_at timestamptz not null,
  constraint lock_replay_results_tenant_replay unique (tenant_id, replay_id),
  constraint lock_replay_results_idempotency unique (tenant_id, idempotency_key)
);

create table lock_cancellations (
  tenant_id uuid not null,
  cancellation_id varchar(64) primary key,
  lock_id varchar(64) not null,
  reason_code varchar(128) not null,
  cancelled_by varchar(128) not null,
  cancelled_at timestamptz not null,
  policy_version_id varchar(128) not null,
  external_notify_required boolean not null,
  evidence_hash varchar(128) not null,
  idempotency_key varchar(160) not null,
  correlation_id varchar(128) not null,
  constraint lock_cancellations_tenant_cancel unique (tenant_id, cancellation_id),
  constraint lock_cancellations_idempotency unique (tenant_id, idempotency_key)
);

create table lock_evidence_exports (
  tenant_id uuid not null,
  export_id varchar(64) primary key,
  report_id varchar(64) not null,
  actor_id varchar(128) not null,
  actor_refs jsonb not null,
  purpose_code varchar(128) not null,
  redacted_by_default boolean not null,
  manifest_hash varchar(128) not null,
  event_ids jsonb not null,
  schema_versions jsonb not null,
  config_versions jsonb not null,
  snapshot_hashes jsonb not null,
  generated_file_hashes jsonb not null,
  idempotency_key varchar(160) not null,
  correlation_id varchar(128) not null,
  generated_at timestamptz not null,
  constraint lock_evidence_exports_tenant_export unique (tenant_id, export_id),
  constraint lock_evidence_exports_idempotency unique (tenant_id, idempotency_key)
);

create index lock_audit_reports_status_idx on lock_audit_reports (tenant_id, status, generated_at desc);
create index lock_replay_results_lock_idx on lock_replay_results (tenant_id, lock_id, replayed_at desc);
create index lock_cancellations_lock_idx on lock_cancellations (tenant_id, lock_id, cancelled_at desc);
create index lock_evidence_exports_report_idx on lock_evidence_exports (tenant_id, report_id, generated_at desc);
