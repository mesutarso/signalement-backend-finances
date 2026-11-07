create table app.decision_correction (
    id uuid primary key,
    report_id uuid not null references app.report (id),
    qualification_id uuid not null references app.qualification (id),
    reason varchar(500) not null,
    actor_id uuid not null,
    recorded_at timestamptz not null,
    unique (qualification_id)
);

alter table app.draw_ticket add column excluded_at timestamptz;
