create table if not exists adjustment_conflict_policy_sets (
    tenant_id uuid not null,
    policy_set_id uuid primary key,
    version integer not null,
    status varchar(40) not null,
    effective_start timestamptz not null,
    effective_end timestamptz,
    selectors jsonb not null default '{}'::jsonb,
    content_hash varchar(128) not null,
    created_by varchar(128) not null,
    approved_by varchar(128),
    published_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uq_adjustment_conflict_policy_sets_tenant_version unique (tenant_id, policy_set_id, version)
);

create index if not exists idx_adjustment_conflict_policy_sets_effective
    on adjustment_conflict_policy_sets (tenant_id, status, effective_start, effective_end);

create table if not exists adjustment_conflict_policies (
    tenant_id uuid not null,
    policy_id uuid primary key,
    policy_set_id uuid not null references adjustment_conflict_policy_sets(policy_set_id),
    policy_code varchar(160) not null,
    severity varchar(40) not null,
    conditions jsonb not null default '[]'::jsonb,
    included_categories jsonb not null default '[]'::jsonb,
    strategy varchar(80) not null,
    formula_parameters jsonb not null default '{}'::jsonb,
    remediation_message varchar(1000) not null,
    reason_code varchar(160) not null,
    priority integer not null,
    enabled boolean not null default true,
    source_ref varchar(240) not null,
    constraint uq_adjustment_conflict_policies_code unique (tenant_id, policy_set_id, policy_code)
);

create index if not exists idx_adjustment_conflict_policies_policy_set
    on adjustment_conflict_policies (tenant_id, policy_set_id, enabled, priority);

create table if not exists quote_conflict_runs (
    tenant_id uuid not null,
    conflict_run_id uuid primary key,
    quote_id varchar(128) not null,
    scenario_id varchar(128) not null,
    policy_set_id uuid not null,
    input_snapshot_hash varchar(128) not null,
    status varchar(40) not null,
    correlation_id varchar(128) not null,
    created_at timestamptz not null,
    constraint uq_quote_conflict_runs_tenant_run unique (tenant_id, conflict_run_id)
);

create index if not exists idx_quote_conflict_runs_quote_status
    on quote_conflict_runs (tenant_id, quote_id, status, created_at desc);

create table if not exists quote_conflict_findings (
    tenant_id uuid not null,
    finding_id uuid primary key,
    conflict_run_id uuid not null references quote_conflict_runs(conflict_run_id),
    affected_line_refs jsonb not null,
    severity varchar(40) not null,
    strategy varchar(80) not null,
    computed_values jsonb not null default '{}'::jsonb,
    resolution_action varchar(80) not null,
    message varchar(1000) not null,
    reason_code varchar(160) not null,
    policy_hash varchar(128) not null,
    finding_hash varchar(128) not null,
    created_at timestamptz not null default now()
);

create index if not exists idx_quote_conflict_findings_run
    on quote_conflict_findings (tenant_id, conflict_run_id, severity);
