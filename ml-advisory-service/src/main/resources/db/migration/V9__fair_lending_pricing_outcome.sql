CREATE SCHEMA IF NOT EXISTS fair_lending;

CREATE TABLE IF NOT EXISTS fair_lending.pricing_outcome (
  outcome_id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL,
  run_id UUID NOT NULL,
  quote_id UUID,
  scenario_id UUID,
  applicant_race VARCHAR(32),
  applicant_ethnicity VARCHAR(32),
  applicant_sex VARCHAR(16),
  applicant_age INT,
  co_applicant_race VARCHAR(32),
  co_applicant_ethnicity VARCHAR(32),
  co_applicant_sex VARCHAR(16),
  fico INT,
  ltv DECIMAL(5,2),
  dti DECIMAL(5,2),
  loan_amount DECIMAL(15,2),
  loan_purpose VARCHAR(32),
  property_type VARCHAR(32),
  occupancy_type VARCHAR(32),
  state VARCHAR(2),
  channel VARCHAR(32),
  product_family VARCHAR(32),
  investor VARCHAR(32),
  note_rate DECIMAL(7,5),
  price DECIMAL(10,5),
  total_llpa_bps INT,
  margin_bps INT,
  lock_period_days INT,
  pricing_date TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_fair_lending_outcome_tenant_date ON fair_lending.pricing_outcome (tenant_id, pricing_date);
CREATE INDEX IF NOT EXISTS idx_fair_lending_outcome_protected ON fair_lending.pricing_outcome (applicant_race, applicant_ethnicity, applicant_sex);
