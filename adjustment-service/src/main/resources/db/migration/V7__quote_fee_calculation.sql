create table if not exists quote_fee_calculations (
    tenant_id uuid not null,
    fee_calculation_id uuid primary key,
    quote_id varchar(128) not null,
    scenario_id varchar(128) not null,
    catalog_version_id uuid not null,
    input_snapshot_hash varchar(128) not null,
    status varchar(40) not null,
    total_borrower_paid numeric(18,2) not null,
    total_lender_paid numeric(18,2) not null,
    total_third_party numeric(18,2) not null,
    request_json jsonb not null,
    result_json jsonb not null default '{}'::jsonb,
    created_by varchar(128) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    correlation_id varchar(128) not null,
    idempotency_key varchar(160),
    constraint uq_quote_fee_calculations_tenant_id unique (tenant_id, fee_calculation_id)
);

create unique index if not exists uq_quote_fee_calculations_idempotency
    on quote_fee_calculations (tenant_id, idempotency_key)
    where idempotency_key is not null;

create index if not exists ix_quote_fee_calculations_quote_scenario
    on quote_fee_calculations (tenant_id, quote_id, scenario_id);

create table if not exists quote_fee_lines (
    tenant_id uuid not null,
    fee_line_id uuid primary key,
    fee_calculation_id uuid not null references quote_fee_calculations(fee_calculation_id),
    fee_code varchar(128) not null,
    fee_definition_id uuid not null,
    category varchar(128) not null,
    payer varchar(128) not null,
    payee varchar(128) not null,
    calculation_method varchar(64) not null,
    formula_inputs jsonb not null default '{}'::jsonb,
    raw_amount numeric(18,6) not null,
    rounded_amount numeric(18,2) not null,
    rounding_mode varchar(40) not null,
    cap_floor_result jsonb not null default '{}'::jsonb,
    reason_code varchar(128) not null,
    catalog_content_hash varchar(128) not null,
    waterfall_sequence int not null,
    line_hash varchar(128) not null,
    constraint uq_quote_fee_lines_tenant_id unique (tenant_id, fee_line_id)
);

create index if not exists ix_quote_fee_lines_calculation
    on quote_fee_lines (tenant_id, fee_calculation_id);
