create table if not exists fee_catalog_versions (
    tenant_id uuid not null,
    catalog_version_id uuid not null,
    version int not null,
    status varchar(40) not null,
    effective_start timestamptz not null,
    effective_end timestamptz,
    content_hash varchar(128) not null,
    created_by varchar(128) not null,
    approved_by varchar(128),
    published_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    primary key (tenant_id, catalog_version_id),
    constraint fee_catalog_version_positive check (version > 0),
    constraint fee_catalog_status_check check (status in ('DRAFT', 'VALIDATED', 'PENDING_APPROVAL', 'PUBLISHED', 'SUSPENDED', 'ROLLED_BACK')),
    constraint fee_catalog_window_check check (effective_end is null or effective_end > effective_start),
    constraint fee_catalog_publish_metadata_check check (status <> 'PUBLISHED' or (approved_by is not null and published_at is not null))
);

create table if not exists fee_definitions (
    tenant_id uuid not null,
    fee_definition_id uuid not null,
    catalog_version_id uuid not null,
    fee_code varchar(64) not null,
    display_name varchar(160) not null,
    description varchar(1000),
    category varchar(120) not null,
    payer varchar(120),
    payee varchar(120),
    finance_charge boolean not null default false,
    apr_included boolean not null default false,
    tolerance_bucket varchar(120) not null,
    disclosure_section varchar(160) not null,
    calculation_method varchar(60) not null,
    formula_parameters jsonb not null default '{}'::jsonb,
    applicability jsonb not null default '{}'::jsonb,
    reason_code varchar(120) not null,
    source_ref varchar(255) not null,
    enabled boolean not null default true,
    content_hash varchar(128) not null,
    created_at timestamptz not null,
    primary key (tenant_id, fee_definition_id),
    constraint fee_definition_catalog_fk foreign key (tenant_id, catalog_version_id)
        references fee_catalog_versions (tenant_id, catalog_version_id),
    constraint fee_definition_method_check check (calculation_method in (
        'FIXED_AMOUNT',
        'PERCENT_OF_LOAN_AMOUNT',
        'BPS_OF_LOAN_AMOUNT',
        'PER_UNIT',
        'PASS_THROUGH',
        'MANUAL_INPUT_ALLOWED',
        'WAIVED',
        'FORMULA_EXPRESSION_APPROVED'
    ))
);

create table if not exists fee_catalog_audit (
    tenant_id uuid not null,
    audit_id uuid not null,
    catalog_version_id uuid not null,
    action varchar(80) not null,
    actor_id varchar(128) not null,
    correlation_id varchar(128) not null,
    before_hash varchar(128),
    after_hash varchar(128) not null,
    audit_payload jsonb not null default '{}'::jsonb,
    created_at timestamptz not null,
    primary key (tenant_id, audit_id),
    constraint fee_catalog_audit_catalog_fk foreign key (tenant_id, catalog_version_id)
        references fee_catalog_versions (tenant_id, catalog_version_id)
);

create unique index if not exists idx_fee_catalog_version_number
    on fee_catalog_versions (tenant_id, version);

create index if not exists idx_fee_catalog_effective_status
    on fee_catalog_versions (tenant_id, status, effective_start, effective_end);

create unique index if not exists idx_fee_definition_catalog_code
    on fee_definitions (tenant_id, catalog_version_id, fee_code);

create index if not exists idx_fee_definition_applicability_gin
    on fee_definitions using gin (applicability);

create index if not exists idx_fee_catalog_audit_lookup
    on fee_catalog_audit (tenant_id, catalog_version_id, created_at desc);
