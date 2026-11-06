create table app.report (
    id uuid primary key,
    external_id varchar(80) not null,
    source varchar(32) not null,
    reference_code varchar(80) not null,
    reported_on date not null,
    title varchar(200) not null,
    description varchar(4000) not null,
    reporter_name varchar(160),
    reporter_phone varchar(40),
    imported_at timestamptz not null,
    unique (source, external_id)
);

create table app.report_photo (
    id uuid primary key,
    report_id uuid not null references app.report (id),
    external_id varchar(80) not null,
    content_type varchar(40) not null,
    content bytea not null,
    unique (report_id, external_id)
);

create index report_reported_on_idx on app.report (reported_on desc, reference_code);

create table app.report_import (
    id uuid primary key,
    started_at timestamptz not null,
    finished_at timestamptz not null,
    status varchar(32) not null,
    created_reports integer not null,
    skipped_reports integer not null,
    created_photos integer not null,
    error_code varchar(64)
);
