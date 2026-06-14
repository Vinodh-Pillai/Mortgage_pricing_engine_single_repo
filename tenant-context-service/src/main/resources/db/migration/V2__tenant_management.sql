CREATE SCHEMA IF NOT EXISTS tenant;

CREATE TABLE IF NOT EXISTS tenant.tenant (
  tenant_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_name VARCHAR(255) NOT NULL UNIQUE,
  display_name VARCHAR(255),
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING_ACTIVATION',
  logo_url VARCHAR(500),
  primary_color VARCHAR(7),
  secondary_color VARCHAR(7),
  contact_email VARCHAR(255),
  contact_phone VARCHAR(50),
  address_line1 VARCHAR(255),
  address_line2 VARCHAR(255),
  city VARCHAR(100),
  state VARCHAR(50),
  postal_code VARCHAR(20),
  country VARCHAR(2) DEFAULT 'US',
  nmls_id VARCHAR(50),
  created_at TIMESTAMPTZ DEFAULT NOW(),
  updated_at TIMESTAMPTZ DEFAULT NOW(),
  activated_at TIMESTAMPTZ,
  suspended_at TIMESTAMPTZ,
  deactivated_at TIMESTAMPTZ,
  created_by VARCHAR(160),
  updated_by VARCHAR(160),
  CONSTRAINT tenant_status_check CHECK (status IN ('PENDING_ACTIVATION', 'ACTIVE', 'SUSPENDED', 'DEACTIVATED')),
  CONSTRAINT tenant_primary_color_hex CHECK (primary_color IS NULL OR primary_color ~ '^#[0-9A-Fa-f]{6}$'),
  CONSTRAINT tenant_secondary_color_hex CHECK (secondary_color IS NULL OR secondary_color ~ '^#[0-9A-Fa-f]{6}$')
);

CREATE INDEX IF NOT EXISTS idx_tenant_status ON tenant.tenant(status);
CREATE INDEX IF NOT EXISTS idx_tenant_name ON tenant.tenant(tenant_name);
