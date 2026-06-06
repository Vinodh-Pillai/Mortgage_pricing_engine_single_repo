CREATE TABLE scenario_analysis.what_if_lock_period_comparison_run (
    tenant_id VARCHAR(128) NOT NULL,
    analysis_id UUID NOT NULL,
    source_quote_id VARCHAR(256) NOT NULL,
    source_quote_version INT NOT NULL,
    status VARCHAR(64) NOT NULL CHECK (status IN ('COMPLETED', 'COMPLETED_WITH_DEPENDENCY_GAPS', 'FAILED')),
    sensitivity_axis VARCHAR(40) NOT NULL CHECK (sensitivity_axis = 'LOCK_PERIOD'),
    baseline_variant_id VARCHAR(256) NOT NULL,
    lock_start_date DATE NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    result_hash VARCHAR(80),
    idempotency_key_hash VARCHAR(64),
    lock_policy_version VARCHAR(128),
    holiday_calendar_version VARCHAR(128),
    created_by VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, analysis_id)
);

CREATE TABLE scenario_analysis.what_if_lock_period_comparison_row (
    tenant_id VARCHAR(128) NOT NULL,
    analysis_id UUID NOT NULL,
    variant_id UUID NOT NULL,
    lock_period_days INT NOT NULL CHECK (lock_period_days > 0),
    lock_start_date DATE NOT NULL,
    expiration_date DATE NOT NULL,
    lock_adjustment_bps INT,
    price_summary_json JSONB NOT NULL DEFAULT '{}'::JSONB,
    extension_fee_summary_json JSONB NOT NULL DEFAULT '{}'::JSONB,
    eligibility VARCHAR(40) NOT NULL,
    deltas_json JSONB NOT NULL DEFAULT '{}'::JSONB,
    rule_hits_json JSONB NOT NULL DEFAULT '[]'::JSONB,
    result_hash VARCHAR(80) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, analysis_id, variant_id),
    FOREIGN KEY (tenant_id, analysis_id) REFERENCES scenario_analysis.what_if_lock_period_comparison_run(tenant_id, analysis_id)
);

CREATE UNIQUE INDEX idx_lock_period_comparison_tenant_idempotency
    ON scenario_analysis.what_if_lock_period_comparison_run(tenant_id, idempotency_key_hash)
    WHERE idempotency_key_hash IS NOT NULL;

CREATE INDEX idx_lock_period_comparison_tenant_status
    ON scenario_analysis.what_if_lock_period_comparison_run(tenant_id, status, updated_at DESC);
