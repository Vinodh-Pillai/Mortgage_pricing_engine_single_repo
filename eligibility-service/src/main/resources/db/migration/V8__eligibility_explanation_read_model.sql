-- PII-03-S09: persisted quote option eligibility explanation read model fields.

alter table eligibility.eligibility_explanation_read_model
    add column if not exists evaluation_id uuid,
    add column if not exists scenario_id uuid,
    add column if not exists scenario_version int not null default 1,
    add column if not exists product_code varchar(64),
    add column if not exists investor_code varchar(64),
    add column if not exists product_version_id uuid,
    add column if not exists rule_version_graph_hash varchar(128);

create index if not exists eligibility_explanation_quote_idx
    on eligibility.eligibility_explanation_read_model (tenant_id, quote_id);

create index if not exists eligibility_explanation_rules_gin_idx
    on eligibility.eligibility_explanation_read_model using gin (rules_json);
