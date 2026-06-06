create table best_execution_rank (
    tenant_id uuid not null,
    rank_id uuid primary key,
    quote_id uuid not null references quote (quote_id),
    option_id uuid not null,
    policy_id varchar(128) not null,
    policy_version varchar(128) not null,
    rank integer not null,
    score numeric(18,8) not null,
    criterion_scores jsonb not null,
    tie_breaker_trace jsonb not null default '[]'::jsonb,
    warnings jsonb not null default '[]'::jsonb,
    created_at timestamptz not null,
    constraint uq_best_execution_rank_tenant_quote_option unique (tenant_id, quote_id, option_id),
    constraint uq_best_execution_rank_tenant_quote_rank unique (tenant_id, quote_id, rank)
);

create index ix_best_execution_rank_tenant_policy on best_execution_rank (tenant_id, policy_id, policy_version);
create index ix_best_execution_rank_tenant_quote on best_execution_rank (tenant_id, quote_id, rank);
