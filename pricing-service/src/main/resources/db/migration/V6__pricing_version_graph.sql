create table if not exists pricing_version_graph (
    tenant_id varchar(64) not null,
    graph_id uuid primary key default gen_random_uuid(),
    product_code varchar(80),
    investor_code varchar(80),
    channel_code varchar(80),
    as_of timestamptz not null,
    version_refs jsonb not null default '[]'::jsonb,
    graph_hash varchar(64) not null,
    warnings jsonb not null default '[]'::jsonb,
    created_at timestamptz not null default current_timestamp,
    actor_id varchar(128) not null,
    correlation_id varchar(128) not null
);

create table if not exists artifact_version (
    id uuid primary key default gen_random_uuid(),
    tenant_id varchar(64) not null,
    product_code varchar(80),
    investor_code varchar(80),
    channel_code varchar(80),
    artifact_type varchar(40) not null,
    version_number int not null,
    status varchar(40) not null default 'PUBLISHED',
    effective_from timestamptz not null,
    effective_to timestamptz,
    version_hash varchar(64),
    created_at timestamptz not null default current_timestamp,
    updated_at timestamptz not null default current_timestamp,
    constraint artifact_version_status_ck check (status in ('DRAFT', 'PUBLISHED', 'SUSPENDED')),
    constraint artifact_version_effective_ck check (effective_to is null or effective_to > effective_from)
);

create table if not exists version_graph_event (
    id uuid primary key default gen_random_uuid(),
    tenant_id varchar(64) not null,
    graph_id uuid not null,
    event_type varchar(120) not null,
    actor_id varchar(128) not null,
    correlation_id varchar(128) not null,
    idempotency_key varchar(160),
    payload jsonb not null default '{}'::jsonb,
    occurred_at timestamptz not null default current_timestamp
);

create table if not exists version_graph_audit (
    id uuid primary key default gen_random_uuid(),
    tenant_id varchar(64) not null,
    graph_id uuid not null,
    as_of timestamptz not null,
    graph_hash varchar(64) not null,
    actor_id varchar(128) not null,
    correlation_id varchar(128) not null,
    pinned_refs jsonb not null default '[]'::jsonb,
    occurred_at timestamptz not null default current_timestamp
);

create table if not exists version_graph_cache (
    cache_key varchar(256) primary key,
    tenant_id varchar(64) not null,
    graph_id uuid not null,
    graph_hash varchar(64) not null,
    ttl_seconds int not null default 300,
    created_at timestamptz not null default current_timestamp,
    expires_at timestamptz not null
);

create index if not exists pricing_version_graph_tenant_hash_idx
    on pricing_version_graph (tenant_id, graph_hash);

create index if not exists pricing_version_graph_lookup_idx
    on pricing_version_graph (tenant_id, as_of, product_code, investor_code, channel_code);

create index if not exists artifact_version_resolution_idx
    on artifact_version (tenant_id, artifact_type, status, effective_from, effective_to);

create index if not exists artifact_version_scope_idx
    on artifact_version (tenant_id, artifact_type, product_code, investor_code, channel_code, status, effective_from, effective_to);

create index if not exists version_graph_event_graph_idx
    on version_graph_event (tenant_id, graph_id, occurred_at desc);

create index if not exists version_graph_audit_graph_idx
    on version_graph_audit (tenant_id, graph_id);

create index if not exists version_graph_cache_expires_idx
    on version_graph_cache (expires_at);
