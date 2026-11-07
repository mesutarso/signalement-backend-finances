create table app.supervision_review (
    id uuid primary key,
    report_id uuid not null references app.report (id),
    qualification_id uuid not null references app.qualification (id),
    result varchar(16) not null,
    reason varchar(500) not null,
    actor_id uuid not null,
    recorded_at timestamptz not null,
    check (result in ('upheld', 'returned'))
);

create index supervision_review_report_idx on app.supervision_review (report_id, recorded_at desc);
create index supervision_review_qualification_idx on app.supervision_review (qualification_id, recorded_at desc);
