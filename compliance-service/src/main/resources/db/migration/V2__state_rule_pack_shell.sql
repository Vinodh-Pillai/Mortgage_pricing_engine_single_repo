create table if not exists state_rule_pack (
  id uuid primary key,
  tenant_id uuid not null,
  state_code char(2) not null,
  code varchar(80) not null,
  name varchar(160) not null,
  category varchar(80) not null,
  created_at timestamptz not null,
  created_by varchar(128) not null,
  unique (tenant_id, state_code, code)
);

create table if not exists state_rule_pack_version (
  id uuid primary key,
  tenant_id uuid not null,
  rule_pack_id uuid not null references state_rule_pack(id),
  version int not null,
  status varchar(40) not null,
  effective_from date not null,
  effective_to date,
  applicability jsonb not null,
  rules jsonb not null default '[]'::jsonb,
  threshold_config_refs jsonb not null default '[]'::jsonb,
  federal_rule_pack_refs jsonb not null default '[]'::jsonb,
  citations jsonb not null default '[]'::jsonb,
  source_document_refs jsonb not null default '[]'::jsonb,
  hash varchar(160) not null,
  approved_by varchar(128),
  approved_at timestamptz,
  approval_comments text,
  created_at timestamptz not null,
  created_by varchar(128) not null,
  unique (tenant_id, rule_pack_id, version)
);

create table if not exists state_rule_pack_approval (
  id uuid primary key,
  tenant_id uuid not null,
  rule_pack_version_id uuid not null references state_rule_pack_version(id),
  action varchar(40) not null,
  actor_id varchar(128) not null,
  before_hash varchar(160),
  after_hash varchar(160) not null,
  comments text,
  correlation_id varchar(128) not null,
  created_at timestamptz not null
);

create index if not exists idx_state_rule_pack_tenant_state_status_effective
  on state_rule_pack_version (tenant_id, status, effective_from);

create index if not exists idx_state_rule_pack_version_lookup
  on state_rule_pack_version (tenant_id, rule_pack_id, version);

create index if not exists idx_state_rule_pack_approval_version
  on state_rule_pack_approval (tenant_id, rule_pack_version_id, created_at desc);
