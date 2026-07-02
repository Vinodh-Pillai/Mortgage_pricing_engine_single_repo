create table if not exists non_qm_underwriting_results (
  tenant_id text not null,
  scenario_id text not null,
  underwriting_id uuid not null,
  product_code text not null,
  product_type text not null,
  decision text not null,
  audit_hash text not null,
  correlation_id text,
  result_json text not null,
  updated_at timestamp with time zone not null default current_timestamp,
  primary key (tenant_id, scenario_id)
);

create index if not exists idx_non_qm_underwriting_results_underwriting_id
  on non_qm_underwriting_results (underwriting_id);
