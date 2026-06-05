create table if not exists integration.integration_los_quote_request (
  tenant_id uuid not null,
  channel_id varchar(128) not null,
  request_id uuid not null,
  los_loan_id varchar(160) not null,
  idempotency_key varchar(160) not null,
  request_hash varchar(64) not null,
  scenario_json jsonb not null,
  status varchar(40) not null,
  pricing_config_version varchar(160) not null,
  quote_id varchar(160),
  result_json jsonb not null default '{}'::jsonb,
  failure_code varchar(160),
  created_by varchar(128) not null,
  submitted_at timestamptz not null,
  completed_at timestamptz,
  correlation_id varchar(128) not null,
  primary key (tenant_id, channel_id, request_id),
  unique (tenant_id, channel_id, idempotency_key)
);

create index if not exists idx_integration_los_quote_request_loan
  on integration.integration_los_quote_request (tenant_id, los_loan_id);

create index if not exists idx_integration_los_quote_request_status
  on integration.integration_los_quote_request (tenant_id, status, submitted_at desc);
