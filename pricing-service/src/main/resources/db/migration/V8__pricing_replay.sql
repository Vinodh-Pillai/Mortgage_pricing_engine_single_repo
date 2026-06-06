create table if not exists pricing_calculation_snapshot (
    tenant_id varchar(64) not null,
    source_type varchar(40) not null,
    source_id varchar(120) not null,
    scenario_hash varchar(160),
    version_graph_hash varchar(160) not null,
    selected_row_hash varchar(160),
    rounding_policy_ref varchar(160),
    canonical_input jsonb not null default '{}'::jsonb,
    result_values jsonb not null default '{}'::jsonb,
    ledger jsonb not null default '[]'::jsonb,
    result_hash varchar(160) not null,
    ledger_hash varchar(160) not null,
    schema_supported boolean not null default true,
    created_at timestamptz not null default current_timestamp,
    primary key (tenant_id, source_type, source_id)
);

create table if not exists pricing_replay_run (
    id uuid primary key,
    tenant_id varchar(64) not null,
    source_type varchar(40) not null,
    source_id varchar(120) not null,
    mode varchar(40) not null,
    status varchar(40) not null,
    original_hash varchar(160) not null,
    replay_hash varchar(160) not null,
    ledger_hash varchar(160) not null,
    mismatch_class varchar(80),
    evidence_ref varchar(180) not null,
    actor varchar(128) not null,
    correlation_id varchar(128) not null,
    started_at timestamptz not null,
    completed_at timestamptz not null,
    check (status in ('QUEUED', 'RUNNING', 'MATCHED', 'MISMATCHED', 'FAILED'))
);

create table if not exists pricing_replay_diff (
    id uuid primary key default gen_random_uuid(),
    replay_run_id uuid not null references pricing_replay_run(id),
    tenant_id varchar(64) not null,
    path varchar(240) not null,
    original_value_redacted varchar(400),
    replay_value_redacted varchar(400),
    classification varchar(80) not null,
    severity varchar(40) not null,
    created_at timestamptz not null default current_timestamp
);

create table if not exists pricing_replay_event (
    id uuid primary key default gen_random_uuid(),
    tenant_id varchar(64) not null,
    replay_run_id uuid not null,
    event_type varchar(120) not null,
    actor_id varchar(128) not null,
    correlation_id varchar(128) not null,
    idempotency_key varchar(160),
    payload jsonb not null default '{}'::jsonb,
    occurred_at timestamptz not null default current_timestamp
);

create table if not exists pricing_replay_audit (
    id uuid primary key default gen_random_uuid(),
    tenant_id varchar(64) not null,
    replay_run_id uuid not null,
    action varchar(120) not null,
    actor_id varchar(128) not null,
    correlation_id varchar(128) not null,
    version_graph_hash varchar(160),
    replay_hash varchar(160) not null,
    evidence_ref varchar(180) not null,
    occurred_at timestamptz not null default current_timestamp
);

create index if not exists pricing_replay_run_source_idx
    on pricing_replay_run (tenant_id, source_type, source_id);

create index if not exists pricing_replay_run_status_idx
    on pricing_replay_run (tenant_id, status, completed_at desc);

create index if not exists pricing_replay_run_mismatch_idx
    on pricing_replay_run (tenant_id, mismatch_class, completed_at desc);

create index if not exists pricing_replay_diff_run_idx
    on pricing_replay_diff (tenant_id, replay_run_id);
