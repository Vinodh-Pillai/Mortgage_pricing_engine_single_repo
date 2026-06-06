CREATE TABLE scenario_analysis.what_if_analysis (
    tenant_id VARCHAR(128) NOT NULL,
    analysis_id UUID NOT NULL,
    source_quote_id VARCHAR(256) NOT NULL,
    source_quote_version INT NOT NULL,
    pricing_config_version VARCHAR(128) NOT NULL,
    name VARCHAR(256) NOT NULL,
    reason_code VARCHAR(128) NOT NULL,
    status VARCHAR(40) NOT NULL CHECK (status IN ('SAVED', 'ARCHIVED')),
    version INT NOT NULL DEFAULT 1,
    visibility VARCHAR(40) NOT NULL CHECK (visibility IN ('private', 'team', 'tenant-read-only')),
    retention_category VARCHAR(128) NOT NULL,
    linked_to_quote_decision BOOLEAN NOT NULL DEFAULT FALSE,
    selected_variant_ids_json JSONB NOT NULL DEFAULT '[]'::JSONB,
    selected_grid_cell_ids_json JSONB NOT NULL DEFAULT '[]'::JSONB,
    tags_json JSONB NOT NULL DEFAULT '[]'::JSONB,
    request_hash VARCHAR(64) NOT NULL,
    analysis_hash VARCHAR(80) NOT NULL,
    notes_hash VARCHAR(80) NOT NULL,
    idempotency_key_hash VARCHAR(64),
    created_by VARCHAR(128) NOT NULL,
    updated_by VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, analysis_id)
);

CREATE TABLE scenario_analysis.what_if_analysis_share (
    tenant_id VARCHAR(128) NOT NULL,
    analysis_id UUID NOT NULL,
    target_type VARCHAR(40) NOT NULL CHECK (target_type IN ('user', 'team')),
    target_id VARCHAR(256) NOT NULL,
    role VARCHAR(40) NOT NULL CHECK (role IN ('read', 'write')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, analysis_id, target_type, target_id),
    FOREIGN KEY (tenant_id, analysis_id) REFERENCES scenario_analysis.what_if_analysis(tenant_id, analysis_id)
);

CREATE TABLE scenario_analysis.what_if_analysis_note_history (
    tenant_id VARCHAR(128) NOT NULL,
    analysis_id UUID NOT NULL,
    version INT NOT NULL,
    notes_text TEXT NOT NULL DEFAULT '',
    notes_hash VARCHAR(80) NOT NULL,
    updated_by VARCHAR(128) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, analysis_id, version),
    FOREIGN KEY (tenant_id, analysis_id) REFERENCES scenario_analysis.what_if_analysis(tenant_id, analysis_id)
);

CREATE UNIQUE INDEX idx_saved_analysis_tenant_idempotency
    ON scenario_analysis.what_if_analysis(tenant_id, idempotency_key_hash)
    WHERE idempotency_key_hash IS NOT NULL;

CREATE UNIQUE INDEX idx_saved_analysis_active_name
    ON scenario_analysis.what_if_analysis(tenant_id, source_quote_id, created_by, lower(name))
    WHERE status = 'SAVED';

CREATE INDEX idx_saved_analysis_tenant_status
    ON scenario_analysis.what_if_analysis(tenant_id, status, updated_at DESC);

CREATE INDEX idx_saved_analysis_tenant_quote
    ON scenario_analysis.what_if_analysis(tenant_id, source_quote_id, updated_at DESC);
