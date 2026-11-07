create table app.prime (
    id uuid primary key,
    declarant_id varchar(80) not null,
    phone varchar(40) not null,
    rule_version varchar(32) not null,
    threshold integer not null,
    amount integer not null,
    status varchar(16) not null,
    actor_id uuid not null,
    recorded_at timestamptz not null,
    updated_at timestamptz not null,
    check (status in ('proposed', 'withdrawn')),
    check (threshold > 0),
    check (amount > 0),
    unique (declarant_id, rule_version, threshold)
);

create index prime_recorded_idx on app.prime (recorded_at desc);
