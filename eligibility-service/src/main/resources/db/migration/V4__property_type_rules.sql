-- PII-03-S05: Property type rules tables
-- Tenant-governed property type policy with versioning and audit

create table if not exists eligibility.property_type_rule_set (
    tenant_id uuid not null,
    rule_set_id uuid primary key default gen_random_uuid(),
    product_family varchar(64),
    product_code varchar(64),
    investor_code varchar(64),
    channel varchar(64),
    status varchar(32) not null default 'DRAFT' check (status in ('DRAFT', 'PUBLISHED', 'SUPERSEDED', 'RETRACTED')),
    effective_from date not null default current_date,
    effective_to date,
    version int not null default 1,
    created_by varchar(128) not null default 'system',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (tenant_id, version)
);

create table if not exists eligibility.property_type_rule (
    tenant_id uuid not null,
    rule_id uuid primary key default gen_random_uuid(),
    rule_set_id uuid not null references eligibility.property_type_rule_set(rule_set_id) on delete cascade,
    property_type varchar(32) not null check (property_type in ('SINGLE_FAMILY', 'CONDO', 'PUD', 'TWO_TO_FOUR_UNIT', 'MANUFACTURED_HOME')),
    units_min int not null check (units_min >= 1 and units_min <= 4),
    units_max int not null check (units_max >= 1 and units_max <= 4 and units_max >= units_min),
    occupancy_type varchar(32),
    loan_purpose varchar(32) not null default 'PURCHASE',
    project_review_requirement varchar(32) default 'NONE' check (project_review_requirement in ('NONE', 'WARNING', 'REQUIRED')),
    decision varchar(32) not null check (decision in ('ALLOW', 'DENY', 'CONDITION')),
    severity varchar(32),
    reason_code varchar(64),
    message_template text,
    priority int not null default 100,
    row_hash varchar(128),
    check (loan_purpose is null or loan_purpose in ('PURCHASE', 'REFINANCE', 'CASH_OUT'))
);

create index if not exists property_type_rule_lookup_idx on eligibility.property_type_rule (
    tenant_id, rule_set_id, property_type, units_min, units_max
);

create table if not exists eligibility.eligibility_decision_property_type (
    tenant_id uuid not null,
    decision_id uuid primary key default gen_random_uuid(),
    scenario_id uuid not null,
    property_type varchar(32) not null,
    units int not null,
    occupancy_type varchar(32),
    loan_purpose varchar(32),
    project_review_status varchar(32),
    matched_rule_id uuid,
    rule_set_id uuid,
    eligibility_status varchar(32) not null,
    project_review_requirement varchar(32),
    created_at timestamptz not null default now()
);

create index if not exists property_type_decision_scenario_idx on eligibility.eligibility_decision_property_type (
    tenant_id, scenario_id, created_at desc
);

create index if not exists property_type_decision_status_idx on eligibility.eligibility_decision_property_type (
    tenant_id, eligibility_status, created_at desc
);

-- Outbox event for rule set publication
create table if not exists eligibility.property_type_outbox_event (
    event_id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null,
    event_type varchar(128) not null default 'PropertyTypeRuleSetPublished.v1',
    event_version varchar(16) not null default '1',
    payload jsonb not null,
    status varchar(32) not null default 'PENDING' check (status in ('PENDING', 'PUBLISHED', 'FAILED')),
    created_at timestamptz not null default now(),
    published_at timestamptz
);

create index if not exists property_type_outbox_status_idx on eligibility.property_type_outbox_event (
    tenant_id, status, created_at desc
);

-- Metrics counter table for Prometheus scrape
create table if not exists eligibility.property_type_metrics (
    tenant_id uuid not null,
    metric_name varchar(64) not null,
    status varchar(32),
    reason_code varchar(64),
    value bigint not null default 1,
    recorded_at timestamptz not null default now()
);

create index if not exists property_type_metrics_idx on eligibility.property_type_metrics (
    tenant_id, metric_name, recorded_at desc
);
