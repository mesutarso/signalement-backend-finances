alter table app.report alter column priority drop not null;
alter table app.report alter column priority drop default;

update app.report
set priority = null
where priority = 'normal'
  and not exists (
      select 1
      from app.report_assignment assignment
      where assignment.report_id = app.report.id
  );

alter table app.report_assignment alter column priority drop not null;
alter table app.report_assignment alter column previous_priority drop not null;
