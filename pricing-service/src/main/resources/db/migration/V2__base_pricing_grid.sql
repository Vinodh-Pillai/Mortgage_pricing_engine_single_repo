create table if not exists base_pricing_grid_version (
    id uuid primary key default gen_random_uuid(),
    tenant_id varchar(64) not null,
    product_code varchar(80),
    investor_code varchar(80),
    channel_code varchar(80),
    version_number int not null,
    status varchar(40) not null default 'DRAFT',
    effective_from timestamptz not null,
    effective_to timestamptz,
    source_digest varchar(256),
    approved_by varchar(128),
    approved_at timestamptz,
    created_at timestamptz not null default current_timestamp,
    updated_at timestamptz not null default current_timestamp,
    constraint base_pricing_grid_version_status_ck check (status in ('DRAFT', 'PUBLISHED', 'SUSPENDED')),
    constraint base_pricing_grid_version_effective_ck check (effective_to is null or effective_to > effective_from)
);

create table if not exists base_pricing_grid_row (
    id uuid primary key default gen_random_uuid(),
    tenant_id varchar(64) not null,
    grid_version_id uuid not null references base_pricing_grid_version(id),
    lock_period_days int not null,
    note_rate numeric(9,5) not null,
    base_price numeric(9,5) not null,
    bucket_key jsonb,
    row_hash varchar(128),
    created_at timestamptz not null default current_timestamp,
    constraint base_pricing_grid_row_lock_period_ck check (lock_period_days > 0),
    constraint base_pricing_grid_row_note_rate_ck check (note_rate >= 0),
    constraint base_pricing_grid_row_base_price_ck check (base_price >= 0)
);

create table if not exists base_pricing_grid_import (
    id uuid primary key default gen_random_uuid(),
    tenant_id varchar(64) not null,
    grid_version_id uuid not null references base_pricing_grid_version(id),
    source_type varchar(80) not null,
    source_digest varchar(256) not null,
    status varchar(40) not null,
    validation_summary jsonb not null default '[]'::jsonb,
    created_by varchar(128) not null,
    created_at timestamptz not null default current_timestamp,
    updated_at timestamptz not null default current_timestamp,
    constraint base_pricing_grid_import_status_ck check (status in ('DRAFT', 'VALIDATED', 'VALIDATION_FAILED', 'PUBLISHED'))
);

create table if not exists base_pricing_grid_event (
    id uuid primary key default gen_random_uuid(),
    tenant_id varchar(64) not null,
    grid_version_id uuid not null,
    event_type varchar(120) not null,
    actor_id varchar(128) not null,
    correlation_id varchar(128) not null,
    idempotency_key varchar(160),
    payload jsonb not null default '{}'::jsonb,
    occurred_at timestamptz not null default current_timestamp
);

create table if not exists base_rate_selection_audit (
    id uuid primary key default gen_random_uuid(),
    tenant_id varchar(64) not null,
    selection_id uuid not null,
    grid_version_id uuid not null,
    request_hash varchar(128) not null,
    response_hash varchar(128) not null,
    rounding_policy_ref varchar(128),
    actor_id varchar(128) not null,
    correlation_id varchar(128) not null,
    causation_id varchar(128),
    occurred_at timestamptz not null default current_timestamp
);

create unique index if not exists base_pricing_grid_version_uniq
    on base_pricing_grid_version (tenant_id, product_code, investor_code, channel_code, version_number);

create index if not exists base_pricing_grid_version_lookup_idx
    on base_pricing_grid_version (tenant_id, status, effective_from);

create index if not exists base_pricing_grid_version_scope_lookup_idx
    on base_pricing_grid_version (tenant_id, product_code, investor_code, channel_code, status, effective_from, effective_to);

create index if not exists base_pricing_grid_row_lookup_idx
    on base_pricing_grid_row (tenant_id, grid_version_id, lock_period_days, note_rate);

create unique index if not exists base_pricing_grid_row_key_uniq
    on base_pricing_grid_row (tenant_id, grid_version_id, lock_period_days, note_rate, coalesce(bucket_key::text, '{}'));

create index if not exists base_pricing_grid_import_version_idx
    on base_pricing_grid_import (tenant_id, grid_version_id, status);

create index if not exists base_pricing_grid_event_version_idx
    on base_pricing_grid_event (tenant_id, grid_version_id, occurred_at desc);

create index if not exists base_rate_selection_audit_selection_idx
    on base_rate_selection_audit (tenant_id, selection_id);

create index if not exists base_rate_selection_audit_occurred_idx
    on base_rate_selection_audit (tenant_id, occurred_at desc);
