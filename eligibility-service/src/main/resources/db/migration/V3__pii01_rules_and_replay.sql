create table if not exists eligibility.rule_definition (
  rule_code varchar(8) primary key,
  rule_name varchar(64) not null,
  description text not null,
  rule_order int not null,
  is_active boolean not null default true,
  created_at_utc timestamptz not null default now()
);

insert into eligibility.rule_definition (rule_code, rule_name, description, rule_order, is_active) values
  ('R00', 'GENERAL_VALIDATION', 'General request validation', 0, true),
  ('R01', 'FICO_MINIMUM', 'Representative FICO must be at least 620', 1, true),
  ('R02', 'LTV_MAXIMUM', 'Loan-to-value ratio must not exceed 97%', 2, true),
  ('R03', 'DTI_MAXIMUM', 'Debt-to-income ratio must not exceed 43%', 3, true),
  ('R04', 'PROPERTY_TYPE', 'Property type must be acceptable', 4, true),
  ('R05', 'OCCUPANCY_TYPE', 'Occupancy type must be acceptable', 5, true),
  ('R06', 'LOAN_PURPOSE', 'Loan purpose must be acceptable', 6, true),
  ('R07', 'INVESTOR_STATUS', 'Investor must be active', 7, true),
  ('R08', 'PRODUCT_INVESTOR', 'Product must support the investor', 8, true),
  ('R09', 'CHANNEL_ALLOWED', 'Channel must be allowed by product', 9, true),
  ('R10', 'STATE_ALLOWED', 'State must be allowed by product', 10, true),
  ('R11', 'LOAN_AMOUNT_LIMIT', 'Loan amount must be within conforming limit', 11, true),
  ('R12', 'DOCUMENTATION_TYPE', 'Documentation type must be acceptable', 12, true);

create table if not exists eligibility.reason_code (
  code varchar(8) primary key,
  rule_code varchar(8) not null references eligibility.rule_definition(rule_code) on delete cascade,
  severity varchar(16) not null check (severity in ('HARD_STOP', 'WARNING', 'INFO')),
  category varchar(32) not null,
  message text not null,
  description text
);

