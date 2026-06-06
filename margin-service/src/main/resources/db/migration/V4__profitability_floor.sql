ALTER TABLE margin_policy
    DROP CONSTRAINT IF EXISTS uq_margin_policy_name,
    ADD CONSTRAINT uq_margin_policy_name UNIQUE (tenant_id, policy_type, name);

ALTER TABLE margin_rule
    ADD COLUMN IF NOT EXISTS floor_basis varchar(40),
    ADD COLUMN IF NOT EXISTS threshold_ref varchar(256),
    ADD COLUMN IF NOT EXISTS floor_action varchar(40),
    ADD COLUMN IF NOT EXISTS exception_route_ref varchar(256);

ALTER TABLE margin_rule
    DROP CONSTRAINT IF EXISTS ck_margin_rule_profitability_basis,
    ADD CONSTRAINT ck_margin_rule_profitability_basis CHECK (
        floor_basis IS NULL OR floor_basis IN ('NET_PRICE', 'NET_MARGIN_BPS', 'DOLLAR_PROFIT')
    );

ALTER TABLE margin_rule
    DROP CONSTRAINT IF EXISTS ck_margin_rule_profitability_action,
    ADD CONSTRAINT ck_margin_rule_profitability_action CHECK (
        floor_action IS NULL OR floor_action IN ('BLOCK', 'WARN', 'REQUIRE_EXCEPTION')
    );

CREATE TABLE profitability_evaluation (
    tenant_id uuid NOT NULL,
    evaluation_id uuid PRIMARY KEY,
    quote_id varchar(128) NOT NULL,
    quote_option_id varchar(128) NOT NULL,
    policy_id uuid NOT NULL REFERENCES margin_policy(policy_id),
    policy_version_id uuid NOT NULL REFERENCES margin_policy_version(version_id),
    basis varchar(40) NOT NULL,
    computed_value numeric(18, 6) NOT NULL,
    threshold_ref varchar(256) NOT NULL,
    action varchar(40) NOT NULL,
    decision varchar(80) NOT NULL,
    replay_hash varchar(128) NOT NULL,
    correlation_id varchar(128) NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT ck_profitability_evaluation_basis CHECK (basis IN ('NET_PRICE', 'NET_MARGIN_BPS', 'DOLLAR_PROFIT')),
    CONSTRAINT ck_profitability_evaluation_action CHECK (action IN ('BLOCK', 'WARN', 'REQUIRE_EXCEPTION')),
    CONSTRAINT ck_profitability_evaluation_decision CHECK (decision IN ('PASS', 'EXCLUDED', 'INCLUDED_WITH_WARNING', 'NON_BINDABLE_EXCEPTION_REQUIRED'))
);

CREATE INDEX idx_profitability_evaluation_quote
    ON profitability_evaluation (tenant_id, quote_option_id, created_at DESC);

CREATE INDEX idx_profitability_evaluation_policy
    ON profitability_evaluation (tenant_id, policy_version_id, decision, created_at DESC);
