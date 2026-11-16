-- Additive metadata; existing decisions and evidence are re-evaluated by the application.
alter table app.report add column screening_reason varchar(24);
alter table app.report add constraint report_screening_reason_check
    check (screening_reason in ('unreadable', 'identity-missing', 'duplicate'));
create index report_screening_reason_idx on app.report (screening_reason, reported_at desc)
    where screening_reason is not null;
