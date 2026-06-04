create table if not exists ml_advisory_controls (
  id uuid primary key,
  tenant_id uuid not null,
  scope_type varchar(40) not null default 'TENANT_CHANNEL_PRODUCT',
  channel varchar(80) not null,
  product_family varchar(120) not null,
  advisory_type varchar(40) not null,
  mode varchar(40) not null,
  effective_from timestamptz not null,
  effective_to timestamptz,
  version int not null,
  status varchar(40) not null,
  created_by varchar(128) not null,
  created_at timestamptz not null,
  approved_by varchar(128),
  approval_ref varchar(160),
  change_reason text not null,
  model_risk_ticket varchar(160) not null,
  constraint ml_advisory_controls_mode_chk check (mode in ('DISABLED', 'SHADOW_ONLY', 'ADVISORY_VISIBLE')),
  constraint ml_advisory_controls_type_chk check (advisory_type in ('PRICING', 'ELIGIBILITY_RISK', 'EXPLAINABILITY', 'FEEDBACK', 'DRIFT')),
  constraint ml_advisory_controls_window_chk check (effective_to is null or effective_to > effective_from)
);

create index if not exists ml_advisory_controls_tenant_scope_idx
  on ml_advisory_controls (tenant_id, channel, product_family, advisory_type, status, effective_from desc);

create table if not exists ml_advisory_kill_switch (
  id uuid primary key,
  tenant_id uuid,
  enabled boolean not null,
  reason text not null,
  activated_by varchar(128) not null,
  activated_at timestamptz not null,
  correlation_id varchar(128) not null
);

create unique index if not exists ml_advisory_one_global_kill_switch_idx
  on ml_advisory_kill_switch ((tenant_id is null)) where tenant_id is null and enabled;
