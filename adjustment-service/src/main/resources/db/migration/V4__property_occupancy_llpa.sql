create table if not exists property_occupancy_llpa_rules (
    tenant_id uuid not null,
    rule_book_id uuid not null,
    rule_id uuid not null,
    rule_book_version varchar(40) not null,
    product_id varchar(80) not null,
    investor_id varchar(80) not null,
    channel varchar(80) not null,
    occupancy_code varchar(80) not null,
    property_type_code varchar(80) not null,
    unit_min int not null,
    unit_max int not null,
    project_type_code varchar(80),
    manufactured_housing_flag boolean,
    state_code varchar(20),
    county_code varchar(80),
    ltv_metric varchar(20),
    ltv_band_key varchar(120),
    ltv_min numeric(12, 6),
    ltv_max numeric(12, 6),
    boundary_policy varchar(40) not null default 'MIN_INCLUSIVE_MAX_INCLUSIVE',
    loan_amount_band_key varchar(120),
    loan_amount_min numeric(14, 2),
    loan_amount_max numeric(14, 2),
    first_time_homebuyer_flag boolean,
    qualifiers jsonb not null default '[]'::jsonb,
    points_delta numeric(12, 6) not null,
    reason_code varchar(120) not null,
    priority int not null,
    exclusivity_group varchar(120),
    effective_start timestamptz not null,
    effective_end timestamptz,
    content_hash varchar(128) not null,
    enabled boolean not null default true,
    created_at timestamptz not null,
    primary key (tenant_id, rule_book_id, rule_id),
    constraint property_occupancy_units_check check (unit_min >= 1 and unit_max >= unit_min),
    constraint property_occupancy_ltv_check check (ltv_min is null or ltv_max is null or ltv_min <= ltv_max),
    constraint property_occupancy_loan_amount_check check (loan_amount_min is null or loan_amount_max is null or loan_amount_min <= loan_amount_max),
    constraint property_occupancy_metric_check check (ltv_metric is null or ltv_metric in ('LTV', 'CLTV', 'HCLTV')),
    constraint property_occupancy_boundary_check check (boundary_policy in ('MIN_INCLUSIVE_MAX_INCLUSIVE', 'MIN_EXCLUSIVE_MAX_INCLUSIVE'))
);

create index if not exists idx_property_occupancy_llpa_selector
    on property_occupancy_llpa_rules (
        tenant_id,
        product_id,
        investor_id,
        channel,
        rule_book_version,
        occupancy_code,
        property_type_code,
        priority
    );

create unique index if not exists idx_property_occupancy_llpa_content
    on property_occupancy_llpa_rules (tenant_id, rule_book_id, rule_id, content_hash);
