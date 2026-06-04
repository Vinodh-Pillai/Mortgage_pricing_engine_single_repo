CREATE TABLE scenario_analysis.what_if_sensitivity_run (
    tenant_id UUID NOT NULL,
    analysis_id UUID NOT NULL,
    run_id UUID NOT NULL,
    source_quote_id VARCHAR(256) NOT NULL,
    source_quote_version INT NOT NULL,
    axis_type VARCHAR(40) NOT NULL CHECK (axis_type = 'FICO'),
    status VARCHAR(40) NOT NULL CHECK (status IN ('REQUESTED', 'COMPLETED', 'FAILED')),
    requested_values JSONB NOT NULL,
    completed_count INT NOT NULL DEFAULT 0,
    failed_count INT NOT NULL DEFAULT 0,
    pricing_config_version VARCHAR(128),
    request_hash VARCHAR(64) NOT NULL,
    result_hash VARCHAR(80),
    idempotency_key_hash VARCHAR(64),
    created_by VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, analysis_id),
    UNIQUE (tenant_id, run_id)
);

CREATE TABLE scenario_analysis.what_if_sensitivity_row (
    tenant_id UUID NOT NULL,
    analysis_id UUID NOT NULL,
    variant_id UUID NOT NULL,
    axis_value_numeric INT NOT NULL CHECK (axis_value_numeric BETWEEN 300 AND 850),
    eligibility_status VARCHAR(40) NOT NULL,
    pricing_summary JSONB NOT NULL DEFAULT '{}'::JSONB,
    comparison_json JSONB NOT NULL DEFAULT '{}'::JSONB,
    result_hash VARCHAR(80) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, analysis_id, variant_id),
    FOREIGN KEY (tenant_id, analysis_id) REFERENCES scenario_analysis.what_if_sensitivity_run(tenant_id, analysis_id)
);

ALTER TABLE scenario_analysis.what_if_variant
    ADD COLUMN sensitivity_axis VARCHAR(40),
    ADD COLUMN axis_value VARCHAR(128);

CREATE UNIQUE INDEX idx_sensitivity_tenant_idempotency
    ON scenario_analysis.what_if_sensitivity_run(tenant_id, idempotency_key_hash)
    WHERE idempotency_key_hash IS NOT NULL;

CREATE INDEX idx_sensitivity_tenant_status
    ON scenario_analysis.what_if_sensitivity_run(tenant_id, status, updated_at DESC);

CREATE INDEX idx_sensitivity_row_axis
    ON scenario_analysis.what_if_sensitivity_row(tenant_id, analysis_id, axis_value_numeric);
