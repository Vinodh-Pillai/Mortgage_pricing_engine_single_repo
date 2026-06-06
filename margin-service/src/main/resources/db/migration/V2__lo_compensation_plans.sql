CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE compensation_plan (
    tenant_id varchar(64) NOT NULL,
    compensation_plan_id uuid NOT NULL,
    plan_type varchar(32) NOT NULL,
    plan_name varchar(255) NOT NULL,
    status varchar(32) NOT NULL,
    current_version_id uuid,
    created_by varchar(128) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(128) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_compensation_plan PRIMARY KEY (tenant_id, compensation_plan_id),
    CONSTRAINT uq_compensation_plan_name UNIQUE (tenant_id, plan_type, plan_name),
    CONSTRAINT chk_compensation_plan_type CHECK (plan_type IN ('LO')),
    CONSTRAINT chk_compensation_plan_status CHECK (status IN ('DRAFT', 'ACTIVE', 'INACTIVE', 'ARCHIVED'))
);

CREATE TABLE compensation_plan_version (
    tenant_id varchar(64) NOT NULL,
    compensation_plan_version_id uuid NOT NULL,
    compensation_plan_id uuid NOT NULL,
    version_number integer NOT NULL,
    effective_from timestamptz NOT NULL,
    effective_to timestamptz,
    config_hash varchar(128) NOT NULL,
    approval_status varchar(32) NOT NULL,
    approved_by varchar(128),
    approved_at timestamptz,
    approval_reference varchar(255),
    created_by varchar(128) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(128) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_compensation_plan_version PRIMARY KEY (tenant_id, compensation_plan_version_id),
    CONSTRAINT fk_compensation_plan_version_plan FOREIGN KEY (tenant_id, compensation_plan_id)
        REFERENCES compensation_plan (tenant_id, compensation_plan_id),
    CONSTRAINT uq_compensation_plan_version_number UNIQUE (tenant_id, compensation_plan_id, version_number),
    CONSTRAINT uq_compensation_plan_version_hash UNIQUE (tenant_id, compensation_plan_id, config_hash),
    CONSTRAINT chk_compensation_plan_version_number CHECK (version_number > 0),
    CONSTRAINT chk_compensation_plan_version_window CHECK (effective_to IS NULL OR effective_to > effective_from),
    CONSTRAINT chk_compensation_plan_version_approval_status CHECK (approval_status IN ('DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'RETIRED')),
    CONSTRAINT chk_compensation_plan_version_approval_metadata CHECK (
        (approval_status = 'APPROVED' AND approved_by IS NOT NULL AND approved_at IS NOT NULL)
        OR approval_status <> 'APPROVED'
    )
);

ALTER TABLE compensation_plan
    ADD CONSTRAINT fk_compensation_plan_current_version FOREIGN KEY (tenant_id, current_version_id)
        REFERENCES compensation_plan_version (tenant_id, compensation_plan_version_id);

CREATE TABLE compensation_rule (
    tenant_id varchar(64) NOT NULL,
    compensation_rule_id uuid NOT NULL,
    compensation_plan_version_id uuid NOT NULL,
    basis varchar(32) NOT NULL,
    amount_ref varchar(255),
    amount_expression jsonb NOT NULL DEFAULT '{}'::jsonb,
    min_ref varchar(255),
    max_ref varchar(255),
    cap_ref varchar(255),
    floor_ref varchar(255),
    reason_code varchar(64) NOT NULL,
    visibility_classification varchar(64) NOT NULL,
    sort_order integer NOT NULL DEFAULT 0,
    created_by varchar(128) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(128) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_compensation_rule PRIMARY KEY (tenant_id, compensation_rule_id),
    CONSTRAINT fk_compensation_rule_version FOREIGN KEY (tenant_id, compensation_plan_version_id)
        REFERENCES compensation_plan_version (tenant_id, compensation_plan_version_id),
    CONSTRAINT uq_compensation_rule_reason UNIQUE (tenant_id, compensation_plan_version_id, reason_code),
    CONSTRAINT chk_compensation_rule_basis CHECK (basis IN ('LOAN_AMOUNT', 'PRICE_POINTS')),
    CONSTRAINT chk_compensation_rule_amount CHECK (amount_ref IS NOT NULL OR amount_expression <> '{}'::jsonb),
    CONSTRAINT chk_compensation_rule_visibility CHECK (visibility_classification <> ''),
    CONSTRAINT chk_compensation_rule_sort_order CHECK (sort_order >= 0)
);

CREATE TABLE compensation_assignment (
    tenant_id varchar(64) NOT NULL,
    compensation_assignment_id uuid NOT NULL,
    compensation_plan_version_id uuid NOT NULL,
    payee_type varchar(32) NOT NULL,
    payee_id varchar(128) NOT NULL,
    branch_scope jsonb NOT NULL DEFAULT '{}'::jsonb,
    channel_scope jsonb NOT NULL DEFAULT '{}'::jsonb,
    product_scope jsonb NOT NULL DEFAULT '{}'::jsonb,
    effective_from timestamptz NOT NULL,
    effective_to timestamptz,
    created_by varchar(128) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(128) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_compensation_assignment PRIMARY KEY (tenant_id, compensation_assignment_id),
    CONSTRAINT fk_compensation_assignment_version FOREIGN KEY (tenant_id, compensation_plan_version_id)
        REFERENCES compensation_plan_version (tenant_id, compensation_plan_version_id),
    CONSTRAINT chk_compensation_assignment_payee_type CHECK (payee_type IN ('LO')),
    CONSTRAINT chk_compensation_assignment_window CHECK (effective_to IS NULL OR effective_to > effective_from),
    CONSTRAINT ex_compensation_assignment_no_overlap EXCLUDE USING gist (
        tenant_id WITH =,
        payee_type WITH =,
        payee_id WITH =,
        (branch_scope::text) WITH =,
        (channel_scope::text) WITH =,
        (product_scope::text) WITH =,
        tstzrange(effective_from, COALESCE(effective_to, 'infinity'::timestamptz), '[)') WITH &&
    )
);

CREATE INDEX idx_compensation_plan_status
    ON compensation_plan (tenant_id, status, plan_type);

CREATE INDEX idx_compensation_plan_current_version
    ON compensation_plan (tenant_id, current_version_id);

CREATE INDEX idx_compensation_plan_version_plan
    ON compensation_plan_version (tenant_id, compensation_plan_id, version_number DESC);

CREATE INDEX idx_compensation_plan_version_effective_window
    ON compensation_plan_version (tenant_id, compensation_plan_id, effective_from, effective_to);

CREATE INDEX idx_compensation_plan_version_approval
    ON compensation_plan_version (tenant_id, approval_status, approved_at);

CREATE INDEX idx_compensation_rule_version
    ON compensation_rule (tenant_id, compensation_plan_version_id, sort_order);

CREATE INDEX idx_compensation_rule_basis
    ON compensation_rule (tenant_id, basis);

CREATE INDEX idx_compensation_rule_reason_code
    ON compensation_rule (tenant_id, reason_code);

CREATE INDEX idx_compensation_rule_amount_expression_gin
    ON compensation_rule USING gin (amount_expression);

CREATE INDEX idx_compensation_assignment_version
    ON compensation_assignment (tenant_id, compensation_plan_version_id);

CREATE INDEX idx_compensation_assignment_payee
    ON compensation_assignment (tenant_id, payee_type, payee_id, effective_from, effective_to);

CREATE INDEX idx_compensation_assignment_branch_scope_gin
    ON compensation_assignment USING gin (branch_scope);

CREATE INDEX idx_compensation_assignment_channel_scope_gin
    ON compensation_assignment USING gin (channel_scope);

CREATE INDEX idx_compensation_assignment_product_scope_gin
    ON compensation_assignment USING gin (product_scope);
