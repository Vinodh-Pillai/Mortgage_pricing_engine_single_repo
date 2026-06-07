CREATE TABLE event_contract_versions (
  event_type varchar(160) NOT NULL,
  event_version int NOT NULL,
  schema_ref varchar(220) NOT NULL,
  json_schema jsonb NOT NULL,
  status varchar(24) NOT NULL,
  effective_from timestamptz NOT NULL,
  deprecated_at timestamptz,
  owner varchar(120) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT NOW(),
  CONSTRAINT pk_event_contract_versions PRIMARY KEY (event_type, event_version),
  CONSTRAINT chk_event_contract_versions_status CHECK (status IN ('DRAFT', 'ACTIVE', 'DEPRECATED', 'RETIRED'))
);

CREATE INDEX idx_event_contract_versions_status_effective_from
  ON event_contract_versions (status, effective_from DESC);
