alter table app.letter add column service_name varchar(160) not null default '';
drop index app.letter_draft_period_idx;
create unique index letter_draft_period_idx on app.letter (period_start, service_name) where status = 'draft';

create table app.letter_route (
    tax_id varchar(40) primary key,
    service_name varchar(160) not null,
    actor_id uuid not null,
    recorded_at timestamptz not null
);
create index letter_route_service_idx on app.letter_route (service_name);

create table app.letter_dispatch (
    letter_id uuid primary key references app.letter(id),
    dispatch_reference varchar(160) not null,
    sent_on date not null,
    actor_id uuid not null,
    recorded_at timestamptz not null
);
