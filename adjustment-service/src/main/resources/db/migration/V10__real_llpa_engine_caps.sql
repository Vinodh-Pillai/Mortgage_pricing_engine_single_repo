create table if not exists adjustment_rule_books (
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
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint adjustment_rule_books_status_check
        check (status in ('DRAFT', 'VALIDATED', 'PENDING_APPROVAL', 'PUBLISHED', 'SUSPENDED', 'EXPIRED')),
    constraint adjustment_rule_books_published_requires_approval_check
        check (status <> 'PUBLISHED' or (approved_by is not null and published_at is not null))
);

create table if not exists adjustment_rules (
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
    created_at timestamptz not null default now(),
    constraint adjustment_rules_priority_check check (priority >= 0)
);

alter table adjustment_rule_books
    add column if not exists max_total_points_delta numeric,
    add column if not exists min_total_points_delta numeric;

alter table adjustment_rules
    add column if not exists max_output numeric,
    add column if not exists min_output numeric;

create index if not exists adjustment_rule_books_selector_published_idx
    on adjustment_rule_books (tenant_id, status, product_family, published_at desc);
