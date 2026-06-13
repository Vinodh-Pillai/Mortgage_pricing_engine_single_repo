create table if not exists fico_ltv_grid_cell (
    grid_cell_id uuid primary key,
    tenant_id uuid not null,
    rule_book_id uuid not null,
    rule_id uuid not null,
    rule_book_version varchar(32) not null,
    rule_book_hash varchar(64) not null,
    product_code varchar(64) not null,
    investor_code varchar(32) not null,
    channel_code varchar(32) not null,
    fico_band_key varchar(32) not null,
    fico_min int not null,
    fico_max int not null,
    ltv_metric varchar(16) not null,
    ltv_band_key varchar(32) not null,
    ltv_min numeric(5,2) not null,
    ltv_max numeric(5,2) not null,
    boundary_policy varchar(32) not null,
    points_delta numeric(10,6) not null,
    reason_code varchar(96) not null,
    priority int not null,
    effective_start timestamptz not null,
    effective_end timestamptz,
    content_hash varchar(64) not null,
    enabled boolean not null default true,
    created_at timestamptz not null default now(),
    constraint fico_ltv_grid_cell_fico_check check (fico_min <= fico_max),
    constraint fico_ltv_grid_cell_ltv_check check (ltv_min <= ltv_max)
);

create index if not exists idx_fico_ltv_grid_lookup on fico_ltv_grid_cell
    (tenant_id, investor_code, channel_code, product_code, effective_start, effective_end);

create index if not exists idx_fico_ltv_grid_version on fico_ltv_grid_cell
    (investor_code, rule_book_version, enabled);

create table if not exists cash_out_llpa_rule (
    cash_out_rule_id uuid primary key,
    tenant_id uuid not null,
    rule_book_id uuid not null,
    rule_id uuid not null,
    rule_book_version varchar(32) not null,
    rule_book_hash varchar(64) not null,
    product_code varchar(64) not null,
    investor_code varchar(32) not null,
    channel_code varchar(32) not null,
    classification_code varchar(80) not null,
    ltv_metric varchar(16) not null,
    ltv_band_key varchar(32) not null,
    ltv_min numeric(5,2) not null,
    ltv_max numeric(5,2) not null,
    boundary_policy varchar(32) not null,
    loan_amount_band_key varchar(64) not null,
    loan_amount_min numeric(14,2) not null,
    loan_amount_max numeric(14,2) not null,
    occupancy_code varchar(80),
    property_type_code varchar(80),
    state_code varchar(20),
    points_delta numeric(10,6) not null,
    reason_code varchar(96) not null,
    priority int not null,
    effective_start timestamptz not null,
    effective_end timestamptz,
    content_hash varchar(64) not null,
    enabled boolean not null default true,
    created_at timestamptz not null default now(),
    constraint cash_out_llpa_rule_ltv_check check (ltv_min <= ltv_max),
    constraint cash_out_llpa_rule_amount_check check (loan_amount_min <= loan_amount_max)
);

create index if not exists idx_cash_out_llpa_rule_lookup on cash_out_llpa_rule
    (tenant_id, investor_code, channel_code, product_code, classification_code, effective_start, effective_end);

create table if not exists property_occupancy_llpa_rule (
    property_occupancy_rule_id uuid primary key,
    tenant_id uuid not null,
    rule_book_id uuid not null,
    rule_id uuid not null,
    rule_book_version varchar(32) not null,
    rule_book_hash varchar(64) not null,
    product_code varchar(64) not null,
    investor_code varchar(32) not null,
    channel_code varchar(32) not null,
    occupancy_code varchar(80) not null,
    property_type_code varchar(80) not null,
    unit_min int not null,
    unit_max int not null,
    project_type_code varchar(80),
    manufactured_housing_flag boolean,
    state_code varchar(20),
    county_code varchar(80),
    ltv_metric varchar(16),
    ltv_band_key varchar(32),
    ltv_min numeric(5,2),
    ltv_max numeric(5,2),
    boundary_policy varchar(32) not null,
    loan_amount_band_key varchar(64),
    loan_amount_min numeric(14,2),
    loan_amount_max numeric(14,2),
    first_time_homebuyer_flag boolean,
    qualifiers text not null default '',
    points_delta numeric(10,6) not null,
    reason_code varchar(96) not null,
    priority int not null,
    exclusivity_group varchar(120),
    effective_start timestamptz not null,
    effective_end timestamptz,
    content_hash varchar(64) not null,
    enabled boolean not null default true,
    created_at timestamptz not null default now(),
    constraint property_occupancy_llpa_rule_units_check check (unit_min >= 1 and unit_max >= unit_min),
    constraint property_occupancy_llpa_rule_ltv_check check (ltv_min is null or ltv_max is null or ltv_min <= ltv_max),
    constraint property_occupancy_llpa_rule_amount_check check (loan_amount_min is null or loan_amount_max is null or loan_amount_min <= loan_amount_max)
);

create index if not exists idx_property_occupancy_llpa_rule_lookup on property_occupancy_llpa_rule
    (tenant_id, investor_code, channel_code, product_code, occupancy_code, property_type_code, effective_start, effective_end);

create table if not exists gse_grid_load_audit (
    grid_load_id uuid primary key,
    investor_code varchar(32) not null,
    rule_book_version varchar(32) not null,
    rule_book_hash varchar(64),
    source_url varchar(512) not null,
    status varchar(24) not null,
    fico_ltv_cell_count int not null default 0,
    cash_out_rule_count int not null default 0,
    property_occupancy_rule_count int not null default 0,
    warning_count int not null default 0,
    error_message varchar(512),
    loaded_at timestamptz not null default now()
);

create index if not exists idx_gse_grid_load_audit_latest on gse_grid_load_audit
    (investor_code, loaded_at desc);
