create table if not exists integration_service_account (
  tenant_id uuid not null,
  account_id uuid primary key,
  display_name varchar(160) not null,
  principal_type varchar(40) not null,
  status varchar(40) not null,
  scopes jsonb not null default '[]'::jsonb,
  allowed_channels jsonb not null default '[]'::jsonb,
  expires_at timestamptz,
  version int not null default 1,
  created_by varchar(128) not null,
  created_at timestamptz not null,
  updated_by varchar(128) not null,
  updated_at timestamptz not null,
  correlation_id varchar(128) not null,
  constraint integration_service_account_tenant_unique unique (tenant_id, account_id)
);

create index if not exists integration_service_account_tenant_status_idx
  on integration_service_account (tenant_id, status, updated_at desc);

create table if not exists integration_credential_reference (
  tenant_id uuid not null,
  credential_id uuid primary key,
  account_id uuid not null,
  credential_name varchar(160) not null,
  credential_type varchar(40) not null,
  secret_ref varchar(512) not null,
  secret_version varchar(128) not null,
  status varchar(40) not null,
  last_rotated_at timestamptz not null,
  expires_at timestamptz,
  metadata jsonb not null default '{}'::jsonb,
  version int not null default 1,
  created_by varchar(128) not null,
  created_at timestamptz not null,
  updated_by varchar(128) not null,
  updated_at timestamptz not null,
  correlation_id varchar(128) not null,
  constraint integration_credential_reference_tenant_unique unique (tenant_id, credential_id),
  constraint integration_credential_reference_account_fk foreign key (account_id) references integration_service_account(account_id)
);

create unique index if not exists integration_credential_reference_active_name_idx
  on integration_credential_reference (tenant_id, account_id, lower(credential_name))
  where status = 'ACTIVE';

create index if not exists integration_credential_reference_tenant_status_idx
  on integration_credential_reference (tenant_id, status, updated_at desc);

create table if not exists integration_credential_usage_audit (
  tenant_id uuid not null,
  credential_id uuid not null,
  purpose varchar(160) not null,
  used_by varchar(160) not null,
  success boolean not null,
  occurred_at timestamptz not null,
  correlation_id varchar(128) not null
);

create index if not exists integration_credential_usage_audit_tenant_credential_idx
  on integration_credential_usage_audit (tenant_id, credential_id, occurred_at desc);
