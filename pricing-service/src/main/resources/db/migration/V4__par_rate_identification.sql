create table if not exists pricing_par_policy_version (
    tenant_id varchar(80) not null,
    policy_version_id varchar(80) primary key,
    product_code varchar(80) not null,
    investor_code varchar(80) not null,
    channel_code varchar(80) not null,
    status varchar(40) not null,
    effective_from timestamptz not null,
    effective_to timestamptz,
    target_definition_json text not null,
    comparator varchar(80) not null,
    tie_breaker varchar(80),
    price_basis varchar(20) not null,
    approval_metadata_json text not null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index if not exists idx_par_policy_version_scope
    on pricing_par_policy_version (tenant_id, product_code, investor_code, channel_code, status, effective_from);

create table if not exists pricing_par_rate_identification_result (
    tenant_id varchar(80) not null,
    par_identification_id uuid primary key,
    grid_version_id uuid not null,
    lock_period_days integer not null,
    par_policy_version_id varchar(80) not null,
    par_note_rate numeric(9,5) not null,
    par_price numeric(9,5) not null,
    result_hash varchar(128) not null,
    ledger_json text not null,
    actor_id varchar(128) not null,
    correlation_id varchar(128) not null,
    created_at timestamptz not null
);

create index if not exists idx_par_result_grid_lock_hash
    on pricing_par_rate_identification_result (tenant_id, grid_version_id, lock_period_days, result_hash);
