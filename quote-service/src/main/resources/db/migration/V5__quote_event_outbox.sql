create table if not exists quote_outbox (
    tenant_id uuid not null,
    event_id uuid primary key,
    aggregate_id uuid not null,
    aggregate_version int not null,
    event_type varchar(120) not null,
    event_version varchar(20) not null,
    payload jsonb not null,
    headers jsonb not null,
    status varchar(40) not null,
    attempt_count int not null default 0,
    next_attempt_at timestamptz,
    published_at timestamptz,
    created_at timestamptz not null,
    constraint uq_quote_outbox_tenant_event unique (tenant_id, event_id),
    constraint ck_quote_outbox_status check (status in ('PENDING', 'PUBLISHED', 'FAILED', 'DLQ'))
);

create index if not exists ix_quote_outbox_status_next_attempt
    on quote_outbox (status, next_attempt_at);

create table if not exists quote_event_delivery (
    tenant_id uuid not null,
    delivery_id uuid primary key,
    event_id uuid not null references quote_outbox (event_id),
    consumer_name varchar(160) not null,
    status varchar(40) not null,
    attempt_count int not null default 0,
    last_error text,
    delivered_at timestamptz,
    constraint ck_quote_event_delivery_status check (status in ('PENDING', 'DELIVERED', 'FAILED', 'DLQ'))
);

create index if not exists ix_quote_event_delivery_tenant_event
    on quote_event_delivery (tenant_id, event_id);
