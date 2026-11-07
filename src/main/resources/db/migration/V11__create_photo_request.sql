create sequence app.photo_request_seq;

create table app.photo_request (
    id uuid primary key,
    reference_code varchar(32) not null unique,
    report_id uuid not null references app.report (id),
    photo_id uuid not null references app.report_photo (id),
    reason varchar(500) not null,
    basis varchar(16) not null,
    status varchar(16) not null,
    actor_id uuid not null,
    created_at timestamptz not null,
    resolved_at timestamptz,
    linked_report_id uuid references app.report (id),
    notice varchar(16) not null,
    check (basis in ('reading', 'review')),
    check (status in ('open', 'linked')),
    check (notice = 'waiting'),
    check (linked_report_id is null or linked_report_id <> report_id),
    check (
        (status = 'open' and linked_report_id is null and resolved_at is null)
        or (status = 'linked' and linked_report_id is not null and resolved_at is not null)
    )
);

create unique index photo_request_open_photo_idx
    on app.photo_request (photo_id)
    where status = 'open';

create index photo_request_report_idx on app.photo_request (report_id, created_at desc);
