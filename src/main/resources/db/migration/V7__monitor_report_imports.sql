alter table app.report_import alter column finished_at drop not null;
alter table app.report_import add column period_start date;
alter table app.report_import add column period_end date;
alter table app.report_import add column trigger_type varchar(16) not null default 'manual';
create index report_import_started_at_idx on app.report_import (started_at desc);
create unique index report_import_single_active_idx on app.report_import ((1)) where status in ('queued', 'running');
alter table app.report_import add column heartbeat_at timestamptz;
