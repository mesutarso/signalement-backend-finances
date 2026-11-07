create table app.declarant_message (
    id uuid primary key,
    report_id uuid not null references app.report (id),
    report_reference varchar(80) not null,
    kind varchar(16) not null,
    body varchar(500) not null,
    status varchar(16) not null,
    prepared_by uuid not null,
    prepared_at timestamptz not null,
    check (kind in ('photo', 'payout', 'reexamination', 'situation')),
    check (status = 'prepared'),
    unique (report_id, kind)
);
