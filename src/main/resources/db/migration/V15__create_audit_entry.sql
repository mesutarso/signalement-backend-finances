create table app.audit_entry (
    id uuid primary key,
    actor_id uuid,
    action varchar(32) not null,
    object_type varchar(32) not null,
    object_id varchar(80),
    result varchar(16) not null,
    recorded_at timestamptz not null,
    check (result in ('allowed', 'refused'))
);

create index audit_entry_recorded_idx on app.audit_entry (recorded_at desc);
create index audit_entry_object_idx on app.audit_entry (object_type, object_id, recorded_at desc);
