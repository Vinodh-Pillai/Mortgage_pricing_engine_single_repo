create table if not exists ml_pricing_advisories (
  advisory_id uuid primary key,
  tenant_id uuid not null,
  scenario_id varchar(160) not null,
  pricing_result_id varchar(160) not null,
  snapshot_id varchar(160) not null,
  model_version_id varchar(160) not null,
  advisory_subtype varchar(80) not null,
  suggestion_json jsonb not null default '{}'::jsonb,
  confidence numeric(5,4) not null,
  severity varchar(80) not null,
  status varchar(40) not null,
  policy_version varchar(160) not null,
  generated_at timestamptz not null,
  expires_at timestamptz not null,
  authoritative boolean not null default false,
  deterministic_pricing_unchanged boolean not null default true,
  correlation_id varchar(128) not null,
  constraint ml_pricing_advisories_authoritative_chk check (authoritative = false),
  constraint ml_pricing_advisories_pricing_unchanged_chk check (deterministic_pricing_unchanged = true),
  constraint ml_pricing_advisories_confidence_chk check (confidence >= 0 and confidence <= 1)
);

create index if not exists ml_pricing_advisories_tenant_pricing_idx
  on ml_pricing_advisories (tenant_id, pricing_result_id, model_version_id, snapshot_id, generated_at desc);

create index if not exists ml_pricing_advisories_tenant_status_idx
  on ml_pricing_advisories (tenant_id, status, generated_at desc);
