CREATE TABLE scenario_analysis.what_if_batch_grid (
    tenant_id VARCHAR(128) NOT NULL,
    grid_id UUID NOT NULL,
    source_quote_id VARCHAR(256) NOT NULL,
    source_quote_version INT NOT NULL,
    grid_name VARCHAR(256) NOT NULL,
    status VARCHAR(40) NOT NULL CHECK (status IN ('QUEUED', 'RUNNING', 'PAUSED', 'FAILED', 'COMPLETED', 'CANCELLED')),
    axes_json JSONB NOT NULL DEFAULT '[]'::JSONB,
    request_hash VARCHAR(64) NOT NULL,
    result_hash VARCHAR(80),
    idempotency_key_hash VARCHAR(64),
    created_by VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, grid_id)
);

CREATE TABLE scenario_analysis.what_if_batch_cell (
    tenant_id VARCHAR(128) NOT NULL,
    grid_id UUID NOT NULL,
    cell_id UUID NOT NULL,
    x_axis_type VARCHAR(40) NOT NULL,
    x_value VARCHAR(128) NOT NULL,
    y_axis_type VARCHAR(40) NOT NULL,
    y_value VARCHAR(128) NOT NULL,
    z_axis_type VARCHAR(40),
    z_value VARCHAR(128),
    status VARCHAR(40) NOT NULL CHECK (status IN ('QUEUED', 'RUNNING', 'PRICED', 'INELIGIBLE', 'FAILED', 'CANCELLED', 'PAUSED')),
    variant_overrides_json JSONB NOT NULL DEFAULT '{}'::JSONB,
    rule_hits_json JSONB NOT NULL DEFAULT '[]'::JSONB,
    attempt_count INT NOT NULL DEFAULT 0,
    error_code VARCHAR(128),
    result_hash VARCHAR(80) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, grid_id, cell_id),
    FOREIGN KEY (tenant_id, grid_id) REFERENCES scenario_analysis.what_if_batch_grid(tenant_id, grid_id)
);

CREATE UNIQUE INDEX idx_batch_grid_tenant_idempotency
    ON scenario_analysis.what_if_batch_grid(tenant_id, idempotency_key_hash)
    WHERE idempotency_key_hash IS NOT NULL;

CREATE UNIQUE INDEX idx_batch_grid_cell_coordinates
    ON scenario_analysis.what_if_batch_cell(tenant_id, grid_id, x_value, y_value, COALESCE(z_value, ''));

CREATE INDEX idx_batch_grid_tenant_status
    ON scenario_analysis.what_if_batch_grid(tenant_id, status, updated_at DESC);
