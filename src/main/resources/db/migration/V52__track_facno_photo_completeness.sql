alter table app.report add column source_photos_complete boolean;

create index report_assignment_assignee_recent_idx
    on app.report_assignment (assignee_id, recorded_at desc);