insert into eligibility.reason_code (code, rule_code, severity, category, message, description) values
  ('FC01', 'R01', 'HARD_STOP', 'FICO', 'FICO score below minimum threshold.', 'The borrower''s representative FICO score is below the minimum required score of 620 for conventional loan eligibility.'),
  ('FC02', 'R01', 'WARNING', 'FICO', 'Representative FICO score is missing.', 'The FICO score was not provided in the request and cannot be validated.'),
  ('FC03', 'R01', 'WARNING', 'FICO', 'FICO score is outside valid range.', 'The provided FICO score is not between 300 and 850.'),
  ('LT01', 'R02', 'HARD_STOP', 'LTV', 'LTV exceeds maximum allowed limit.', 'The loan-to-value ratio exceeds the maximum allowed 97% for conventional loans.'),
  ('LT02', 'R02', 'WARNING', 'LTV', 'Loan amount or purchase price is missing.', 'Cannot calculate LTV because required fields are missing.'),
  ('LT03', 'R02', 'HARD_STOP', 'LTV', 'CLTV exceeds maximum allowed limit.', 'The combined loan-to-value ratio exceeds the maximum allowed for the loan program.'),
  ('DT01', 'R03', 'HARD_STOP', 'DTI', 'DTI exceeds maximum allowed limit.', 'The debt-to-income ratio exceeds the maximum allowed 43% for conventional loans.'),
  ('DT02', 'R03', 'WARNING', 'DTI', 'Monthly income or monthly debt is missing.', 'Cannot calculate DTI because income or debt information is missing.'),
  ('PT01', 'R04', 'HARD_STOP', 'PROPERTY_TYPE', 'Property type is not acceptable.', 'The property type is not supported for conventional loan eligibility.'),
  ('PT02', 'R04', 'WARNING', 'PROPERTY_TYPE', 'Property type is missing.', 'The property type was not provided in the request.'),
  ('OC01', 'R05', 'HARD_STOP', 'OCCUPANCY', 'Occupancy type is not acceptable.', 'The occupancy type is not supported for conventional loan eligibility.'),
  ('OC02', 'R05', 'WARNING', 'OCCUPANCY', 'Occupancy type is missing.', 'The occupancy type was not provided in the request.'),
  ('LP01', 'R06', 'HARD_STOP', 'LOAN_PURPOSE', 'Loan purpose is not acceptable.', 'The loan purpose is not supported for conventional loan eligibility.'),
  ('LP02', 'R06', 'WARNING', 'LOAN_PURPOSE', 'Loan purpose is missing.', 'The loan purpose was not provided in the request.'),
  ('IV01', 'R07', 'HARD_STOP', 'INVESTOR', 'Investor is not active.', 'The specified investor is suspended or inactive and cannot be used as a GSE.'),
  ('IV02', 'R07', 'WARNING', 'INVESTOR', 'Investor code not found in catalog.', 'The investor code was not found in the product catalog.'),
  ('PR01', 'R08', 'HARD_STOP', 'PRODUCT', 'Product does not support the investor.', 'The selected product does not support the specified investor for this loan.'),
  ('PR02', 'R08', 'WARNING', 'PRODUCT', 'Product code not found in catalog.', 'The product code was not found in the product catalog.'),
  ('CH01', 'R09', 'HARD_STOP', 'CHANNEL', 'Channel is not allowed by product.', 'The selected channel is not allowed for the specified product.'),
  ('CH02', 'R09', 'WARNING', 'CHANNEL', 'Channel code not found in catalog.', 'The channel code was not found in the product catalog.'),
  ('ST01', 'R10', 'HARD_STOP', 'STATE', 'State is not allowed by product.', 'The property state is not supported for the specified product.'),
  ('ST02', 'R10', 'WARNING', 'STATE', 'State code is missing.', 'The property state was not provided in the request.'),
  ('AL01', 'R11', 'HARD_STOP', 'LOAN_AMOUNT', 'Loan amount exceeds conforming limit.', 'The loan amount exceeds the federal conforming loan limit for the property state.'),
  ('AL02', 'R11', 'WARNING', 'LOAN_AMOUNT', 'Conforming loan limit not found for state.', 'No conforming loan limit is configured for the property state.'),
  ('AL03', 'R11', 'WARNING', 'LOAN_AMOUNT', 'Loan amount or state is missing.', 'Cannot validate loan amount limit because required fields are missing.'),
  ('DC01', 'R12', 'HARD_STOP', 'DOCUMENTATION', 'Documentation type is not acceptable.', 'The documentation type is not supported for conventional loan eligibility.'),
  ('DC02', 'R12', 'WARNING', 'DOCUMENTATION', 'Documentation type is missing.', 'The documentation type was not provided in the request.'),
  ('GEN01', 'R00', 'WARNING', 'GENERAL', 'Insufficient data to evaluate.', 'One or more required fields are missing from the request.'),
  ('GEN02', 'R00', 'HARD_STOP', 'GENERAL', 'Request validation failed.', 'The request contains invalid data that prevents evaluation.'),
  ('GEN03', 'R00', 'WARNING', 'GENERAL', 'Policy version mismatch.', 'The request target policy version does not match the current policy version.'),
  ('GEN04', 'R00', 'HARD_STOP', 'GENERAL', 'Tenant not configured.', 'The specified tenant does not have eligibility rules configured.');

create table if not exists eligibility.policy_version (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null,
  version int not null,
  name varchar(128) not null,
  status varchar(16) not null default 'ACTIVE' check (status in ('ACTIVE', 'SUPERSEDED', 'DRAFT')),
  effective_from date not null,
  effective_to date,
  created_at_utc timestamptz not null default now(),
  unique (tenant_id, version)
);

create table if not exists eligibility.replay_record (
  tenant_id uuid not null,
  replay_id uuid primary key,
  input_hash varchar(128) not null,
  output_hash varchar(128) not null,
  replay_status varchar(16) not null check (replay_status in ('MATCH', 'MISMATCH', 'ERROR')),
  policy_version int not null,
  rule_set_version int not null,
  replay_json jsonb not null,
  occurred_at timestamptz not null default now()
);

create index if not exists replay_status_idx on eligibility.replay_record (tenant_id, replay_status, occurred_at desc);
create index if not exists policy_version_idx on eligibility.policy_version (tenant_id, version, status);
