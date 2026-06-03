-- PII-03-S06: tenant-governed investor overlay rule evaluation

create table if not exists eligibility.investor_overlay_set (
    tenant_id uuid not null,
    overlay_set_id uuid primary key default gen_random_uuid(),
    investor_id uuid not null,
    product_version_id uuid,
    channel varchar(32),
    status varchar(32) not null default 'DRAFT' check (status in ('DRAFT', 'PUBLISHED', 'SUPERSEDED', 'SUSPENDED')),
    effective_from date not null default current_date,
    effective_to date,
    version int not null default 1,
    created_by varchar(128) not null default 'system',
    approved_by varchar(128),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (tenant_id, investor_id, product_version_id, channel, version)
);

create table if not exists eligibility.investor_overlay_rule (
    tenant_id uuid not null,
    overlay_rule_id uuid primary key default gen_random_uuid(),
    overlay_set_id uuid not null references eligibility.investor_overlay_set(overlay_set_id) on delete cascade,
    rule_code varchar(64) not null,
    fact_path varchar(128) not null,
    operator varchar(32) not null,
    comparison_value varchar(256),
    secondary_value varchar(256),
    value_type varchar(32) not null check (value_type in ('MONEY', 'PERCENT', 'INTEGER', 'ENUM', 'BOOLEAN')),
    condition_expression_json jsonb,
    severity varchar(32) not null,
    reason_code varchar(64) not null,
    message_template text,
    priority int not null default 100,
    row_hash varchar(128)
);

create index if not exists investor_overlay_rule_lookup_idx on eligibility.investor_overlay_rule (
    tenant_id, overlay_set_id, priority
);

create index if not exists investor_overlay_rule_code_idx on eligibility.investor_overlay_rule (
    tenant_id, rule_code
);

create table if not exists eligibility.eligibility_decision_investor_overlay (
    tenant_id uuid not null,
    decision_id uuid primary key default gen_random_uuid(),
    scenario_id uuid not null,
    evaluation_id uuid not null,
    overlay_rule_id uuid,
    rule_code varchar(64) not null,
    eligibility_status varchar(32) not null,
    severity varchar(32) not null,
    reason_code varchar(64),
    actual_value varchar(256),
    threshold_value varchar(512),
    rule_version_id uuid,
    result_hash varchar(128) not null,
    created_at timestamptz not null default now()
);

create index if not exists investor_overlay_decision_scenario_idx on eligibility.eligibility_decision_investor_overlay (
    tenant_id, scenario_id, created_at desc
);

create index if not exists investor_overlay_decision_status_idx on eligibility.eligibility_decision_investor_overlay (
    tenant_id, eligibility_status, reason_code, created_at desc
);
