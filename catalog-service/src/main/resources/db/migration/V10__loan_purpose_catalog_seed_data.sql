create or replace function catalog.seed_mvp_loan_purposes(p_tenant_id uuid, p_catalog_id uuid default null)
returns integer
language plpgsql
as $$
declare
  v_catalog_id uuid;
  v_seeded integer := 0;
  v_seed record;
  v_entry_id uuid;
  v_snapshot jsonb;
begin
  if p_tenant_id is null then
    raise exception 'TENANT_ID_REQUIRED';
  end if;

  if p_catalog_id is null then
    select catalog_id into v_catalog_id
    from catalog.product_catalog
    where tenant_id = p_tenant_id
    order by updated_at desc
    limit 1;

    if v_catalog_id is null then
      v_catalog_id := gen_random_uuid();
      insert into catalog.product_catalog(tenant_id, catalog_id, version, status, replay_hash)
      values (p_tenant_id, v_catalog_id, 1, 'DRAFT', 'seed:loan-purpose:' || v_catalog_id::text);
    end if;
  else
    v_catalog_id := p_catalog_id;
  end if;

  for v_seed in
    select * from (values
      ('PURCHASE', 'Purchase', 'PURCHASE', false, false, false, true, '["PUR"]'::jsonb),
      ('RATE_TERM_REFI', 'Rate/Term Refinance', 'REFINANCE', true, false, true, true, '["RATE_TERM_REFINANCE"]'::jsonb),
      ('CASH_OUT_REFI', 'Cash-Out Refinance', 'REFINANCE', true, true, true, true, '["CASH_OUT_REFINANCE"]'::jsonb),
      ('CONSTRUCTION_TO_PERMANENT', 'Construction-to-Permanent', 'CONSTRUCTION', true, false, true, false, '["CONSTRUCTION_PERM"]'::jsonb)
    ) as seeds(code, label, category, is_refinance, is_cash_out, requires_existing_lien, eligible_for_conventional, aliases)
  loop
    if not exists (
      select 1 from catalog.reference_entry
      where tenant_id = p_tenant_id and catalog_type = 'LOAN_PURPOSE' and code = v_seed.code
    ) then
      v_entry_id := gen_random_uuid();
      v_snapshot := jsonb_build_object(
        'catalogType', 'LOAN_PURPOSE',
        'code', v_seed.code,
        'label', v_seed.label,
        'category', v_seed.category,
        'attributes', jsonb_build_object(
          'isRefinance', v_seed.is_refinance,
          'isCashOut', v_seed.is_cash_out,
          'requiresExistingLien', v_seed.requires_existing_lien,
          'eligibleForConventional', v_seed.eligible_for_conventional,
          'agencyAliases', v_seed.aliases
        ),
        'effectiveFrom', '2026-01-01',
        'effectiveTo', null
      );

      insert into catalog.reference_entry(tenant_id, entry_id, catalog_id, catalog_type, code, label, category, attributes, effective_from, effective_to)
      values (
        p_tenant_id,
        v_entry_id,
        v_catalog_id,
        'LOAN_PURPOSE',
        v_seed.code,
        v_seed.label,
        v_seed.category,
        v_snapshot->'attributes',
        date '2026-01-01',
        null
      );

      insert into catalog.catalog_version_control(
        tenant_id,
        version_control_id,
        catalog_id,
        artifact_type,
        artifact_id,
        artifact_code,
        version_number,
        status,
        effective_start,
        effective_end,
        config_hash,
        snapshot_json,
        created_by
      ) values (
        p_tenant_id,
        gen_random_uuid(),
        v_catalog_id,
        'LOAN_PURPOSE',
        v_entry_id,
        v_seed.code,
        1,
        'DRAFT',
        date '2026-01-01',
        null,
        'sha256:' || repeat('0', 32) || md5(v_snapshot::text),
        v_snapshot,
        'loan-purpose-seed'
      );

      v_seeded := v_seeded + 1;
    end if;
  end loop;

  if v_seeded > 0 then
    update catalog.product_catalog
    set version = version + 1,
        row_version = row_version + 1,
        replay_hash = 'seed:loan-purpose:' || v_catalog_id::text || ':' || v_seeded::text,
        updated_at = now()
    where tenant_id = p_tenant_id and catalog_id = v_catalog_id;
  end if;

  return v_seeded;
end $$;
