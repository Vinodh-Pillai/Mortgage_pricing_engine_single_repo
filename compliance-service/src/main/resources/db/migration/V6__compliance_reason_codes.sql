create table if not exists compliance_reason_code (
  id uuid primary key,
  tenant_id varchar(64) not null,
  code varchar(120) not null,
  category varchar(120) not null,
  created_at timestamp not null,
  created_by varchar(128) not null
);

create unique index if not exists idx_compliance_reason_code_tenant_code_category
  on compliance_reason_code (tenant_id, code, category);

create table if not exists compliance_reason_code_version (
  id uuid primary key,
  tenant_id varchar(64) not null,
  reason_code_id uuid not null,
  version int not null,
  status varchar(40) not null,
  severity varchar(40) not null,
  effective_from date not null,
  effective_to date,
  internal_label varchar(240) not null,
  borrower_safe_label boolean not null,
  borrower_safe_approved boolean not null default false,
  description text not null,
  citations text not null,
  rule_mappings text not null,
  locale_text text not null,
  successor_code varchar(120),
  hash varchar(160) not null,
  approved_by varchar(128),
  approved_at timestamp,
  approval_comment text,
  correlation_id varchar(128) not null,
  created_at timestamp not null,
  updated_at timestamp not null,
  foreign key (reason_code_id) references compliance_reason_code(id)
);

create unique index if not exists idx_compliance_reason_code_version_tenant_code_version
  on compliance_reason_code_version (tenant_id, reason_code_id, version);

create index if not exists idx_compliance_reason_code_version_tenant_status_effective
  on compliance_reason_code_version (tenant_id, status, effective_from, effective_to);

create index if not exists idx_compliance_reason_code_version_mapping_lookup
  on compliance_reason_code_version (tenant_id, category, status, updated_at);

create table if not exists compliance_reason_code_approval (
  id uuid primary key,
  tenant_id varchar(64) not null,
  reason_code_version_id uuid not null,
  action varchar(80) not null,
  actor_id varchar(128) not null,
  comments text not null,
  before_hash varchar(160),
  after_hash varchar(160) not null,
  correlation_id varchar(128) not null,
  created_at timestamp not null,
  foreign key (reason_code_version_id) references compliance_reason_code_version(id)
);

create index if not exists idx_compliance_reason_code_approval_tenant_version
  on compliance_reason_code_approval (tenant_id, reason_code_version_id, created_at);
