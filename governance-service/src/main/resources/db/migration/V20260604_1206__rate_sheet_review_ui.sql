create table if not exists governance_rate_sheet_review_ui (
  tenant_id uuid not null,
  id uuid primary key,
  status varchar(40) not null,
  version int not null default 1,
  request_json jsonb not null,
  result_json jsonb not null default '{}'::jsonb,
  created_by varchar(128) not null,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  correlation_id varchar(128) not null,
  idempotency_key varchar(160),
  unique (tenant_id, id)
);

create unique index if not exists ux_governance_rate_sheet_review_ui_idempotency
  on governance_rate_sheet_review_ui (tenant_id, idempotency_key)
  where idempotency_key is not null;

create index if not exists idx_governance_rate_sheet_review_ui_status
  on governance_rate_sheet_review_ui (tenant_id, status, updated_at desc);

create table if not exists admin_rate_sheet_row_read (
  tenant_id uuid not null,
  version_id varchar(128) not null,
  row_id varchar(128) not null,
  product_ref varchar(128) not null,
  investor_ref varchar(128) not null,
  channel_ref varchar(128) not null,
  lock_period varchar(80) not null,
  rate varchar(80) not null,
  price varchar(80) not null,
  adjustment_hash varchar(128) not null,
  row_hash varchar(128) not null,
  status varchar(40) not null,
  effective_date date not null,
  primary key (tenant_id, version_id, row_id)
);

create index if not exists idx_admin_rate_sheet_row_read_filter
  on admin_rate_sheet_row_read (tenant_id, version_id, investor_ref, product_ref, channel_ref, effective_date);

create table if not exists admin_rate_sheet_review_note (
  tenant_id uuid not null,
  note_id uuid primary key,
  version_id varchar(128) not null,
  row_id varchar(128),
  finding_code varchar(128) not null,
  reason_code varchar(128) not null,
  comment text not null,
  actor varchar(128) not null,
  created_at timestamptz not null
);

create index if not exists idx_admin_rate_sheet_review_note_version
  on admin_rate_sheet_review_note (tenant_id, version_id, row_id, created_at);
