create table if not exists missing_price_incident (
    id uuid primary key,
    tenant_id varchar(80) not null,
    scenario_hash varchar(128) not null,
    product_code varchar(80) not null,
    investor_code varchar(80) not null,
    channel_code varchar(80) not null,
    lock_period_days integer not null,
    note_rate numeric(9,5),
    as_of timestamptz not null,
    reason_code varchar(80) not null,
    diagnostics_json text not null,
    status varchar(40) not null,
    correlation_id varchar(128) not null,
    created_at timestamptz not null,
    resolved_at timestamptz,
    version integer not null default 1
);

create index if not exists idx_missing_price_incident_status
    on missing_price_incident (tenant_id, status, created_at);

create index if not exists idx_missing_price_incident_reason
    on missing_price_incident (tenant_id, reason_code, created_at);

create index if not exists idx_missing_price_incident_scenario
    on missing_price_incident (tenant_id, scenario_hash);

create table if not exists missing_price_retry (
    id uuid primary key,
    incident_id uuid not null references missing_price_incident(id),
    tenant_id varchar(80) not null,
    attempted_by varchar(128) not null,
    attempted_at timestamptz not null,
    result_status varchar(40) not null,
    result_ref varchar(160) not null,
    error_code varchar(80)
);

create index if not exists idx_missing_price_retry_incident
    on missing_price_retry (tenant_id, incident_id, attempted_at desc);

create table if not exists pricing_missing_price_outbox (
    event_id uuid primary key,
    event_type varchar(120) not null,
    event_key varchar(180) not null,
    tenant_id varchar(80) not null,
    incident_id uuid not null,
    actor_id varchar(128) not null,
    correlation_id varchar(128) not null,
    idempotency_key varchar(160) not null,
    payload_json text not null,
    occurred_at timestamptz not null
);

create index if not exists idx_missing_price_outbox_key
    on pricing_missing_price_outbox (tenant_id, event_type, occurred_at);
