create index if not exists catalog_vc_artifact_asof_idx
  on catalog.catalog_version_control (tenant_id, artifact_type, artifact_code, status, effective_start, effective_end);

create unique index if not exists catalog_vc_artifact_code_version_uq
  on catalog.catalog_version_control (tenant_id, artifact_type, artifact_code, version_number);

do $$ begin
  alter table catalog.catalog_version_validation_issue
    add constraint catalog_version_issue_severity_ck check (severity in ('ERROR','WARNING','INFO'));
exception when duplicate_object then null; end $$;
