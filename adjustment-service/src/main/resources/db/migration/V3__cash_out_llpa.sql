create table if not exists cash_out_llpa_rules (
    tenant_id uuid not null,
    rule_book_id uuid not null,
    rule_id uuid not null,
    classification_code varchar(80) not null,
    ltv_metric varchar(20) not null,
    ltv_band_key varchar(120) not null,
    loan_amount_band_key varchar(120) not null,
    occupancy_code varchar(80),
    property_type_code varchar(80),
    state_code varchar(20),
    points_delta numeric(12, 6) not null,
    reason_code varchar(120) not null,
    priority int not null,
    effective_start timestamptz not null,
    effective_end timestamptz,
    content_hash varchar(128) not null,
    primary key (tenant_id, rule_book_id, rule_id)
);

create index if not exists idx_cash_out_llpa_rules_selector
    on cash_out_llpa_rules (tenant_id, rule_book_id, classification_code, ltv_metric, loan_amount_band_key, priority);
