alter table app.report add column state varchar(32) not null default 'pending';
alter table app.report add column priority varchar(16) not null default 'normal';
alter table app.report add column assignee_id uuid;
alter table app.report add column version bigint not null default 0;

create index report_queue_idx on app.report (state, assignee_id, reported_at desc);

create table app.report_assignment (
    id uuid primary key,
    report_id uuid not null references app.report (id),
    actor_id uuid not null,
    assignee_id uuid,
    previous_assignee_id uuid,
    priority varchar(16) not null,
    previous_priority varchar(16) not null,
    state varchar(32) not null,
    recorded_at timestamptz not null
);

create index report_assignment_report_idx on app.report_assignment (report_id, recorded_at desc);
