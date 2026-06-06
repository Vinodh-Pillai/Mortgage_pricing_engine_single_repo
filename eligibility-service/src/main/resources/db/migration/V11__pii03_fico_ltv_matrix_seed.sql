create unique index if not exists matrix_set_unique_scope_idx on eligibility.fico_ltv_matrix_set (
  tenant_id,
  product_family,
  coalesce(investor_code, '*'),
  coalesce(channel, '*'),
  version
);

create unique index if not exists matrix_row_unique_scope_idx on eligibility.fico_ltv_matrix_row (
  tenant_id,
  matrix_set_id,
  fico_min,
  fico_max,
  loan_purpose,
  occupancy_type,
  coalesce(property_type, '*'),
  units_min,
  units_max
);

insert into eligibility.fico_ltv_matrix_set (
  tenant_id, matrix_set_id, product_family, investor_code, channel, status,
  effective_from, effective_to, version, created_by, approved_by
) values (
  '11111111-1111-1111-1111-111111111111',
  '66666666-6666-6666-6666-666666666666',
  'CONVENTIONAL',
  'FNMA',
  null,
  'PUBLISHED',
  date '2026-01-01',
  date '2026-12-31',
  1,
  'PII-03-S03 approved story fixture',
  '00000000-0000-0000-0000-000000000000'
) on conflict do nothing;

insert into eligibility.fico_ltv_matrix_row (
  tenant_id, matrix_row_id, matrix_set_id, fico_min, fico_max, max_ltv, max_cltv,
  loan_purpose, occupancy_type, property_type, units_min, units_max,
  documentation_type, aus_type, severity_if_missing_fico, reason_code, row_hash
) values
  ('11111111-1111-1111-1111-111111111111', '55555555-5555-5555-5555-555555555555', '66666666-6666-6666-6666-666666666666', 740, 850, 0.97000, 0.97000, 'PURCHASE', 'PRIMARY_RESIDENCE', 'SINGLE_FAMILY', 1, 4, null, null, 'WARNING', 'FICO_LTV_WITHIN_MATRIX', 'pii03s03-conf30-fnma-primary-740-850'),
  ('11111111-1111-1111-1111-111111111111', '55555555-5555-5555-5555-555555555556', '66666666-6666-6666-6666-666666666666', 720, 739, 0.95000, 0.95000, 'PURCHASE', 'PRIMARY_RESIDENCE', 'SINGLE_FAMILY', 1, 4, null, null, 'WARNING', 'FICO_LTV_WITHIN_MATRIX', 'pii03s03-conf30-fnma-primary-720-739'),
  ('11111111-1111-1111-1111-111111111111', '55555555-5555-5555-5555-555555555557', '66666666-6666-6666-6666-666666666666', 700, 719, 0.90000, 0.90000, 'PURCHASE', 'PRIMARY_RESIDENCE', 'SINGLE_FAMILY', 1, 4, null, null, 'WARNING', 'FICO_LTV_WITHIN_MATRIX', 'pii03s03-conf30-fnma-primary-700-719'),
  ('11111111-1111-1111-1111-111111111111', '55555555-5555-5555-5555-555555555558', '66666666-6666-6666-6666-666666666666', 620, 699, 0.90000, 0.90000, 'PURCHASE', 'PRIMARY_RESIDENCE', 'SINGLE_FAMILY', 1, 4, null, null, 'WARNING', 'FICO_LTV_WITHIN_MATRIX', 'pii03s03-conf30-fnma-primary-620-699')
on conflict do nothing;
