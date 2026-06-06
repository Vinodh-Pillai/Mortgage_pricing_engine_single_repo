-- PII-03-S04: tenant-governed occupancy and purchase-purpose rules.
-- This migration creates storage for versioned rule configuration, evaluation evidence,
-- outbox/audit events, and operational metrics without seeding tenant/product policy.

create table if not exists eligibility.occupancy_purpose_rule_set (
    tenant_id uuid not null,
    rule_set_id uuid primary key default gen_random_uuid(),
    product_family varchar(64),
    product_code varchar(64),
    product_version_id uuid,
    investor_code varchar(64),
    investor_id uuid,
    channel varchar(64),
    status varchar(32) not null default 'DRAFT' check (status in ('DRAFT', 'PUBLISHED', 'SUPERSEDED', 'SUSPENDED', 'RETRACTED')),
    effective_from date not null default current_date,
    effective_to date,
    version int not null default 1,
    created_by varchar(128) not null,
    approved_by varchar(128),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    check (effective_to is null or effective_to >= effective_from),
    unique (tenant_id, product_family, product_code, investor_code, channel, version)
);

create index if not exists occupancy_purpose_rule_set_effective_idx on eligibility.occupancy_purpose_rule_set (
    tenant_id, status, effective_from, effective_to
);

create table if not exists eligibility.occupancy_purpose_rule (
    tenant_id uuid not null,
    rule_id uuid primary key default gen_random_uuid(),
    rule_set_id uuid not null references eligibility.occupancy_purpose_rule_set(rule_set_id) on delete cascade,
    loan_purpose varchar(32) not null,
    occupancy_type varchar(32) not null,
    property_type varchar(32),
    units_min int not null default 1 check (units_min >= 1 and units_min <= 4),
    units_max int not null default 4 check (units_max >= 1 and units_max <= 4),
    channel varchar(64),
    aus_type varchar(32),
    documentation_type varchar(32),
    decision varchar(32) not null check (decision in ('ALLOW', 'DENY', 'WARNING')),
    severity varchar(32),
    reason_code varchar(64),
    message_template varchar(1000),
    priority int not null,
    row_hash char(64),
    check (units_min <= units_max),
    check (loan_purpose = 'PURCHASE')
);

create index if not exists occupancy_purpose_rule_lookup_idx on eligibility.occupancy_purpose_rule (
    tenant_id, rule_set_id, loan_purpose, occupancy_type, priority
);

create table if not exists eligibility.eligibility_decision_occupancy_purpose (
    tenant_id uuid not null,
    decision_id uuid not null,
    scenario_id uuid not null,
    scenario_version int not null,
    loan_purpose varchar(32),
    occupancy_type varchar(32),
    property_type varchar(32),
    units int,
    matched_rule_id uuid,
    rule_set_id uuid,
    eligibility_status varchar(32) not null,
    severity varchar(32),
    reason_code varchar(64),
    result_hash varchar(128) not null,
    created_at timestamptz not null default now(),
    primary key (tenant_id, decision_id)
);

create index if not exists occupancy_purpose_decision_scenario_idx on eligibility.eligibility_decision_occupancy_purpose (
    tenant_id, scenario_id, created_at desc
);

create table if not exists eligibility.occupancy_purpose_outbox_event (
    event_id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null,
    aggregate_id uuid not null,
    event_type varchar(128) not null,
    event_version varchar(16) not null default '1',
    payload jsonb not null,
    status varchar(32) not null default 'PENDING' check (status in ('PENDING', 'PUBLISHED', 'FAILED')),
    created_at timestamptz not null default now(),
    published_at timestamptz
);

create index if not exists occupancy_purpose_outbox_status_idx on eligibility.occupancy_purpose_outbox_event (
    tenant_id, status, created_at desc
);

create table if not exists eligibility.occupancy_purpose_metrics (
    tenant_id uuid not null,
    metric_name varchar(64) not null,
    status varchar(32),
    reason_code varchar(64),
    value bigint not null default 1,
    recorded_at timestamptz not null default now()
);

create index if not exists occupancy_purpose_metrics_idx on eligibility.occupancy_purpose_metrics (
    tenant_id, metric_name, recorded_at desc
);
