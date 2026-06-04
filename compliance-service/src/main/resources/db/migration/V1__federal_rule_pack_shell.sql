create table if not exists federal_rule_pack (
  id uuid primary key,
  tenant_id uuid not null,
  code text not null,
  name text not null,
  category text not null,
  created_at timestamptz not null,
  created_by varchar(128) not null,
  unique (tenant_id, code)
);

create table if not exists federal_rule_pack_version (
  id uuid primary key,
  tenant_id uuid not null,
  rule_pack_id uuid not null references federal_rule_pack(id),
  version int not null,
  status text not null,
  effective_from date not null,
  effective_to date,
  applicability jsonb not null,
  rules jsonb not null,
  threshold_config_refs jsonb not null,
  citations jsonb not null,
  source_document_refs jsonb not null default '[]'::jsonb,
  hash text not null,
  created_by varchar(128) not null,
  approved_by varchar(128),
  created_at timestamptz not null,
  updated_at timestamptz not null,
  unique (tenant_id, rule_pack_id, version)
);

create index if not exists idx_federal_rule_pack_version_resolution
  on federal_rule_pack_version (tenant_id, rule_pack_id, status, effective_from, effective_to);

create table if not exists federal_rule_pack_approval (
  id uuid primary key,
  tenant_id uuid not null,
  rule_pack_version_id uuid not null references federal_rule_pack_version(id),
  action varchar(40) not null,
  actor_id varchar(128) not null,
  comments text,
  correlation_id varchar(128) not null,
  before_hash text,
  after_hash text not null,
  created_at timestamptz not null
);

create table if not exists compliance_outbox (
  event_id uuid primary key,
  tenant_id uuid not null,
  event_type varchar(160) not null,
  event_version int not null,
  partition_key varchar(200) not null,
  headers jsonb not null,
  payload jsonb not null,
  occurred_at timestamptz not null,
  published_at timestamptz,
  retry_count int not null default 0
);
