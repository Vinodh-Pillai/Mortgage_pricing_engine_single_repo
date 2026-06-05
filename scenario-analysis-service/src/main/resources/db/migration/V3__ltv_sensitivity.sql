CREATE TABLE scenario_analysis.what_if_ltv_sensitivity_run (
    tenant_id UUID NOT NULL,
    analysis_id UUID NOT NULL,
    source_quote_id VARCHAR(256) NOT NULL,
    source_quote_version INT NOT NULL,
    status VARCHAR(40) NOT NULL CHECK (status IN ('COMPLETED', 'FAILED')),
    mode VARCHAR(40) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    result_hash VARCHAR(80),
    idempotency_key_hash VARCHAR(64),
    created_by VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, analysis_id)
);

CREATE TABLE scenario_analysis.what_if_ltv_sensitivity_row (
    tenant_id UUID NOT NULL,
    analysis_id UUID NOT NULL,
    variant_id UUID NOT NULL,
    axis_value_numeric NUMERIC(18, 5) NOT NULL,
    loan_amount NUMERIC(18, 2) NOT NULL CHECK (loan_amount > 0),
    down_payment_amount NUMERIC(18, 2) NOT NULL CHECK (down_payment_amount >= 0),
    ltv NUMERIC(9, 5) NOT NULL CHECK (ltv BETWEEN 0.00000 AND 9.99999),
    cltv NUMERIC(9, 5) NOT NULL CHECK (cltv BETWEEN 0.00000 AND 9.99999),
    hcltv NUMERIC(9, 5) NOT NULL CHECK (hcltv BETWEEN 0.00000 AND 9.99999),
    mi_summary_json JSONB NOT NULL DEFAULT '{}'::JSONB,
    thresholds_json JSONB NOT NULL DEFAULT '[]'::JSONB,
    result_hash VARCHAR(80) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, analysis_id, variant_id),
    FOREIGN KEY (tenant_id, analysis_id) REFERENCES scenario_analysis.what_if_ltv_sensitivity_run(tenant_id, analysis_id)
);

CREATE UNIQUE INDEX idx_ltv_sensitivity_tenant_idempotency
    ON scenario_analysis.what_if_ltv_sensitivity_run(tenant_id, idempotency_key_hash)
    WHERE idempotency_key_hash IS NOT NULL;

CREATE INDEX idx_ltv_sensitivity_tenant_status
    ON scenario_analysis.what_if_ltv_sensitivity_run(tenant_id, status, updated_at DESC);
