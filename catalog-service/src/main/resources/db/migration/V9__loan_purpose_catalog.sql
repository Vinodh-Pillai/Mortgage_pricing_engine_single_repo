do $$ begin
  alter table catalog.reference_entry
    add constraint loan_purpose_cash_out_refi_ck check (
      catalog_type <> 'LOAN_PURPOSE'
      or coalesce((attributes->>'isCashOut')::boolean, false) = false
      or coalesce((attributes->>'isRefinance')::boolean, false) = true
    );
exception when duplicate_object then null; end $$;

do $$ begin
  alter table catalog.reference_entry
    add constraint loan_purpose_purchase_lien_ck check (
      catalog_type <> 'LOAN_PURPOSE'
      or code <> 'PURCHASE'
      or coalesce((attributes->>'requiresExistingLien')::boolean, false) = false
    );
exception when duplicate_object then null; end $$;

create index if not exists reference_entry_loan_purpose_alias_gin
  on catalog.reference_entry using gin (attributes)
  where catalog_type = 'LOAN_PURPOSE';
