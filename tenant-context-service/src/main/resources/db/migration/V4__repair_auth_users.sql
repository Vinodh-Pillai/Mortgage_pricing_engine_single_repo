CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS users (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email VARCHAR(320) NOT NULL UNIQUE,
  full_name VARCHAR(160) NOT NULL,
  role VARCHAR(64) NOT NULL,
  password_hash TEXT NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT users_email_lowercase CHECK (email = lower(email)),
  CONSTRAINT users_role_check CHECK (role IN (
    'loan_officer',
    'pricing_analyst',
    'operations_lead',
    'governance_reviewer',
    'admin',
    'partner_manager',
    'compliance_officer',
    'borrower'
  ))
);

CREATE INDEX IF NOT EXISTS idx_users_role ON users (role);
CREATE INDEX IF NOT EXISTS idx_users_enabled ON users (enabled);

INSERT INTO users (email, full_name, role, password_hash) VALUES
  ('sarah.mitchell@wcpe.demo', 'Sarah Mitchell', 'loan_officer', crypt('Password123!', gen_salt('bf'))),
  ('david.chen@wcpe.demo', 'David Chen', 'pricing_analyst', crypt('Password123!', gen_salt('bf'))),
  ('maria.rodriguez@wcpe.demo', 'Maria Rodriguez', 'operations_lead', crypt('Password123!', gen_salt('bf'))),
  ('james.thompson@wcpe.demo', 'James Thompson', 'governance_reviewer', crypt('Password123!', gen_salt('bf'))),
  ('admin@wcpe.demo', 'Admin User', 'admin', crypt('Password123!', gen_salt('bf'))),
  ('lisa.park@wcpe.demo', 'Lisa Park', 'partner_manager', crypt('Password123!', gen_salt('bf'))),
  ('robert.kim@wcpe.demo', 'Robert Kim', 'compliance_officer', crypt('Password123!', gen_salt('bf'))),
  ('alex.johnson@borrower.demo', 'Alex Johnson', 'borrower', crypt('Password123!', gen_salt('bf')))
ON CONFLICT (email) DO UPDATE SET
  full_name = EXCLUDED.full_name,
  role = EXCLUDED.role,
  password_hash = EXCLUDED.password_hash,
  enabled = TRUE,
  updated_at = NOW();
