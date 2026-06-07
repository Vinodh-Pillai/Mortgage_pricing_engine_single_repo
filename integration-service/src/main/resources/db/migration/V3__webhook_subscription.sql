create table if not exists integration.integration_webhook_subscription (
  tenant_id uuid not null,
  channel_id varchar(128) not null,
  subscription_id uuid not null,
  display_name varchar(160) not null,
  endpoint_url varchar(1024) not null,
  endpoint_url_hash varchar(64) not null,
  event_types jsonb not null,
  status varchar(40) not null,
  signing_secret_ref varchar(256) not null,
  secret_version varchar(80) not null,
  retry_policy jsonb not null,
  failure_count int not null default 0,
  last_success_at timestamptz,
  last_failure_at timestamptz,
  version int not null,
  created_by varchar(128) not null,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  correlation_id varchar(128) not null,
  primary key (tenant_id, channel_id, subscription_id)
);

create unique index if not exists uq_integration_webhook_subscription_active_endpoint
  on integration.integration_webhook_subscription (tenant_id, channel_id, endpoint_url_hash)
  where status = 'ACTIVE';

create index if not exists idx_integration_webhook_subscription_tenant_status
  on integration.integration_webhook_subscription (tenant_id, status, updated_at desc);

create index if not exists idx_integration_webhook_subscription_channel
  on integration.integration_webhook_subscription (tenant_id, channel_id, status);
