create table app.qualification (
    id uuid primary key,
    report_id uuid not null references app.report (id),
    decision varchar(16) not null,
    finding varchar(32),
    proof varchar(16),
    justification varchar(500) not null,
    seller_name varchar(160),
    seller_tax_id varchar(40),
    issued_on date,
    amount varchar(40),
    document_number varchar(80),
    read_code varchar(80),
    actor_id uuid not null,
    recorded_at timestamptz not null,
    check (decision in ('confirm', 'reject')),
    check (
        (decision = 'confirm'
            and finding is not null
            and proof is not null
            and (
                (finding = 'non-normalized' and proof in ('photo', 'register'))
                or (finding in ('unknown-seller', 'fake-invoice', 'understated', 'missing-transmission') and proof = 'register')
            ))
        or (decision = 'reject'
            and finding is null
            and proof is null
            and seller_name is null
            and seller_tax_id is null
            and issued_on is null
            and amount is null
            and document_number is null
            and read_code is null)
    )
);

create index qualification_report_idx on app.qualification (report_id, recorded_at desc);
