-- PII-03-S08: cache invalidation/control metadata. Cached values remain in Redis only.

create table if not exists eligibility.eligibility_cache_namespace (
    tenant_id uuid not null,
    namespace varchar(64) not null,
    product_family varchar(32) not null,
    quote_type varchar(64) not null,
    ttl_seconds int not null check (ttl_seconds > 0),
    enabled boolean not null default true,
    updated_at_utc timestamptz not null default now(),
    primary key (tenant_id, namespace, product_family, quote_type)
);

create table if not exists eligibility.eligibility_cache_invalidation (
    tenant_id uuid not null,
    invalidation_id uuid primary key default gen_random_uuid(),
    namespace varchar(64) not null,
    version_token varchar(128) not null,
    reason varchar(128) not null,
    requested_by uuid not null,
    requested_at_utc timestamptz not null default now(),
    processed_at_utc timestamptz null,
    status varchar(16) not null check (status in ('PENDING', 'PROCESSED', 'FAILED')),
    error_message varchar(1000) null
);

create index if not exists eligibility_cache_invalidation_lookup_idx
    on eligibility.eligibility_cache_invalidation (tenant_id, namespace, status, requested_at_utc);
