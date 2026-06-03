-- PII-03-S07: extension registry for future product-family eligibility modules.
-- This table records module availability only; it does not implement government eligibility rules.

create table if not exists eligibility.eligibility_rule_module_registry (
    tenant_id uuid not null,
    module_id uuid primary key default gen_random_uuid(),
    product_family varchar(32) not null,
    quote_type varchar(64) not null,
    module_name varchar(128) not null,
    module_version varchar(32) not null,
    status varchar(16) not null check (status in ('ENABLED', 'DISABLED', 'PREVIEW')),
    rule_codes jsonb not null default '[]'::jsonb,
    created_at_utc timestamptz not null default now(),
    updated_at_utc timestamptz not null default now(),
    unique (tenant_id, product_family, quote_type, module_name, module_version)
);

create unique index if not exists eligibility_rule_module_one_enabled_idx
    on eligibility.eligibility_rule_module_registry (tenant_id, product_family, quote_type, module_name)
    where status = 'ENABLED';

create index if not exists eligibility_rule_module_lookup_idx
    on eligibility.eligibility_rule_module_registry (tenant_id, product_family, quote_type, status);
