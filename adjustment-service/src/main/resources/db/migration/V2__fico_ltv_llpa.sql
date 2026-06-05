create table fico_ltv_llpa_grid_cells (
    tenant_id uuid not null,
    rule_book_id uuid not null,
    rule_id uuid not null,
    rule_book_version varchar(40) not null,
    rule_book_hash char(64) not null,
    product_id varchar(80) not null,
    investor_id varchar(80) not null,
    channel varchar(80) not null,
    fico_band_key varchar(80) not null,
    fico_min int not null,
    fico_max int not null,
    ltv_metric varchar(16) not null,
    ltv_band_key varchar(80) not null,
    ltv_min numeric(12,6) not null,
    ltv_max numeric(12,6) not null,
    boundary_policy varchar(40) not null,
    points_delta numeric(12,6) not null,
    reason_code varchar(64) not null,
    priority int not null,
    effective_start timestamptz not null,
    effective_end timestamptz,
    content_hash char(64) not null,
    enabled boolean not null default true,
    created_at timestamptz not null,
    constraint fico_ltv_llpa_fico_range_check check (fico_min <= fico_max),
    constraint fico_ltv_llpa_ltv_range_check check (ltv_min <= ltv_max),
    constraint fico_ltv_llpa_metric_check check (ltv_metric in ('LTV', 'CLTV', 'HCLTV')),
    constraint fico_ltv_llpa_boundary_check check (boundary_policy in ('MIN_INCLUSIVE_MAX_INCLUSIVE', 'MIN_EXCLUSIVE_MAX_INCLUSIVE'))
);

create index fico_ltv_llpa_grid_lookup_idx
    on fico_ltv_llpa_grid_cells (tenant_id, product_id, investor_id, channel, rule_book_version, effective_start, effective_end);

create unique index fico_ltv_llpa_grid_content_uidx
    on fico_ltv_llpa_grid_cells (tenant_id, rule_book_id, rule_id, content_hash);

create table quote_adjustment_ledger (
    tenant_id uuid not null,
    quote_id varchar(80) not null,
    adjustment_id uuid primary key,
    category varchar(40) not null,
    rule_type varchar(40) not null,
    input_values jsonb not null,
    points_delta numeric(12,6) not null,
    bps_delta numeric(12,4) not null,
    rounding_mode varchar(24) not null,
    rule_book_version varchar(40),
    content_hash char(64) not null,
    waterfall_sequence int not null,
    created_at timestamptz not null
);

create index quote_adjustment_ledger_quote_idx
    on quote_adjustment_ledger (tenant_id, quote_id, waterfall_sequence);
