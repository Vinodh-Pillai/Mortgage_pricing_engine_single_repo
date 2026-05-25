-- Dedicated Investor table
CREATE TABLE catalog.investor (
    investor_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL,
    code        VARCHAR(32) NOT NULL,
    name        VARCHAR(160) NOT NULL,
    status      VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'
                 CHECK (status IN ('ACTIVE', 'SUSPENDED', 'RETIRED')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, code)
);
CREATE INDEX investor_status_idx ON catalog.investor (tenant_id, status);
COMMENT ON TABLE catalog.investor IS 'Canonical investor actor definition';

-- Dedicated Product table
CREATE TABLE catalog.product (
    product_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id  UUID NOT NULL,
    code       VARCHAR(32) NOT NULL,
    name       VARCHAR(160) NOT NULL,
    type       VARCHAR(32) NOT NULL DEFAULT 'CONVENTIONAL'
               CHECK (type IN ('CONVENTIONAL', 'FHA', 'VA', 'USDA', 'JUMBO')),
    status     VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'
               CHECK (status IN ('ACTIVE', 'SUSPENDED', 'RETIRED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, code)
);
CREATE INDEX product_status_idx ON catalog.product (tenant_id, status);
COMMENT ON TABLE catalog.product IS 'Canonical product definition';

-- Dedicated Channel table
CREATE TABLE catalog.channel (
    channel_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id  UUID NOT NULL,
    code       VARCHAR(32) NOT NULL,
    name       VARCHAR(160) NOT NULL,
    type       VARCHAR(32) NOT NULL DEFAULT 'RETAIL'
               CHECK (type IN ('RETAIL', 'BROKER', 'CORRESPONDENT', 'WHOLESALE', 'ONLINE')),
    status     VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'
               CHECK (status IN ('ACTIVE', 'SUSPENDED', 'RETIRED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, code)
);
CREATE INDEX channel_status_idx ON catalog.channel (tenant_id, status);
COMMENT ON TABLE catalog.channel IS 'Canonical channel definition';

-- Product Offering table (investor x product x channel relationship)
CREATE TABLE catalog.product_offering (
    offering_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID NOT NULL,
    investor_id   UUID NOT NULL REFERENCES catalog.investor(investor_id),
    product_id    UUID NOT NULL REFERENCES catalog.product(product_id),
    channel_id    UUID NOT NULL REFERENCES catalog.channel(channel_id),
    status        VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'
                  CHECK (status IN ('ACTIVE', 'SUSPENDED', 'RETIRED')),
    effective_date DATE NOT NULL,
    expiry_date   DATE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (expiry_date IS NULL OR expiry_date > effective_date),
    UNIQUE (tenant_id, investor_id, product_id, channel_id, effective_date)
);
CREATE INDEX offering_lookup_idx ON catalog.product_offering (tenant_id, investor_id, product_id, channel_id, status);
CREATE INDEX offering_effective_idx ON catalog.product_offering (tenant_id, status, effective_date DESC);
COMMENT ON TABLE catalog.product_offering IS 'Investor x Product x Channel offering relationship';

-- Data migration: backfill investor from investor_program
INSERT INTO catalog.investor (investor_id, tenant_id, code, name, status)
SELECT investor_id, tenant_id, investor_code, investor_name,
       CASE WHEN effective_to IS NOT NULL THEN 'RETIRED' ELSE 'ACTIVE' END
FROM catalog.investor_program
ON CONFLICT (tenant_id, code) DO NOTHING;

-- Data migration: backfill product from product_definition
INSERT INTO catalog.product (product_id, tenant_id, code, name, type, status)
SELECT product_id, tenant_id, product_code, product_name, product_family,
       CASE WHEN effective_to IS NOT NULL THEN 'RETIRED' ELSE 'ACTIVE' END
FROM catalog.product_definition pd
ON CONFLICT (tenant_id, code) DO NOTHING;
