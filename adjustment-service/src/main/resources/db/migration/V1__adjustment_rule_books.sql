create table adjustment_rule_books (
    tenant_id uuid not null,
    rule_book_id uuid primary key,
    business_key varchar(80) not null,
    version varchar(40) not null,
    status varchar(24) not null,
    product_family varchar(40) not null,
    investor_selector jsonb not null,
    channel_selector jsonb not null,
    effective_start timestamptz not null,
    effective_end timestamptz null,
    precision_policy jsonb not null default '{}'::jsonb,
    content_hash char(64) not null,
    created_by varchar(128) not null,
    approved_by varchar(128),
    published_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint adjustment_rule_books_status_check
        check (status in ('DRAFT', 'VALIDATED', 'PENDING_APPROVAL', 'PUBLISHED', 'SUSPENDED', 'EXPIRED')),
    constraint adjustment_rule_books_published_requires_approval_check
        check (status <> 'PUBLISHED' or (approved_by is not null and published_at is not null))
);

create unique index adjustment_rule_books_tenant_rule_book_uidx
    on adjustment_rule_books (tenant_id, rule_book_id);

create index adjustment_rule_books_tenant_status_effective_idx
    on adjustment_rule_books (tenant_id, status, effective_start, effective_end);

create unique index adjustment_rule_books_one_open_published_uidx
    on adjustment_rule_books (tenant_id, business_key, product_family, investor_selector, channel_selector)
    where status = 'PUBLISHED' and effective_end is null;

create table adjustment_rules (
    tenant_id uuid not null,
    rule_id uuid primary key,
    rule_book_id uuid not null references adjustment_rule_books(rule_book_id),
    priority int not null,
    rule_type varchar(40) not null,
    conditions jsonb not null,
    output jsonb not null,
    reason_code varchar(64) not null,
    exclusivity_group varchar(80),
    enabled boolean not null default true,
    source_ref varchar(255) not null,
    created_at timestamptz not null,
    constraint adjustment_rules_priority_check check (priority >= 0)
);

create index adjustment_rules_tenant_rule_book_idx
    on adjustment_rules (tenant_id, rule_book_id);

create index adjustment_rules_conditions_gin_idx
    on adjustment_rules using gin (conditions);

create table adjustment_rule_audit (
    tenant_id uuid not null,
    audit_id uuid primary key,
    rule_book_id uuid not null,
    command varchar(80) not null,
    actor_id varchar(128) not null,
    correlation_id varchar(128) not null,
    causation_id varchar(128),
    before_hash char(64),
    after_hash char(64) not null,
    validation_result jsonb not null default '{}'::jsonb,
    replay_pointer varchar(255) not null,
    created_at timestamptz not null
);

create index adjustment_rule_audit_tenant_rule_book_idx
    on adjustment_rule_audit (tenant_id, rule_book_id, created_at desc);
