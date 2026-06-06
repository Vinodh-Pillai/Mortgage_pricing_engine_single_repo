create table quote_snapshot (
    tenant_id uuid not null,
    snapshot_id uuid primary key,
    quote_id uuid not null references quote (quote_id),
    quote_version integer not null,
    manifest_version varchar(80) not null,
    canonical_request jsonb not null,
    canonical_response jsonb not null,
    input_version_set jsonb not null,
    output_digest varchar(128) not null,
    replay_hash varchar(128) not null,
    evidence_refs jsonb not null,
    redaction_profile varchar(128) not null,
    created_at timestamptz not null,
    audit_ref varchar(160) not null,
    correlation_id varchar(128) not null,
    constraint uq_quote_snapshot_tenant_quote unique (tenant_id, quote_id)
);

create index ix_quote_snapshot_tenant_replay_hash on quote_snapshot (tenant_id, replay_hash);
