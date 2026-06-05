create table quote (
    tenant_id uuid not null,
    quote_id uuid primary key,
    scenario_id uuid not null,
    scenario_version integer not null,
    status varchar(40) not null,
    ranking_policy_id varchar(128) not null,
    ranking_policy_version varchar(128) not null,
    input_version_set jsonb not null,
    requested_filters jsonb not null,
    expires_at timestamptz not null,
    replay_hash varchar(128) not null,
    idempotency_key varchar(160) not null,
    created_by varchar(128) not null,
    created_at timestamptz not null,
    correlation_id varchar(128) not null,
    version integer not null default 1,
    constraint uq_quote_tenant_idempotency unique (tenant_id, idempotency_key),
    constraint ck_quote_status check (status in ('REQUESTED', 'READY', 'NO_OPTIONS', 'FAILED', 'EXPIRED'))
);

create index ix_quote_tenant_scenario_created on quote (tenant_id, scenario_id, created_at desc);

create table quote_option (
    tenant_id uuid not null,
    option_id uuid primary key,
    quote_id uuid not null references quote (quote_id),
    product_id varchar(128) not null,
    investor_id varchar(128) not null,
    channel varchar(80) not null,
    lock_period_days integer not null,
    note_rate_pct numeric(9,5) not null,
    final_price_bps numeric(12,4) not null,
    total_adjustment_bps numeric(12,4) not null,
    margin_bps numeric(12,4) not null,
    waterfall jsonb not null,
    rank integer not null,
    rank_score numeric(18,8) not null,
    rank_reasons jsonb not null,
    upstream_refs jsonb not null,
    constraint uq_quote_option_rank unique (tenant_id, quote_id, rank)
);

create index ix_quote_option_tenant_quote on quote_option (tenant_id, quote_id);

create table comparison_view_config (
    tenant_id uuid not null,
    view_id varchar(128) not null,
    view_version varchar(80) not null,
    columns_json jsonb not null,
    visibility_predicates_json jsonb not null,
    sort_policy varchar(128),
    max_compare_count integer not null,
    effective_from timestamptz not null,
    effective_to timestamptz,
    approval_ref varchar(160) not null,
    constraint pk_comparison_view_config primary key (tenant_id, view_id, view_version)
);

create table quote_comparison_export (
    tenant_id uuid not null,
    export_id uuid primary key,
    quote_id uuid not null references quote (quote_id),
    view_id varchar(128) not null,
    view_version varchar(80) not null,
    row_option_ids uuid[] not null,
    format varchar(40) not null,
    redaction_profile varchar(128) not null,
    storage_ref varchar(256) not null,
    created_by varchar(128) not null,
    created_at timestamptz not null,
    idempotency_key varchar(160) not null,
    constraint uq_quote_comparison_export_idempotency unique (tenant_id, idempotency_key)
);

create index ix_quote_comparison_export_tenant_quote on quote_comparison_export (tenant_id, quote_id);
