create table if not exists quote_adjustment_summaries (
    tenant_id uuid not null,
    summary_id uuid primary key,
    quote_id varchar(128) not null,
    scenario_id varchar(128) not null,
    pricing_run_id varchar(128) not null,
    status varchar(40) not null,
    input_snapshot_hash varchar(128) not null,
    ledger_hash varchar(128) not null,
    total_points numeric(12,6) not null,
    total_bps numeric(12,4) not null,
    total_money jsonb not null default '{}'::jsonb,
    final_adjusted_price numeric(12,6) not null,
    generated_at timestamptz not null,
    expires_at timestamptz,
    correlation_id varchar(128) not null,
    unique (tenant_id, quote_id, scenario_id, pricing_run_id)
);

create table if not exists quote_adjustment_summary_lines (
    tenant_id uuid not null,
    summary_line_id uuid primary key,
    summary_id uuid not null references quote_adjustment_summaries(summary_id),
    source_ledger_line_id varchar(128) not null,
    sequence int not null,
    category varchar(80) not null,
    label varchar(200) not null,
    formula_display varchar(500) not null,
    source_inputs_redacted jsonb not null default '{}'::jsonb,
    points_delta numeric(12,6),
    bps_delta numeric(12,4),
    money_amount numeric(18,2),
    status varchar(40) not null,
    reason_code varchar(128) not null,
    config_ref jsonb not null default '{}'::jsonb,
    source_ref varchar(200) not null,
    suppression_reason varchar(300),
    unique (tenant_id, summary_id, source_ledger_line_id)
);

create index if not exists ix_quote_adjustment_summaries_quote on quote_adjustment_summaries (tenant_id, quote_id, scenario_id);
create index if not exists ix_quote_adjustment_summaries_pricing_run on quote_adjustment_summaries (tenant_id, pricing_run_id);
create index if not exists ix_quote_adjustment_summary_lines_sequence on quote_adjustment_summary_lines (tenant_id, summary_id, sequence);
