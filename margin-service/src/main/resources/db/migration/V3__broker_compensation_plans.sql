ALTER TABLE compensation_plan
    DROP CONSTRAINT IF EXISTS chk_compensation_plan_type,
    ADD CONSTRAINT chk_compensation_plan_type CHECK (plan_type IN ('LO', 'BROKER'));

ALTER TABLE compensation_plan
    DROP CONSTRAINT IF EXISTS chk_compensation_plan_status,
    ADD CONSTRAINT chk_compensation_plan_status CHECK (status IN ('DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'PUBLISHED', 'SUSPENDED', 'ACTIVE', 'INACTIVE', 'ARCHIVED'));

ALTER TABLE compensation_plan_version
    DROP CONSTRAINT IF EXISTS chk_compensation_plan_version_approval_status,
    ADD CONSTRAINT chk_compensation_plan_version_approval_status CHECK (approval_status IN ('DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'PUBLISHED', 'REJECTED', 'RETIRED'));

ALTER TABLE compensation_rule
    ADD COLUMN IF NOT EXISTS payment_responsibility varchar(40),
    ADD COLUMN IF NOT EXISTS eligible_channels jsonb NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS disclosure_label varchar(255);

ALTER TABLE compensation_rule
    ADD CONSTRAINT chk_compensation_rule_payment_responsibility CHECK (
        payment_responsibility IS NULL OR payment_responsibility IN ('LENDER_PAID', 'BORROWER_PAID')
    );

ALTER TABLE compensation_assignment
    DROP CONSTRAINT IF EXISTS chk_compensation_assignment_payee_type,
    ADD CONSTRAINT chk_compensation_assignment_payee_type CHECK (payee_type IN ('LO', 'BROKER'));

CREATE INDEX IF NOT EXISTS idx_compensation_rule_payment_responsibility
    ON compensation_rule (tenant_id, compensation_plan_version_id, payment_responsibility);

CREATE INDEX IF NOT EXISTS idx_compensation_rule_eligible_channels_gin
    ON compensation_rule USING gin (eligible_channels);

CREATE INDEX IF NOT EXISTS idx_compensation_assignment_broker_channel
    ON compensation_assignment (tenant_id, payee_type, payee_id, (channel_scope->>'channel'), effective_from, effective_to);
