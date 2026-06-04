CREATE SCHEMA IF NOT EXISTS scenario_analysis;

CREATE TABLE scenario_analysis.what_if_variant (
    tenant_id UUID NOT NULL,
    variant_id UUID NOT NULL,
    source_quote_id VARCHAR(256) NOT NULL,
    source_quote_version INT NOT NULL,
    source_quote_snapshot_id VARCHAR(256),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'PRICING', 'PRICED', 'FAILED', 'SAVED', 'EXPIRED', 'ARCHIVED')),
    reason_code VARCHAR(40),
    variant_name VARCHAR(256),
    notes TEXT,
    pricing_as_of TIMESTAMPTZ,
    variant_version INT NOT NULL DEFAULT 1,
    input_hash VARCHAR(64),
    result_hash VARCHAR(64),
    idempotency_key_hash VARCHAR(64) UNIQUE,
    created_by VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, variant_id)
);

CREATE TABLE scenario_analysis.what_if_variant_change (
    tenant_id UUID NOT NULL,
    change_id UUID NOT NULL,
    variant_id UUID NOT NULL,
    field_path VARCHAR(128) NOT NULL,
    old_value TEXT,
    new_value TEXT NOT NULL,
    value_type VARCHAR(40) NOT NULL DEFAULT 'STRING',
    changed_by VARCHAR(128) NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, change_id),
    FOREIGN KEY (tenant_id, variant_id) REFERENCES scenario_analysis.what_if_variant(tenant_id, variant_id)
);

CREATE TABLE scenario_analysis.what_if_input_snapshot (
    tenant_id UUID NOT NULL,
    snapshot_id UUID NOT NULL,
    variant_id UUID NOT NULL,
    snapshot_jsonb JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, snapshot_id),
    FOREIGN KEY (tenant_id, variant_id) REFERENCES scenario_analysis.what_if_variant(tenant_id, variant_id)
);

CREATE TABLE scenario_analysis.what_if_outbox (
    tenant_id UUID NOT NULL,
    event_id UUID NOT NULL,
    variant_id UUID NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    payload_jsonb JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, event_id),
    FOREIGN KEY (tenant_id, variant_id) REFERENCES scenario_analysis.what_if_variant(tenant_id, variant_id)
);

CREATE INDEX idx_variant_tenant_status ON scenario_analysis.what_if_variant(tenant_id, status, updated_at DESC);
CREATE INDEX idx_variant_idempotency ON scenario_analysis.what_if_variant(idempotency_key_hash);
CREATE INDEX idx_variant_change_variant ON scenario_analysis.what_if_variant_change(tenant_id, variant_id);
CREATE INDEX idx_snapshot_variant ON scenario_analysis.what_if_input_snapshot(tenant_id, variant_id);
CREATE INDEX idx_outbox_variant_status ON scenario_analysis.what_if_outbox(tenant_id, variant_id, status);
