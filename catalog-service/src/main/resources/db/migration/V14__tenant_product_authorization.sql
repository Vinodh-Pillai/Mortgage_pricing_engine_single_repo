create table if not exists catalog.tenant_product_authorization (
  tenant_id uuid not null,
  product_code varchar(64) not null,
  investor_code varchar(64),
  channel_code varchar(64),
  status varchar(16) not null default 'ACTIVE',
  authorized_at timestamptz not null default now(),
  authorized_by varchar(160),
  expires_at timestamptz,
  notes varchar(500),
  constraint tenant_product_authorization_status_ck check (status in ('ACTIVE', 'INACTIVE', 'PENDING'))
);

create unique index if not exists tenant_product_authorization_key_idx
  on catalog.tenant_product_authorization (tenant_id, product_code, coalesce(investor_code, '*'), coalesce(channel_code, '*'));

create index if not exists idx_tenant_auth_lookup
  on catalog.tenant_product_authorization (tenant_id, status, expires_at);
