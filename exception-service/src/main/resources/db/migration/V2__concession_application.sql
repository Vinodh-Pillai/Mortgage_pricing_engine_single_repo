create table if not exists concession_application (
  tenant_id uuid not null,
  application_id varchar(64) primary key,
  concession_request_id varchar(64) not null,
  target_type varchar(40) not null,
  quote_id varchar(128) not null,
  lock_id varchar(128),
  applied_unit varchar(40) not null,
  applied_value numeric(19, 8) not null,
  pricing_ledger_entry_id varchar(128) not null,
  before_price_hash varchar(128) not null,
  after_price_hash varchar(128) not null,
  pricing_rule_version_id varchar(128) not null,
  policy_version_id varchar(128) not null,
  precedence_config_version_id varchar(128) not null,
  rounding_scale int not null,
  rounding_mode varchar(64) not null,
  status varchar(40) not null,
  idempotency_key varchar(160) not null,
  applied_by varchar(128) not null,
  correlation_id varchar(128) not null,
  audit_ref varchar(128) not null,
  outbox_event_type varchar(128) not null,
  replay_hash varchar(128) not null,
  version int not null default 1,
  applied_at timestamptz not null,
  unique (tenant_id, concession_request_id, target_type, quote_id, lock_id),
  unique (tenant_id, idempotency_key),
  foreign key (concession_request_id) references concession_request (concession_request_id)
);

create table if not exists concession_application_audit (
  tenant_id uuid not null,
  application_id varchar(64) not null,
  command_payload_hash varchar(128) not null,
  pricing_response_hash varchar(128) not null,
  replay_hash varchar(128) not null,
  created_at timestamptz not null,
  primary key (tenant_id, application_id),
  foreign key (application_id) references concession_application (application_id)
);

create index if not exists idx_concession_application_tenant_quote
  on concession_application (tenant_id, quote_id, applied_at desc);

create index if not exists idx_concession_application_tenant_lock
  on concession_application (tenant_id, lock_id, applied_at desc)
  where lock_id is not null;
