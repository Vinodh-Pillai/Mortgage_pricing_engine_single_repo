alter table lock_freshness_checks
  add column if not exists idempotency_key varchar(160);

update lock_freshness_checks
set idempotency_key = 'legacy-' || check_id
where idempotency_key is null;

alter table lock_freshness_checks
  alter column idempotency_key set not null;

create unique index if not exists lock_freshness_checks_tenant_idempotency_idx
  on lock_freshness_checks (tenant_id, idempotency_key);
