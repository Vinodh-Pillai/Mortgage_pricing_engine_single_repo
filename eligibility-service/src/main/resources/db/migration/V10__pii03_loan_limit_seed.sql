create unique index if not exists limit_row_unique_scope_idx on eligibility.conforming_loan_limit_row (
  tenant_id,
  limit_set_id,
  state_code,
  coalesce(county_fips, '*'),
  units
);

insert into eligibility.conforming_loan_limit_set (
  tenant_id, limit_set_id, agency, year, status, effective_from, effective_to, version,
  source_name, source_document_uri, approved_by, approved_at_utc
) values (
  '11111111-1111-1111-1111-111111111111',
  '33333333-3333-3333-3333-333333333333',
  'FNMA',
  2026,
  'PUBLISHED',
  date '2026-01-01',
  date '2026-12-31',
  1,
  'PII-03-S02 approved story fixture',
  'world-class-pricing-engine/pii/PII-03-eligibility/stories/02-loan-limit-check.md',
  '00000000-0000-0000-0000-000000000000',
  timestamp '2026-01-01 00:00:00'
) on conflict (tenant_id, agency, year, version) do nothing;

insert into eligibility.conforming_loan_limit_row (
  tenant_id, limit_row_id, limit_set_id, state_code, county_name, county_fips, units,
  limit_amount, high_cost_area, row_hash
) values (
  '11111111-1111-1111-1111-111111111111',
  '44444444-4444-4444-4444-444444444444',
  '33333333-3333-3333-3333-333333333333',
  'TX',
  'Travis',
  '48453',
  1,
  806500.00,
  true,
  'pii03s02-travis-2026-fnma-1-unit'
) on conflict do nothing;
