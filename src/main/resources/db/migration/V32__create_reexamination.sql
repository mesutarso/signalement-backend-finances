create sequence app.reexamination_seq;

create table app.reexamination (
    id uuid primary key,
    reference_code varchar(32) not null unique,
    report_id uuid not null references app.report (id),
    report_reference varchar(80) not null,
    request_text varchar(500) not null,
    received_on date not null,
    status varchar(16) not null,
    comment varchar(500),
    recorded_by uuid not null,
    recorded_at timestamptz not null,
    decided_by uuid,
    decided_at timestamptz,
    check (status in ('received', 'maintained', 'modified')),
    check (
        (status = 'received' and decided_by is null and decided_at is null and comment is null)
        or (status in ('maintained', 'modified') and decided_by is not null and decided_at is not null)
    )
);
