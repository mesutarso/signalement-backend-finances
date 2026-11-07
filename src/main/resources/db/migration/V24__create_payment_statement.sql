create sequence app.payment_statement_seq;

create table app.payment_statement (
    id uuid primary key,
    reference_code varchar(32) not null unique,
    status varchar(16) not null,
    prepared_by uuid not null,
    prepared_at timestamptz not null,
    controlled_by uuid,
    controlled_at timestamptz,
    visaed_by uuid,
    visaed_at timestamptz,
    authorized_by uuid,
    authorized_at timestamptz,
    due_on date,
    reason varchar(500),
    check (status in ('draft', 'controlled', 'visaed', 'authorized', 'returned', 'refused')),
    check (
        (status = 'draft' and controlled_at is null and visaed_at is null and authorized_at is null and due_on is null and reason is null)
        or (status = 'controlled' and controlled_at is not null and controlled_by is not null and visaed_at is null and authorized_at is null and due_on is null and reason is null)
        or (status = 'visaed' and controlled_at is not null and visaed_at is not null and visaed_by is not null and authorized_at is null and due_on is null and reason is null)
        or (status = 'authorized' and controlled_at is not null and visaed_at is not null and authorized_at is not null and authorized_by is not null and due_on is not null and reason is null)
        or (status in ('returned', 'refused') and reason is not null and due_on is null)
    )
);

create unique index payment_statement_open_idx on app.payment_statement ((true)) where status in ('draft', 'controlled', 'visaed');

create table app.payment_statement_line (
    id uuid primary key,
    statement_id uuid not null references app.payment_statement (id),
    prime_id uuid not null references app.prime (id),
    threshold integer not null,
    amount integer not null,
    phone varchar(40) not null,
    released boolean not null,
    unique (statement_id, prime_id)
);

create unique index payment_line_active_prime_idx on app.payment_statement_line (prime_id) where released = false;

create table app.payment_statement_exclusion (
    id uuid primary key,
    statement_id uuid not null references app.payment_statement (id),
    prime_id uuid not null references app.prime (id),
    reason varchar(160) not null,
    unique (statement_id, prime_id)
);
