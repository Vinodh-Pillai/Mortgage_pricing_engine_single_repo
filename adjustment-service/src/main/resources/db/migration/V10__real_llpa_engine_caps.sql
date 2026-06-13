alter table adjustment_rule_books
    add column if not exists max_total_points_delta numeric,
    add column if not exists min_total_points_delta numeric;

alter table adjustment_rules
    add column if not exists max_output numeric,
    add column if not exists min_output numeric;

create index if not exists adjustment_rule_books_selector_published_idx
    on adjustment_rule_books (tenant_id, status, product_family, published_at desc);
