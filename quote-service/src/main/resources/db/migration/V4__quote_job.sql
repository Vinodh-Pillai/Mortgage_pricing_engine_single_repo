create table if not exists quote_job (
    tenant_id uuid not null,
    job_id uuid primary key,
    status varchar(40) not null,
    request_payload jsonb not null,
    request_hash varchar(128) not null,
    quote_id uuid,
    failure_code varchar(80),
    failure_detail text,
    progress jsonb not null default '{}'::jsonb,
    attempt_count int not null default 0,
    max_attempts int not null,
    idempotency_key varchar(160) not null,
    created_by varchar(128) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    expires_at timestamptz not null,
    correlation_id varchar(128) not null,
    version int not null default 1,
    constraint uq_quote_job_tenant_job unique (tenant_id, job_id),
    constraint uq_quote_job_tenant_idempotency unique (tenant_id, idempotency_key),
    constraint ck_quote_job_status check (status in ('QUEUED', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED'))
);

create index if not exists ix_quote_job_tenant_status_created
    on quote_job (tenant_id, status, created_at);
