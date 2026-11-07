alter table app.report_photo add column fingerprint varchar(64);

create index report_photo_fingerprint_idx on app.report_photo (fingerprint);

create table app.duplicate_alert (
    id uuid primary key,
    report_id uuid not null references app.report (id),
    other_report_id uuid not null references app.report (id),
    criterion varchar(32) not null,
    explanation varchar(400) not null,
    strength varchar(16) not null,
    status varchar(16) not null,
    reason varchar(500),
    actor_id uuid,
    created_at timestamptz not null,
    decided_at timestamptz,
    priority_report_id uuid references app.report (id),
    check (report_id <> other_report_id),
    check (criterion in ('photo', 'invoice', 'number', 'code')),
    check (strength in ('likely', 'possible')),
    check (status in ('open', 'confirmed', 'dismissed')),
    check (
        (status = 'open' and reason is null and actor_id is null and decided_at is null and priority_report_id is null)
        or (status = 'dismissed' and reason is not null and actor_id is not null and decided_at is not null and priority_report_id is null)
        or (status = 'confirmed' and reason is not null and actor_id is not null and decided_at is not null and priority_report_id is not null)
    )
);

create unique index duplicate_alert_pair_idx
    on app.duplicate_alert (least(report_id, other_report_id), greatest(report_id, other_report_id), criterion);

create index duplicate_alert_report_idx on app.duplicate_alert (report_id, other_report_id);
