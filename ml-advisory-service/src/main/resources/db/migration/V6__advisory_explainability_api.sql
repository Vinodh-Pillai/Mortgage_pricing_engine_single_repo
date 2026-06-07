create table if not exists ml_advisory_explanations (
  explanation_id varchar(80) primary key,
  tenant_id uuid not null,
  advisory_id varchar(80) not null,
  model_version_id varchar(160) not null,
  snapshot_id varchar(160) not null,
  policy_version varchar(80) not null,
  summary text not null,
  confidence_narrative text not null,
  limitations_json text not null,
  generated_at timestamptz not null,
  expires_at timestamptz not null,
  created_at timestamptz not null default now(),
  unique (tenant_id, advisory_id),
  unique (tenant_id, explanation_id)
);

create table if not exists ml_explanation_drivers (
  explanation_id varchar(80) not null,
  tenant_id uuid not null,
  rank int not null,
  feature_label varchar(160) not null,
  direction varchar(40) not null,
  relative_impact varchar(40) not null,
  visibility_class varchar(40) not null,
  suppressed boolean not null default false,
  suppression_reason varchar(160),
  primary key (explanation_id, rank)
);

create index if not exists idx_ml_explanation_drivers_tenant_visibility
  on ml_explanation_drivers (tenant_id, visibility_class, rank);
