CREATE TABLE IF NOT EXISTS tenant.tenant_feature_flag (
  tenant_id UUID NOT NULL REFERENCES tenant.tenant(tenant_id),
  feature_key VARCHAR(64) NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT false,
  config JSONB DEFAULT '{}',
  updated_at TIMESTAMPTZ DEFAULT NOW(),
  updated_by VARCHAR(160),
  PRIMARY KEY (tenant_id, feature_key)
);

CREATE TABLE IF NOT EXISTS tenant.tenant_feature_catalog (
  feature_key VARCHAR(64) PRIMARY KEY,
  category VARCHAR(64) NOT NULL,
  default_enabled BOOLEAN NOT NULL DEFAULT false
);

INSERT INTO tenant.tenant_feature_catalog (feature_key, category, default_enabled) VALUES
  ('non_qm_pricing', 'Non-QM', true),
  ('heloc_pricing', 'Core Pricing', true),
  ('reverse_mortgage', 'Advanced', false),
  ('government_products', 'Government', true),
  ('mi_pricing', 'Core Pricing', true),
  ('quick_pricer', 'Core Pricing', true),
  ('lock_management', 'Core Pricing', true),
  ('scenario_analysis', 'Advanced', true),
  ('partner_integrations', 'Advanced', false),
  ('ml_advisory', 'Advanced', false),
  ('loanpass_compatibility', 'LoanPass Integration', false),
  ('loanpass_strict_mapping', 'LoanPass Integration', true),
  ('loanpass_callback_delivery', 'LoanPass Integration', false)
ON CONFLICT (feature_key) DO UPDATE SET
  category = EXCLUDED.category,
  default_enabled = EXCLUDED.default_enabled;
