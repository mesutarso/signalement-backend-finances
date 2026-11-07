create table app.draw_ticket (
    id uuid primary key,
    report_id uuid not null references app.report (id),
    report_reference varchar(80) not null,
    person_key varchar(80) not null,
    kind varchar(16) not null,
    period_start date not null,
    unique (report_id, kind),
    check (kind in ('monthly', 'final')),
    check (extract(day from period_start) = 1)
);
