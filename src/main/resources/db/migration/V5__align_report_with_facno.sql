delete from app.report_photo
where report_id in (select id from app.report where source = 'fixture');

delete from app.report
where source = 'fixture';

alter table app.report add column reporter_email varchar(254);
alter table app.report add column reporter_user_id varchar(80);
alter table app.report add column reported_at timestamp;

update app.report
set reported_at = reported_on::timestamp
where reported_at is null;

alter table app.report alter column reported_at set not null;
alter table app.report alter column title drop not null;
alter table app.report alter column description drop not null;

create index report_reported_at_idx on app.report (reported_at desc, reference_code);
