-- Product overlay table (S07)
CREATE TABLE catalog.product_overlay (
    overlay_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID NOT NULL,
    product_code  VARCHAR(32) NOT NULL,
    attribute     VARCHAR(64) NOT NULL,
    override_value VARCHAR(128) NOT NULL,
    effective_date DATE NOT NULL,
    expiry_date   DATE,
    reason        TEXT NOT NULL,
    status        VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'
                  CHECK (status IN ('ACTIVE', 'SUSPENDED', 'RETIRED')),
    created_by    VARCHAR(128) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, product_code, attribute, effective_date, created_by),
    CHECK (expiry_date IS NULL OR expiry_date > effective_date)
);
CREATE INDEX overlay_effective_idx ON catalog.product_overlay (tenant_id, product_code, status, effective_date DESC, expiry_date);
COMMENT ON TABLE catalog.product_overlay IS 'Product attribute overlays (S07)';

-- Version diff materialized view (S09)
CREATE MATERIALIZED VIEW catalog.version_diff_cache AS
SELECT
    gen_random_uuid() AS cache_id,
    vc_a.tenant_id,
    vc_a.version_number AS version_a,
    vc_b.version_number AS version_b,
    vc_a.artifact_type,
    CASE
        WHEN vc_b.artifact_code IS NULL THEN 'REMOVED'
        WHEN vc_a.artifact_code IS NULL THEN 'ADDED'
        WHEN vc_a.snapshot_json != vc_b.snapshot_json THEN 'MODIFIED'
    END AS diff_type,
    COALESCE(vc_a.artifact_code, vc_b.artifact_code) AS artifact_code,
    now() AS computed_at
FROM catalog.catalog_version_control vc_a
FULL OUTER JOIN catalog.catalog_version_control vc_b
    ON vc_a.tenant_id = vc_b.tenant_id
    AND vc_a.artifact_type = vc_b.artifact_type
    AND vc_a.artifact_code = vc_b.artifact_code
    AND vc_b.version_number = vc_a.version_number + 1
WHERE vc_a.status IN ('PUBLISHED', 'ACTIVE')
AND vc_b.status IN ('PUBLISHED', 'ACTIVE')
WITH DATA;
CREATE UNIQUE INDEX version_diff_cache_unique ON catalog.version_diff_cache (cache_id);
REFRESH MATERIALIZED VIEW catalog.version_diff_cache;
