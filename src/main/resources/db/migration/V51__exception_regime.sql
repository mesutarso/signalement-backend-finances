alter table app.payment_statement add column exceptional boolean not null default false;
alter table app.payment_statement drop constraint payment_statement_check;
alter table app.payment_statement add constraint payment_statement_workflow_check check (
    (exceptional and status = 'authorized' and controlled_by is null and controlled_at is null
        and visaed_by is null and visaed_at is null and authorized_by = prepared_by
        and authorized_by is not null and authorized_at is not null and due_on is not null
        and reason is not null and btrim(reason) <> '')
    or (not exceptional and (
        (status = 'draft' and controlled_at is null and visaed_at is null and authorized_at is null and due_on is null and reason is null)
        or (status = 'controlled' and controlled_at is not null and controlled_by is not null and visaed_at is null and authorized_at is null and due_on is null and reason is null)
        or (status = 'visaed' and controlled_at is not null and visaed_at is not null and visaed_by is not null and authorized_at is null and due_on is null and reason is null)
        or (status = 'authorized' and controlled_at is not null and visaed_at is not null and authorized_at is not null and authorized_by is not null and due_on is not null and reason is null)
        or (status in ('returned', 'refused') and reason is not null and due_on is null)
    ))
);

alter table app.qualification drop constraint qualification_check;
alter table app.qualification add constraint qualification_check check (
    (decision = 'wait' and finding in ('refusal', 'verification') and proof = 'pending'
        and missing_elements is not null and dgi_reference is null)
    or (decision = 'confirm' and finding = 'refusal' and proof = 'authority'
        and dgi_reference is not null and missing_elements is null)
    or (decision = 'confirm' and finding is not null and (
        (finding = 'exceptional-validation' and proof = 'exception')
        or (finding not in ('refusal', 'exceptional-validation') and proof in ('photo', 'register'))
        ) and missing_elements is null and dgi_reference is null)
    or (decision = 'reject' and finding is null and proof is null
        and missing_elements is null and seller_name is null and seller_tax_id is null
        and issued_on is null and amount is null and document_number is null and read_code is null)
);

create table app.exception_payment_batch (
    id uuid primary key,
    actor_id uuid not null,
    reason varchar(500) not null check (btrim(reason) <> ''),
    environment varchar(16) not null check (environment in ('sandbox', 'production')),
    fingerprint varchar(64) not null,
    created_at timestamptz not null default current_timestamp
);

create table app.exception_payment_item (
    id uuid primary key,
    batch_id uuid not null references app.exception_payment_batch (id),
    line_id uuid not null references app.payment_statement_line (id),
    provider varchar(16) not null check (provider in ('MPESA', 'AIRTEL', 'ORANGE', 'AFRIMONEY')),
    state varchar(16) not null default 'queued' check (state in ('queued', 'submitted', 'blocked')),
    failure_code varchar(64),
    unique (batch_id, line_id)
);

create unique index exception_payment_queued_line_idx on app.exception_payment_item (line_id) where state = 'queued';
create index exception_payment_item_line_idx on app.exception_payment_item (line_id);
create index exception_payment_due_idx on app.exception_payment_item (batch_id, id) where state = 'queued';
