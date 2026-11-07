create table app.prime_suspension (
    id uuid primary key,
    declarant_id varchar(80) not null,
    action varchar(16) not null,
    reason varchar(500) not null,
    actor_id uuid not null,
    recorded_at timestamptz not null,
    check (action in ('suspended', 'restored'))
);

create index prime_suspension_declarant_idx on app.prime_suspension (declarant_id, recorded_at desc);
