CREATE TABLE scenario_analysis.what_if_product_comparison_run (
    tenant_id VARCHAR(128) NOT NULL,
    analysis_id UUID NOT NULL,
    source_quote_id VARCHAR(256) NOT NULL,
    source_quote_version INT NOT NULL,
    status VARCHAR(64) NOT NULL CHECK (status IN ('COMPLETED', 'COMPLETED_WITH_DEPENDENCY_GAPS', 'FAILED')),
    sensitivity_axis VARCHAR(40) NOT NULL CHECK (sensitivity_axis = 'PRODUCT'),
    baseline_product_id VARCHAR(256),
    request_hash VARCHAR(64) NOT NULL,
    result_hash VARCHAR(80),
    idempotency_key_hash VARCHAR(64),
    created_by VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, analysis_id)
);

CREATE TABLE scenario_analysis.what_if_product_comparison_row (
    tenant_id VARCHAR(128) NOT NULL,
    analysis_id UUID NOT NULL,
    variant_id UUID NOT NULL,
    product_id VARCHAR(256) NOT NULL,
    product_version VARCHAR(128),
    investor_id VARCHAR(256),
    product_family VARCHAR(128),
    term_months INT,
    amortization_type VARCHAR(64),
    eligibility VARCHAR(40) NOT NULL,
    pricing_summary_json JSONB NOT NULL DEFAULT '{}'::JSONB,
    payment_summary_json JSONB NOT NULL DEFAULT '{}'::JSONB,
    apr_summary_json JSONB NOT NULL DEFAULT '{}'::JSONB,
    deltas_json JSONB NOT NULL DEFAULT '{}'::JSONB,
    rule_hits_json JSONB NOT NULL DEFAULT '[]'::JSONB,
    result_hash VARCHAR(80) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, analysis_id, variant_id),
    FOREIGN KEY (tenant_id, analysis_id) REFERENCES scenario_analysis.what_if_product_comparison_run(tenant_id, analysis_id)
);

CREATE UNIQUE INDEX idx_product_comparison_tenant_idempotency
    ON scenario_analysis.what_if_product_comparison_run(tenant_id, idempotency_key_hash)
    WHERE idempotency_key_hash IS NOT NULL;

CREATE INDEX idx_product_comparison_product_investor
    ON scenario_analysis.what_if_product_comparison_row(tenant_id, analysis_id, product_id, investor_id);

CREATE INDEX idx_product_comparison_source_quote
    ON scenario_analysis.what_if_product_comparison_run(tenant_id, source_quote_id, created_at DESC);
