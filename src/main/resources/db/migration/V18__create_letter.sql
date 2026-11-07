create sequence app.letter_seq;

create table app.letter (
    id uuid primary key,
    reference_code varchar(32) not null unique,
    period_start date not null,
    period_end date not null,
    status varchar(16) not null,
    recipient varchar(160),
    reason varchar(500),
    actor_id uuid not null,
    prepared_at timestamptz not null,
    decided_at timestamptz,
    decided_by uuid,
    check (period_end >= period_start),
    check (status in ('draft', 'validated', 'returned')),
    check (
        (status = 'draft' and decided_at is null and decided_by is null and reason is null and recipient is null)
        or (status = 'validated' and decided_at is not null and decided_by is not null and reason is null)
        or (status = 'returned' and decided_at is not null and decided_by is not null and reason is not null and recipient is null)
    )
);

create unique index letter_draft_period_idx on app.letter (period_start) where status = 'draft';

create table app.letter_line (
    id uuid primary key,
    letter_id uuid not null references app.letter (id),
    annex varchar(16) not null,
    position integer not null,
    report_id uuid not null references app.report (id),
    report_reference varchar(80) not null,
    tax_id varchar(40),
    seller_name varchar(160),
    decision varchar(16),
    finding varchar(32),
    proof varchar(16),
    document_number varchar(80),
    issued_on date,
    amount varchar(40),
    justification varchar(500),
    missing_elements varchar(500),
    dgi_reference varchar(80),
    photo_id uuid references app.report_photo (id),
    content_type varchar(40),
    check (annex in ('company', 'invoice', 'sheet', 'piece')),
    unique (letter_id, position)
);

create index letter_line_letter_idx on app.letter_line (letter_id, position);
