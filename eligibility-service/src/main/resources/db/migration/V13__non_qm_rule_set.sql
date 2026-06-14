CREATE SCHEMA IF NOT EXISTS eligibility;

CREATE TABLE IF NOT EXISTS eligibility.non_qm_rule_set (
    rule_set_id VARCHAR(120) PRIMARY KEY,
    product_code VARCHAR(64) NOT NULL,
    non_qm_type VARCHAR(32) NOT NULL,
    investor_code VARCHAR(64) NOT NULL,
    channel_code VARCHAR(64) NOT NULL,
    version INT NOT NULL,
    effective_start TIMESTAMPTZ,
    effective_end TIMESTAMPTZ,
    rule_document JSONB NOT NULL,
    source_system VARCHAR(32) NOT NULL,
    source_ref VARCHAR(160),
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(product_code, investor_code, channel_code, version)
);

CREATE INDEX IF NOT EXISTS idx_nonqm_rules_lookup ON eligibility.non_qm_rule_set
    (product_code, investor_code, channel_code, status, effective_start);
CREATE INDEX IF NOT EXISTS idx_nonqm_rules_doc ON eligibility.non_qm_rule_set USING GIN (rule_document);
