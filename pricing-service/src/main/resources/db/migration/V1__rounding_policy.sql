create table if not exists rounding_policy_version (
    tenant_id varchar(64) not null,
    policy_version_id varchar(80) primary key,
    version_number int not null,
    status varchar(40) not null,
    scope varchar(120) not null,
    product_code varchar(80),
    investor_code varchar(80),
    channel_code varchar(80),
    effective_from date not null,
    effective_to date,
    schema_version int not null,
    created_by varchar(128) not null,
    approved_by varchar(128),
    approved_at timestamptz,
    audit_reference varchar(160) not null,
    correlation_id varchar(128) not null,
    validation_passed boolean not null default false,
    created_at timestamptz not null default current_timestamp,
    updated_at timestamptz not null default current_timestamp,
    constraint rounding_policy_effective_window_ck check (effective_to is null or effective_to > effective_from)
);

create table if not exists rounding_rule (
    tenant_id varchar(64) not null,
    rule_id varchar(80) primary key,
    policy_version_id varchar(80) not null references rounding_policy_version(policy_version_id),
    output_context varchar(120) not null,
    unit varchar(40) not null,
    scale int not null,
    rounding_mode varchar(40) not null,
    increment numeric(18,8) not null,
    precedence int not null,
    reason_code varchar(120),
    constraint rounding_rule_scale_ck check (scale >= 0),
    constraint rounding_rule_increment_ck check (increment > 0),
    constraint rounding_rule_precedence_ck check (precedence >= 0)
);

create table if not exists rounding_sample_fixture (
    tenant_id varchar(64) not null,
    fixture_id varchar(80) primary key,
    policy_version_id varchar(80) not null references rounding_policy_version(policy_version_id),
    fixture_name varchar(160) not null,
    output_context varchar(120) not null,
    input_value numeric(18,8) not null,
    expected_value numeric(18,8) not null
);

create index if not exists rounding_policy_version_effective_idx
    on rounding_policy_version (tenant_id, status, effective_from, effective_to);

create index if not exists rounding_rule_context_idx
    on rounding_rule (tenant_id, policy_version_id, output_context);

create unique index if not exists rounding_rule_context_precedence_uq
    on rounding_rule (tenant_id, policy_version_id, output_context, precedence);
