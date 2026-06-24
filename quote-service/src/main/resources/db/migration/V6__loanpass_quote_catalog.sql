create table loanpass_quote_catalog_snapshot (
    tenant_id uuid not null,
    snapshot_id varchar(160) not null,
    source_system varchar(160) not null,
    synthetic boolean not null default false,
    generator_version varchar(160) not null,
    seed varchar(160) not null,
    schema_version varchar(160) not null,
    loaded_at timestamptz not null,
    payload_hash varchar(128) not null,
    metadata jsonb not null,
    primary key (tenant_id, snapshot_id)
);

create table loanpass_quote_catalog_product (
    tenant_id uuid not null,
    snapshot_id varchar(160) not null,
    product_id varchar(160) not null,
    product_name varchar(240) not null,
    investor_name varchar(240) not null,
    product_type varchar(160) not null,
    status varchar(40) not null,
    lock_periods jsonb not null,
    note_rate_pct numeric(12, 5),
    price_bps numeric(14, 4),
    rules jsonb not null,
    stipulations jsonb not null,
    rejections jsonb not null,
    source_refs jsonb not null,
    primary key (tenant_id, snapshot_id, product_id),
    constraint fk_loanpass_catalog_product_snapshot foreign key (tenant_id, snapshot_id)
        references loanpass_quote_catalog_snapshot (tenant_id, snapshot_id)
);

create table loanpass_quote_catalog_source_payload (
    tenant_id uuid not null,
    snapshot_id varchar(160) not null,
    payload_hash varchar(128) not null,
    source_system varchar(160) not null,
    synthetic boolean not null default false,
    payload jsonb not null,
    created_at timestamptz not null default now(),
    primary key (tenant_id, snapshot_id, payload_hash),
    constraint fk_loanpass_catalog_payload_snapshot foreign key (tenant_id, snapshot_id)
        references loanpass_quote_catalog_snapshot (tenant_id, snapshot_id)
);

create index ix_loanpass_quote_catalog_snapshot_loaded on loanpass_quote_catalog_snapshot (tenant_id, loaded_at desc);
create index ix_loanpass_quote_catalog_product_status on loanpass_quote_catalog_product (tenant_id, snapshot_id, status);
