create table if not exists ml_advisory_results (
  advisory_id uuid primary key,
  tenant_id uuid not null,
  scenario_id varchar(160) not null,
  pricing_result_id varchar(160) not null,
  snapshot_id uuid not null,
  model_version_id varchar(160) not null,
  advisory_type varchar(40) not null,
  recommendation_json jsonb not null default '{}'::jsonb,
  confidence numeric(5,4) not null,
  confidence_band varchar(80) not null,
  authoritative boolean not null default false,
  status varchar(40) not null,
  generated_at timestamptz not null,
  expires_at timestamptz not null,
  disclaimer text not null,
  allowed_actions_json jsonb not null default '[]'::jsonb,
  correlation_id varchar(128) not null,
  constraint ml_advisory_results_authoritative_chk check (authoritative = false),
  constraint ml_advisory_results_confidence_chk check (confidence >= 0 and confidence <= 1)
);

create index if not exists ml_advisory_results_tenant_scenario_idx
  on ml_advisory_results (tenant_id, scenario_id, pricing_result_id, generated_at desc);

create table if not exists ml_advisory_reasons (
  advisory_id uuid not null,
  tenant_id uuid not null,
  reason_code varchar(120) not null,
  rank int not null,
  description text not null,
  direction varchar(80) not null,
  feature_ref varchar(160) not null,
  sensitivity_class varchar(80) not null,
  primary key (advisory_id, rank),
  constraint ml_advisory_reasons_result_fk foreign key (advisory_id) references ml_advisory_results (advisory_id)
);
