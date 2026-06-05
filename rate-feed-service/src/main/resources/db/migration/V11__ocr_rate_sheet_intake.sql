create table rate_feed.ocr_extraction (
  tenant_id uuid not null,
  ocr_extraction_id uuid not null,
  batch_id uuid not null,
  ocr_profile_id uuid not null,
  engine_version varchar(128) not null,
  status varchar(40) not null,
  page_count integer not null default 0,
  min_confidence numeric(5,4),
  review_required boolean not null default true,
  created_at timestamptz not null default now(),
  completed_at timestamptz,
  updated_at timestamptz not null default now(),
  result_hash varchar(128) not null,
  idempotency_key varchar(160),
  primary key (tenant_id, ocr_extraction_id),
  constraint ocr_extraction_batch_fk foreign key (tenant_id, batch_id) references rate_feed.rate_feed_batch(tenant_id, batch_id)
);

create index ocr_extraction_batch_idx on rate_feed.ocr_extraction (tenant_id, batch_id, created_at desc);
create index ocr_extraction_review_idx on rate_feed.ocr_extraction (tenant_id, status, updated_at desc) where review_required;

create table rate_feed.ocr_extracted_cell (
  tenant_id uuid not null,
  cell_id uuid not null,
  ocr_extraction_id uuid not null,
  page_number integer not null,
  row_index integer not null,
  column_index integer not null,
  raw_text text not null,
  reviewed_text text,
  confidence numeric(5,4) not null,
  bounding_box jsonb not null,
  reviewed_by varchar(128),
  reviewed_at timestamptz,
  status varchar(40) not null,
  created_at timestamptz not null default now(),
  primary key (tenant_id, cell_id),
  constraint ocr_cell_extraction_fk foreign key (tenant_id, ocr_extraction_id) references rate_feed.ocr_extraction(tenant_id, ocr_extraction_id)
);

create index ocr_cell_grid_idx on rate_feed.ocr_extracted_cell (tenant_id, ocr_extraction_id, page_number, row_index, column_index);
create index ocr_cell_review_idx on rate_feed.ocr_extracted_cell (tenant_id, ocr_extraction_id, status, confidence);
